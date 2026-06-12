package com.zhuxiangcun.budgetapp.service;

import com.zhuxiangcun.budgetapp.dto.ImportResult;
import com.zhuxiangcun.budgetapp.dto.ImportResult.FailedSample;
import com.zhuxiangcun.budgetapp.model.Transaction;
import com.zhuxiangcun.budgetapp.repository.TransactionRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WechatBillImportService {

    public static final String INVALID_FILE_MESSAGE = "请上传 .xlsx 或 .csv 格式的微信账单";

    public static final String INVALID_FORMAT_MESSAGE = "微信账单格式不正确,请确认是微信导出的原始账单";

    private static final int MAX_FAILED_SAMPLES = 5;

    private static final int TIME_INDEX = 0;
    private static final int TYPE_INDEX = 1;
    private static final int MERCHANT_INDEX = 2;
    private static final int PRODUCT_INDEX = 3;
    private static final int DIRECTION_INDEX = 4;
    private static final int AMOUNT_INDEX = 5;
    private static final int STATUS_INDEX = 7;
    private static final int MIN_COLUMNS = STATUS_INDEX + 1;

    private static final Set<String> SUCCESS_STATUSES =
            Set.of("支付成功", "对方已收钱", "已存入零钱", "朋友已领取", "已转账");

    private static final DateTimeFormatter CSV_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TransactionRepository transactionRepository;

    private final WeChatCategoryMapper categoryMapper;

    public WechatBillImportService(
            TransactionRepository transactionRepository,
            WeChatCategoryMapper categoryMapper) {
        this.transactionRepository = transactionRepository;
        this.categoryMapper = categoryMapper;
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
        String name = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx")) {
            return readXlsxRows(userId, file, candidates, failedSamples);
        }
        if (name.endsWith(".csv")) {
            return readCsvRows(userId, file, candidates, failedSamples);
        }
        throw new InvalidWechatBillException(INVALID_FILE_MESSAGE);
    }

    private Counters readXlsxRows(
            Long userId,
            MultipartFile file,
            List<CandidateRow> candidates,
            List<FailedSample> failedSamples) {
        Counters counters = new Counters();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int headerIndex = findHeaderIndex(sheet, formatter);
            if (headerIndex < 0) {
                throw new InvalidWechatBillException(INVALID_FORMAT_MESSAGE);
            }

            for (int index = headerIndex + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || isBlank(row, formatter)) {
                    continue;
                }

                counters.totalRows++;
                parseXlsxRow(userId, row, formatter, candidates, counters, failedSamples);
            }
            return counters;
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidWechatBillException invalidWechatBillException) {
                throw invalidWechatBillException;
            }
            throw new InvalidWechatBillException(INVALID_FORMAT_MESSAGE, e);
        }
    }

    private Counters readCsvRows(
            Long userId,
            MultipartFile file,
            List<CandidateRow> candidates,
            List<FailedSample> failedSamples) {
        Counters counters = new Counters();
        boolean foundHeader = false;
        int rowNumber = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                String[] columns = line.split("\t", -1);
                if (!foundHeader) {
                    foundHeader = isHeader(columns);
                    continue;
                }
                if (line.isBlank()) {
                    continue;
                }

                counters.totalRows++;
                parseColumns(userId, columns, rowNumber, null, candidates, counters, failedSamples);
            }
        } catch (IOException e) {
            throw new InvalidWechatBillException(INVALID_FORMAT_MESSAGE, e);
        }

        if (!foundHeader) {
            throw new InvalidWechatBillException(INVALID_FORMAT_MESSAGE);
        }
        return counters;
    }

    private int findHeaderIndex(Sheet sheet, DataFormatter formatter) {
        for (int index = 0; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row != null
                    && "交易时间".equals(cellText(row, TIME_INDEX, formatter))
                    && "交易类型".equals(cellText(row, TYPE_INDEX, formatter))) {
                return index;
            }
        }
        return -1;
    }

    private void parseXlsxRow(
            Long userId,
            Row row,
            DataFormatter formatter,
            List<CandidateRow> candidates,
            Counters counters,
            List<FailedSample> failedSamples) {
        String[] columns = new String[Math.max(MIN_COLUMNS, row.getLastCellNum())];
        for (int index = 0; index < columns.length; index++) {
            columns[index] = cellText(row, index, formatter);
        }
        parseColumns(
                userId,
                columns,
                row.getRowNum() + 1,
                row.getCell(TIME_INDEX),
                candidates,
                counters,
                failedSamples);
    }

    private void parseColumns(
            Long userId,
            String[] columns,
            int rowNumber,
            Cell timeCell,
            List<CandidateRow> candidates,
            Counters counters,
            List<FailedSample> failedSamples) {
        try {
            if (columns.length < MIN_COLUMNS) {
                fail(rowNumber, "数据列不完整", counters, failedSamples);
                return;
            }

            String direction = clean(columns[DIRECTION_INDEX]);
            if (!"支出".equals(direction)) {
                counters.skippedNotExpense++;
                return;
            }

            String transactionType = clean(columns[TYPE_INDEX]);
            if (categoryMapper.shouldSkipTransactionType(transactionType)) {
                counters.skippedFailedStatus++;
                return;
            }

            String status = clean(columns[STATUS_INDEX]);
            if (!SUCCESS_STATUSES.contains(status)) {
                counters.skippedFailedStatus++;
                return;
            }

            BigDecimal amount = parseAmount(columns[AMOUNT_INDEX]);
            if (amount.signum() <= 0) {
                counters.skippedNotExpense++;
                return;
            }

            String product = blankToNull(columns[PRODUCT_INDEX]);
            String merchant = merchant(columns[MERCHANT_INDEX], product);
            Transaction transaction = new Transaction();
            transaction.setUserId(userId);
            transaction.setType("expense");
            transaction.setAmount(amount);
            transaction.setCategory(categoryMapper.classify(
                    transactionType, columns[MERCHANT_INDEX], product));
            transaction.setMerchant(merchant);
            transaction.setNote(product);
            transaction.setSpentAt(parseSpentAt(columns[TIME_INDEX], timeCell));
            transaction.setInputMethod("import_wechat");
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

    private BigDecimal parseAmount(String value) {
        String amount = clean(value).replace("¥", "").replace(",", "");
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException e) {
            throw new InvalidRowException("金额无法解析: '" + amount + "'");
        }
    }

    private LocalDateTime parseSpentAt(String value, Cell timeCell) {
        if (timeCell != null) {
            try {
                return timeCell.getDateCellValue()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            } catch (RuntimeException ignored) {
                // Some exports leave the time as text, which uses the CSV formatter below.
            }
        }

        String spentAt = clean(value);
        try {
            return LocalDateTime.parse(spentAt, CSV_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new InvalidRowException("交易时间无法解析: '" + spentAt + "'");
        }
    }

    private String merchant(String merchant, String fallbackProduct) {
        String cleaned = blankToNull(merchant);
        if (cleaned == null || "/".equals(cleaned)) {
            return fallbackProduct == null ? cleaned : fallbackProduct;
        }
        return cleaned;
    }

    private boolean isHeader(String[] columns) {
        return columns.length >= 2
                && "交易时间".equals(clean(columns[TIME_INDEX]))
                && "交易类型".equals(clean(columns[TYPE_INDEX]));
    }

    private boolean isBlank(Row row, DataFormatter formatter) {
        for (int index = 0; index < row.getLastCellNum(); index++) {
            if (!cellText(row, index, formatter).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String cellText(Row row, int index, DataFormatter formatter) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        return clean(formatter.formatCellValue(cell));
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

    public static class InvalidWechatBillException extends RuntimeException {

        public InvalidWechatBillException(String message) {
            super(message);
        }

        public InvalidWechatBillException(String message, Throwable cause) {
            super(message, cause);
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
