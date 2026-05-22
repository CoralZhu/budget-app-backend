package com.zhuxiangcun.budgetapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhuxiangcun.budgetapp.dto.ImportResult;
import com.zhuxiangcun.budgetapp.model.Transaction;
import com.zhuxiangcun.budgetapp.repository.TransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class AlipayCsvImportServiceTests {

    private static final String HEADER =
            "交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注";

    private final TransactionRepository transactionRepository =
            org.mockito.Mockito.mock(TransactionRepository.class);

    private final AlipayCsvImportService service = new AlipayCsvImportService(transactionRepository);

    @Test
    void importsGbkRowsAfterHeaderAndKeepsProcessingBadRows() {
        when(transactionRepository.findPotentialDuplicates(eq(7L), anyList(), anyList(), anyList()))
                .thenReturn(List.of());

        MockMultipartFile file = csv("""
                支付宝账单说明
                导出时间:2026-05-22
                %s
                2026/5/18 22:24:48,餐饮美食,网鱼网咖,,网鱼水吧,加冰,支出,10,账户余额,交易成功,1,2,
                2026-05-18 23:24:48,日用百货,/,phone,屈臣氏(华润),支出,39.88,花呗,支付成功,3,4,
                2026/5/18 23:30:48,商业服务,服务商,,会员,收入,12,账户余额,交易成功,5,6,
                2026/5/18 23:31:48,文化休闲,影院,,电影票,支出,36,银行卡,退款成功,7,8,
                bad-time,宠物,猫店,,猫粮,支出,abc,账户余额,交易成功,9,10,
                """.formatted(HEADER));

        ImportResult result = service.importFile(7L, file);

        assertThat(result.totalRows()).isEqualTo(5);
        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skippedNotExpense()).isEqualTo(1);
        assertThat(result.skippedFailedStatus()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failedSamples()).hasSize(1);
        assertThat(result.failedSamples().get(0).row()).isEqualTo(8);
        assertThat(result.failedSamples().get(0).reason()).isEqualTo("金额无法解析: 'abc'");

        ArgumentCaptor<List<Transaction>> transactions = transactionCaptor();
        verify(transactionRepository).saveAll(transactions.capture());
        assertThat(transactions.getValue()).hasSize(2);
        assertThat(transactions.getValue().get(0).getNote()).isEqualTo("网鱼水吧,加冰");
        assertThat(transactions.getValue().get(0).getCategory()).isEqualTo("餐饮");
        assertThat(transactions.getValue().get(1).getMerchant()).isEqualTo("屈臣氏(华润)");
        assertThat(transactions.getValue().get(1).getCategory()).isEqualTo("购物");
        assertThat(transactions.getValue())
                .allSatisfy(transaction -> {
                    assertThat(transaction.getUserId()).isEqualTo(7L);
                    assertThat(transaction.getType()).isEqualTo("expense");
                    assertThat(transaction.getInputMethod()).isEqualTo("import_alipay");
                });
    }

    @Test
    void skipsExistingAndSameFileDuplicatesBeforeBatchSave() {
        Transaction existing = new Transaction();
        existing.setUserId(9L);
        existing.setMerchant("美团");
        existing.setAmount(new BigDecimal("25.00"));
        existing.setSpentAt(java.time.LocalDateTime.of(2026, 5, 18, 12, 0));
        when(transactionRepository.findPotentialDuplicates(eq(9L), anyList(), anyList(), anyList()))
                .thenReturn(List.of(existing));

        MockMultipartFile file = csv("""
                %s
                2026-05-18 12:00:00,餐饮美食,美团,,外卖,支出,25,账户余额,交易成功,1,2,
                2026-05-18 13:00:00,文化休闲,网咖,,上机,支出,18,账户余额,交易成功,3,4,
                2026-05-18 13:00:00,文化休闲,网咖,,上机,支出,18.00,账户余额,交易成功,5,6,
                """.formatted(HEADER));

        ImportResult result = service.importFile(9L, file);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skippedDuplicate()).isEqualTo(2);

        ArgumentCaptor<List<Transaction>> transactions = transactionCaptor();
        verify(transactionRepository).saveAll(transactions.capture());
        assertThat(transactions.getValue()).singleElement()
                .satisfies(transaction -> assertThat(transaction.getCategory()).isEqualTo("购物"));
    }

    @Test
    void rejectsCsvWithoutAlipayHeader() {
        MockMultipartFile file = csv("time,amount\n2026-05-18,10\n");

        assertThatThrownBy(() -> service.importFile(3L, file))
                .isInstanceOf(AlipayCsvImportService.InvalidAlipayCsvException.class)
                .hasMessage(AlipayCsvImportService.INVALID_FORMAT_MESSAGE);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<List<Transaction>> transactionCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile(
                "file",
                "alipay.csv",
                "text/csv",
                content.getBytes(Charset.forName("GBK")));
    }
}
