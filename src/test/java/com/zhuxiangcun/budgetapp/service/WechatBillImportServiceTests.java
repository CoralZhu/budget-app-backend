package com.zhuxiangcun.budgetapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhuxiangcun.budgetapp.dto.ImportResult;
import com.zhuxiangcun.budgetapp.model.Transaction;
import com.zhuxiangcun.budgetapp.repository.TransactionRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class WechatBillImportServiceTests {

    private static final String[] HEADER = {
        "交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)",
        "支付方式", "当前状态", "交易单号", "商户单号", "备注"
    };

    private final TransactionRepository transactionRepository =
            org.mockito.Mockito.mock(TransactionRepository.class);

    private final WechatBillImportService service =
            new WechatBillImportService(transactionRepository, new WeChatCategoryMapper());

    @Test
    void importsXlsxRowsAfterHiddenHeaderAndKeepsProcessingBadRows() throws Exception {
        when(transactionRepository.findPotentialDuplicates(eq(7L), anyList(), anyList(), anyList()))
                .thenReturn(List.of());

        MockMultipartFile file = xlsx(
                row("2026-05-18T08:00:00", "商户消费", "HUNGRYPANDA", "PD11997882076744", "支出",
                        "169.01", "支付成功"),
                row("2026-05-18T09:00:00", "商户消费", "Luckin", "瑞幸咖啡", "支出",
                        "18.50", "支付成功"),
                row("2026-05-18T10:00:00", "商户消费", "滴滴出行", "滴滴打车", "支出",
                        "26", "支付成功"),
                row("2026-05-18T11:00:00", "商户消费", "零售店", "文具", "支出",
                        "12", "支付成功"),
                row("2026-05-18T12:00:00", "转账", "'=)", "微信转账", "支出",
                        "88", "对方已收钱"),
                row("2026-05-18T13:00:00", "商户消费", "工资", "入账", "收入",
                        "99", "支付成功"),
                row("2026-05-18T14:00:00", "商户消费", "零钱通", "转出", "中性交易",
                        "5", "支付成功"),
                row("2026-05-18T15:00:00", "商户消费", "饭店", "晚饭", "支出",
                        "35", "已退款"),
                row("2026-05-18T16:00:00", "商户消费", "坏行", "无效金额", "支出",
                        "bad", "支付成功"));

        ImportResult result = service.importFile(7L, file);

        assertThat(result.totalRows()).isEqualTo(9);
        assertThat(result.imported()).isEqualTo(5);
        assertThat(result.skippedNotExpense()).isEqualTo(2);
        assertThat(result.skippedFailedStatus()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failedSamples()).singleElement()
                .satisfies(sample -> {
                    assertThat(sample.row()).isEqualTo(13);
                    assertThat(sample.reason()).isEqualTo("金额无法解析: 'bad'");
                });

        ArgumentCaptor<List<Transaction>> transactions = transactionCaptor();
        verify(transactionRepository).saveAll(transactions.capture());
        assertThat(transactions.getValue()).extracting(Transaction::getCategory)
                .containsExactly("餐饮", "饮品", "交通", "购物", "其他");
        assertThat(transactions.getValue().get(4).getMerchant()).isEqualTo("'=)");
        assertThat(transactions.getValue())
                .allSatisfy(transaction -> {
                    assertThat(transaction.getUserId()).isEqualTo(7L);
                    assertThat(transaction.getType()).isEqualTo("expense");
                    assertThat(transaction.getInputMethod()).isEqualTo("import_wechat");
                });
    }

    @Test
    void skipsExistingAndSameBillDuplicatesBeforeSaving() throws Exception {
        Transaction existing = new Transaction();
        existing.setUserId(9L);
        existing.setMerchant("早餐铺");
        existing.setAmount(new BigDecimal("16.00"));
        existing.setSpentAt(LocalDateTime.of(2026, 5, 18, 7, 30));
        when(transactionRepository.findPotentialDuplicates(eq(9L), anyList(), anyList(), anyList()))
                .thenReturn(List.of(existing));

        MockMultipartFile file = xlsx(
                row("2026-05-18T07:30:00", "商户消费", "早餐铺", "早餐", "支出",
                        "16", "支付成功"),
                row("2026-05-18T18:30:00", "商户消费", "火锅店", "火锅", "支出",
                        "128", "支付成功"),
                row("2026-05-18T18:30:00", "商户消费", "火锅店", "火锅", "支出",
                        "128.00", "支付成功"));

        ImportResult result = service.importFile(9L, file);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skippedDuplicate()).isEqualTo(2);

        ArgumentCaptor<List<Transaction>> transactions = transactionCaptor();
        verify(transactionRepository).saveAll(transactions.capture());
        assertThat(transactions.getValue()).singleElement()
                .satisfies(transaction -> assertThat(transaction.getMerchant()).isEqualTo("火锅店"));
    }

    @Test
    void importsUtf8TabSeparatedCsvRows() {
        when(transactionRepository.findPotentialDuplicates(eq(11L), anyList(), anyList(), anyList()))
                .thenReturn(List.of());

        String csv = """
                微信支付账单明细
                交易时间	交易类型	交易对方	商品	收/支	金额(元)	支付方式	当前状态	交易单号	商户单号	备注
                2026-05-18 12:00:00	商户消费	便利店	午餐	支出	22.50	零钱	支付成功	1	2	
                """;

        ImportResult result = service.importFile(
                11L,
                new MockMultipartFile(
                        "file", "wechat.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.imported()).isEqualTo(1);
        ArgumentCaptor<List<Transaction>> transactions = transactionCaptor();
        verify(transactionRepository).saveAll(transactions.capture());
        assertThat(transactions.getValue()).singleElement()
                .satisfies(transaction -> assertThat(transaction.getCategory()).isEqualTo("餐饮"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<List<Transaction>> transactionCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    private MockMultipartFile xlsx(BillRow... rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("微信支付账单");
            sheet.createRow(0).createCell(0).setCellValue("微信支付账单明细");
            sheet.createRow(1).createCell(0).setCellValue("导出说明");
            sheet.createRow(2).createCell(0).setCellValue("这里只是说明");
            Row header = sheet.createRow(3);
            for (int index = 0; index < HEADER.length; index++) {
                header.createCell(index).setCellValue(HEADER[index]);
            }
            for (int index = 0; index < rows.length; index++) {
                writeRow(sheet.createRow(index + 4), rows[index]);
            }

            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "wechat.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }

    private void writeRow(Row row, BillRow billRow) {
        row.createCell(0).setCellValue(LocalDateTime.parse(billRow.spentAt()));
        row.createCell(1).setCellValue(billRow.transactionType());
        row.createCell(2).setCellValue(billRow.merchant());
        row.createCell(3).setCellValue(billRow.product());
        row.createCell(4).setCellValue(billRow.direction());
        row.createCell(5).setCellValue(billRow.amount());
        row.createCell(6).setCellValue("零钱");
        row.createCell(7).setCellValue(billRow.status());
        row.createCell(8).setCellValue("order");
        row.createCell(9).setCellValue("merchant-order");
        row.createCell(10).setCellValue("");
    }

    private BillRow row(
            String spentAt,
            String transactionType,
            String merchant,
            String product,
            String direction,
            String amount,
            String status) {
        return new BillRow(spentAt, transactionType, merchant, product, direction, amount, status);
    }

    private record BillRow(
            String spentAt,
            String transactionType,
            String merchant,
            String product,
            String direction,
            String amount,
            String status) {
    }
}
