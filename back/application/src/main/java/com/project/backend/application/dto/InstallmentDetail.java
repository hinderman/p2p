package com.project.backend.application.dto;

import com.project.backend.domain.valueobject.InstallmentId;
import com.project.backend.domain.valueobject.Money;

import java.time.LocalDate;
import java.util.Objects;

public record InstallmentDetail(
        InstallmentId installmentId,
        int number,
        LocalDate dueDate,
        Money agreedPrincipal,
        Money agreedInterest,
        Money agreedFee,
        Money paidPrincipal,
        Money paidInterest,
        Money paidFee,
        Money outstandingPrincipal,
        Money outstandingInterest,
        Money outstandingFee) {
    public InstallmentDetail {
        Objects.requireNonNull(installmentId, "The installment id is required");
        Objects.requireNonNull(dueDate, "The installment due date is required");
        Objects.requireNonNull(agreedPrincipal, "The agreed principal is required");
        Objects.requireNonNull(agreedInterest, "The agreed interest is required");
        Objects.requireNonNull(agreedFee, "The agreed fee is required");
        Objects.requireNonNull(paidPrincipal, "The paid principal is required");
        Objects.requireNonNull(paidInterest, "The paid interest is required");
        Objects.requireNonNull(paidFee, "The paid fee is required");
        Objects.requireNonNull(outstandingPrincipal, "The outstanding principal is required");
        Objects.requireNonNull(outstandingInterest, "The outstanding interest is required");
        Objects.requireNonNull(outstandingFee, "The outstanding fee is required");
    }

    public Money agreedTotal() { return agreedPrincipal.add(agreedInterest).add(agreedFee); }
    public Money paidTotal() { return paidPrincipal.add(paidInterest).add(paidFee); }
    public Money outstandingTotal() { return outstandingPrincipal.add(outstandingInterest).add(outstandingFee); }
}
