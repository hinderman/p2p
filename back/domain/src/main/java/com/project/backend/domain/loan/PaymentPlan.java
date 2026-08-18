package com.project.backend.domain.loan;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.PaymentPlanId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Plan de installments versionado. Un abono a capital puede producir un nuevo paymentPlan. */
public final class PaymentPlan {
    private final PaymentPlanId id;
    private final LoanTermId loanTermId;
    private final int versionNumber;
    private final PaymentPlanReason reason;
    private final List<Installment> installments;
    private PaymentPlanStatus status;
    private final Instant createdAt;
    private Instant replacedAt;

    public PaymentPlan(
            PaymentPlanId id,
            LoanTermId loanTermId,
            int versionNumber,
            PaymentPlanReason reason,
            List<Installment> installments,
            PaymentPlanStatus status,
            Instant createdAt,
            Instant replacedAt) {
        this.id = Objects.requireNonNull(id, "El paymentPlan de payment es obligatorio");
        this.loanTermId = Objects.requireNonNull(loanTermId, "The payment plan loan terms are required");
        if (versionNumber < 1) {
            throw new DomainRuleViolation("The payment plan version must start at one");
        }
        this.versionNumber = versionNumber;
        this.reason = Objects.requireNonNull(reason, "El reason del paymentPlan es obligatorio");
        this.installments = List.copyOf(Objects.requireNonNull(installments, "Las installments son obligatorias"));
        if (this.installments.isEmpty()) {
            throw new DomainRuleViolation("El paymentPlan debe contener installments");
        }
        validateInstallments(this.installments);
        this.status = Objects.requireNonNull(status, "El status del paymentPlan es obligatorio");
        this.createdAt = Objects.requireNonNull(createdAt, "The payment plan creation time is required");
        this.replacedAt = replacedAt;
        if (replacedAt != null && !replacedAt.isAfter(createdAt)) {
            throw new DomainRuleViolation("The replacement time must be after the creation time");
        }
    }

    public PaymentPlanId id() { return id; }
    public LoanTermId loanTermId() { return loanTermId; }
    public int versionNumber() { return versionNumber; }
    public PaymentPlanReason reason() { return reason; }
    public List<Installment> installments() { return installments; }
    public PaymentPlanStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant replacedAt() { return replacedAt; }

    public void supersede(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "La fecha de reemplazo es obligatoria");
        if (status != PaymentPlanStatus.CURRENT) {
            throw new DomainRuleViolation("Solo un paymentPlan vigente puede reemplazarse");
        }
        if (occurredAt.isBefore(createdAt)) {
            throw new DomainRuleViolation("A payment plan cannot be superseded before it is created");
        }
        status = PaymentPlanStatus.SUPERSEDED;
        replacedAt = occurredAt;
    }

    private static void validateInstallments(List<Installment> installments) {
        String currency = installments.getFirst().agreedPrincipal().currency();
        for (int index = 0; index < installments.size(); index++) {
            Installment installment = installments.get(index);
            if (installment.number() != index + 1) {
                throw new DomainRuleViolation("Las installments deben estar numeradas consecutivamente desde uno");
            }
            if (!currency.equals(installment.agreedPrincipal().currency())) {
                throw new DomainRuleViolation("Todas las installments del paymentPlan deben usar la misma currency");
            }
        }
        List<LocalDate> dueDates = installments.stream().map(Installment::dueDate).toList();
        if (!dueDates.equals(dueDates.stream().sorted(Comparator.naturalOrder()).toList())) {
            throw new DomainRuleViolation("Las dueDates de vencimiento deben estar ordenadas");
        }
    }
}
