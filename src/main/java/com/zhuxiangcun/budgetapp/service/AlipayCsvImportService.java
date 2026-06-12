package com.zhuxiangcun.budgetapp.service;

import com.zhuxiangcun.budgetapp.dto.ImportResult;
import com.zhuxiangcun.budgetapp.dto.ImportResult.FailedSample;
import com.zhuxiangcun.budgetapp.model.Transaction;
import com.zhuxiangcun.budgetapp.repository.TransactionRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AlipayCsvImportService {

    public static final String INVALID_FORMAT_MESSAGE = "CSV 格式不正确,请确认是支付宝导出的原始账单";

    private static final Charset ALIPAY_CHARSET = Charset.forName("GBK");

    private static final String HEADER_MARKER = "交易时间,交易分类,交易对方";

    private static final int MAX_FAILED_SAMPLES = 5;

    private static final Set<String> EXPENSE_MARKERS = Set.of("支出", "收入", "不计收支");

    private static final Set<String> SUCCESS_STATUSES = Set.of("交易成功", "支付成功");

    private static final DateTimeFormatter SLASH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/M/d H:m:s");

    private static final DateTimeFormatter DASH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, String> CATEGORY_MAPPING = createCategoryMapping();

    private final TransactionRepository transactionRepository;

    public AlipayCsvImportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public ImportResult importFile(Long userId, MultipartFile file) {
        List<CandidateRow> candidates = new ArrayList<>();
        List<FailedSample> failedSamples = new ArrayList<>();
        Counters counters = readRows(userId, file, candidates, failedSamples);

        Set<DuplicateKey> existingKeys = loadExistingKeys(userId, candidates);
        Set<DuplicateKey> pendingKeys = new HashSet<>();
        List<Transaction> transactions = new ArrayList<>();
        for (CandidateRow candidate : candidates) {
            if (existingKeys.contains(candidate.duplicateKey())
                    || !pendingKeys.add(candidate.duplicateKey())) {
                counters.skippedDuplicate++;
                continue;
            }
            transactions.add(candidate.transaction());
        }

        transactionRepository.saveAll(transactions);
        counters.imported = transactions.size();
        return counters.toResult(failedSamples);
    }

    private Counters readRows(
            Long userId,
            MultipartFile file,
            List<CandidateRow> candidates,
            List<FailedSample> failedSamples) {
        Counters counters = new Counters();
        boolean foundHeader = false;
        int rowNumber = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), ALIPAY_CHARSET))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (!foundHeader) {
                    foundHeader = line.contains(HEADER_MARKER);
                    continue;
                }

                if (line.isBlank()) {
                    continue;
                }

                counters.totalRows++;
                parseRow(userId, line, rowNumber, candidates, counters, failedSamples);
            }
        } catch (IOException e) {
            throw new InvalidAlipayCsvException(e);
        }

        if (!foundHeader) {
            throw new InvalidAlipayCsvException();
        }
        return counters;
    }

    private void parseRow(
            Long userId,
            String line,
            int rowNumber,
            List<CandidateRow> candidates,
            Counters counters,
            List<FailedSample> failedSamples) {
        try {
            String[] columns = line.split(",", -1);
            int directionIndex = findDirectionIndex(columns);
            if (directionIndex < 0 || directionIndex + 3 >= columns.length) {
                fail(rowNumber, "数据列不完整", counters, failedSamples);
                return;
            }

            String direction = clean(columns[directionIndex]);
            if (!"支出".equals(direction)) {
                counters.skippedNotExpense++;
                return;
            }

            String status = clean(columns[directionIndex + 3]);
            if (!SUCCESS_STATUSES.contains(status)) {
                counters.skippedFailedStatus++;
                return;
            }

            BigDecimal amount = parseAmount(columns[directionIndex + 1]);
            if (amount.signum() <= 0) {
                counters.skippedNotExpense++;
                return;
            }

            if (columns.length < 5) {
                fail(rowNumber, "数据列不完整", counters, failedSamples);
                return;
            }

            LocalDateTime spentAt = parseSpentAt(columns[0]);
            String note = blankToNull(join(columns, 4, directionIndex));
            String merchant = merchant(columns[2], note);

            Transaction transaction = new Transaction();
            transaction.setUserId(userId);
            transaction.setType("expense");
            transaction.setAmount(amount);
            transaction.setCategory(mapCategory(columns[1]));
            transaction.setMerchant(merchant);
            transaction.setNote(note);
            transaction.setSpentAt(spentAt);
            transaction.setInputMethod("import_alipay");

            candidates.add(new CandidateRow(transaction, DuplicateKey.from(transaction)));
        } catch (InvalidRowException e) {
            fail(rowNumber, e.getMessage(), counters, failedSamples);
        }
    }

    private Set<DuplicateKey> loadExistingKeys(Long userId, List<CandidateRow> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }

        List<String> merchants = candidates.stream()
                .map(candidate -> candidate.transaction().getMerchant())
                .map(merchant -> merchant == null ? "" : merchant)
                .distinct()
                .toList();
        List<BigDecimal> amounts = candidates.stream()
                .map(candidate -> candidate.transaction().getAmount())
                .distinct()
                .toList();
        List<LocalDateTime> spentAts = candidates.stream()
                .map(candidate -> candidate.transaction().getSpentAt())
                .distinct()
                .toList();

        return transactionRepository.findPotentialDuplicates(userId, merchants, amounts, spentAts)
                .stream()
                .map(DuplicateKey::from)
                .collect(HashSet::new, Set::add, Set::addAll);
    }

    private int findDirectionIndex(String[] columns) {
        for (int index = 5; index < columns.length; index++) {
            if (EXPENSE_MARKERS.contains(clean(columns[index]))) {
                return index;
            }
        }
        return -1;
    }

    private BigDecimal parseAmount(String value) {
        String amount = clean(value);
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException e) {
            throw new InvalidRowException("金额无法解析: '" + amount + "'");
        }
    }

    private LocalDateTime parseSpentAt(String value) {
        String spentAt = clean(value);
        for (DateTimeFormatter formatter : List.of(SLASH_TIME_FORMATTER, DASH_TIME_FORMATTER)) {
            try {
                return LocalDateTime.parse(spentAt, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next Alipay export format.
            }
        }
        throw new InvalidRowException("交易时间无法解析: '" + spentAt + "'");
    }

    private String merchant(String merchant, String fallbackNote) {
        String normalizedMerchant = blankToNull(merchant);
        if (normalizedMerchant == null || "/".equals(normalizedMerchant)) {
            return fallbackNote;
        }
        return normalizedMerchant;
    }

    private String mapCategory(String alipayCategory) {
        return CATEGORY_MAPPING.getOrDefault(clean(alipayCategory), "其他");
    }

    private String join(String[] columns, int startInclusive, int endExclusive) {
        StringBuilder value = new StringBuilder();
        for (int index = startInclusive; index < endExclusive; index++) {
            if (index > startInclusive) {
                value.append(',');
            }
            value.append(columns[index]);
        }
        return clean(value.toString());
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private void fail(
            int rowNumber,
            String reason,
            Counters counters,
            List<FailedSample> failedSamples) {
        counters.failed++;
        if (failedSamples.size() < MAX_FAILED_SAMPLES) {
            failedSamples.add(new FailedSample(rowNumber, reason));
        }
    }

    private static Map<String, String> createCategoryMapping() {
        Map<String, String> categories = new HashMap<>();
        // 餐饮
        categories.put("餐饮美食", "餐饮");
        // 饮品
        categories.put("酒水饮品", "饮品");
        categories.put("咖啡茶饮", "饮品");
        // 交通
        categories.put("交通出行", "交通");
        // 购物(支付宝里大多数日常消费类)
        categories.put("日用百货", "购物");
        categories.put("服饰装扮", "购物");
        categories.put("数码电器", "购物");
        categories.put("美妆个护", "购物");
        categories.put("母婴亲子", "购物");
        categories.put("宠物", "购物");
        categories.put("文化休闲", "购物");
        categories.put("鲜花园艺", "购物");
        categories.put("家居家装", "购物");
        categories.put("运动户外", "购物");
        // 医疗
        categories.put("美容美发", "医疗");
        categories.put("医疗健康", "医疗");
        categories.put("医疗保健", "医疗");
        // 教育
        categories.put("教育培训", "教育");
        categories.put("书报杂志", "教育");
        // 娱乐
        categories.put("游戏娱乐", "娱乐");
        categories.put("休闲娱乐", "娱乐");
        // 其他(账单、服务、转账)
        categories.put("住房物业", "其他");
        categories.put("通讯服务", "其他");
        categories.put("商业服务", "其他");
        categories.put("公共服务", "其他");
        categories.put("转账红包", "其他");
        categories.put("其他", "其他");
        return Map.copyOf(categories);
    }

    public static class InvalidAlipayCsvException extends RuntimeException {

        public InvalidAlipayCsvException() {
            super(INVALID_FORMAT_MESSAGE);
        }

        public InvalidAlipayCsvException(Throwable cause) {
            super(INVALID_FORMAT_MESSAGE, cause);
        }
    }

    private static class InvalidRowException extends RuntimeException {

        InvalidRowException(String message) {
            super(message);
        }
    }

    private record CandidateRow(Transaction transaction, DuplicateKey duplicateKey) {
    }

    private record DuplicateKey(String merchant, BigDecimal amount, LocalDateTime spentAt) {

        static DuplicateKey from(Transaction transaction) {
            return new DuplicateKey(
                    transaction.getMerchant(),
                    transaction.getAmount().stripTrailingZeros(),
                    transaction.getSpentAt());
        }

    }

    private static class Counters {

        int totalRows;
        int imported;
        int skippedNotExpense;
        int skippedFailedStatus;
        int skippedDuplicate;
        int failed;

        ImportResult toResult(List<FailedSample> failedSamples) {
            return new ImportResult(
                    totalRows,
                    imported,
                    skippedNotExpense,
                    skippedFailedStatus,
                    skippedDuplicate,
                    failed,
                    List.copyOf(failedSamples));
        }
    }
}
