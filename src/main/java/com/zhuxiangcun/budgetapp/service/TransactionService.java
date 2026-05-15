package com.zhuxiangcun.budgetapp.service;

import com.zhuxiangcun.budgetapp.dto.TransactionRequest;
import com.zhuxiangcun.budgetapp.dto.TransactionResponse;
import com.zhuxiangcun.budgetapp.model.Transaction;
import com.zhuxiangcun.budgetapp.repository.TransactionRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public TransactionResponse create(Long userId, TransactionRequest request) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        applyRequest(transaction, request);

        return toResponse(transactionRepository.save(transaction));
    }

    public List<TransactionResponse> list(Long userId) {
        return transactionRepository.findByUserIdOrderBySpentAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<TransactionResponse> getByMonth(Long userId, YearMonth yearMonth) {
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        return transactionRepository.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(userId, start, end)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public TransactionResponse update(Long userId, Long id, TransactionRequest request) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("交易记录不存在"));

        applyRequest(transaction, request);
        return toResponse(transactionRepository.save(transaction));
    }

    public void delete(Long userId, Long id) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("交易记录不存在"));

        transactionRepository.delete(transaction);
    }

    private void applyRequest(Transaction transaction, TransactionRequest request) {
        transaction.setType(request.getType());
        transaction.setAmount(request.getAmount());
        transaction.setCategory(request.getCategory());
        transaction.setMerchant(request.getMerchant());
        transaction.setNote(request.getNote());
        transaction.setSpentAt(request.getSpentAt());
        transaction.setInputMethod(normalizeInputMethod(request.getInputMethod()));
    }

    private String normalizeInputMethod(String inputMethod) {
        return inputMethod == null || inputMethod.isBlank() ? "manual" : inputMethod;
    }

    private TransactionResponse toResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setType(transaction.getType());
        response.setAmount(transaction.getAmount());
        response.setCategory(transaction.getCategory());
        response.setMerchant(transaction.getMerchant());
        response.setNote(transaction.getNote());
        response.setSpentAt(transaction.getSpentAt());
        response.setInputMethod(transaction.getInputMethod());
        response.setCreatedAt(transaction.getCreatedAt());
        return response;
    }
}
