package com.project.backend.infrastructure.persistence.outbox;

import com.project.backend.application.dto.OutboundEmailMessage;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.infrastructure.notification.OutboundEmailPayloadCipher;
import com.project.backend.domain.event.DomainEvent;
import com.project.backend.domain.event.LoanActivated;
import com.project.backend.domain.event.LoanCreated;
import com.project.backend.domain.event.PaymentApproved;
import com.project.backend.domain.event.PaymentPlanRecalculated;
import com.project.backend.domain.event.PaymentRejected;
import com.project.backend.domain.event.PaymentReversed;
import com.project.backend.domain.event.PaymentSubmittedForReview;
import com.project.backend.domain.event.UserAccountActivated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.project.backend.infrastructure.persistence.JdbcTime.timestamp;

/** Stores domain events atomically with aggregate state by using the transactional outbox. */
@Component
public final class JdbcOutboxEventsAdapter implements OutboxEventsPort {
    private final JdbcTemplate jdbcTemplate;
    private final OutboundEmailPayloadCipher emailPayloadCipher;

    public JdbcOutboxEventsAdapter(JdbcTemplate jdbcTemplate, OutboundEmailPayloadCipher emailPayloadCipher) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailPayloadCipher = emailPayloadCipher;
    }

    @Override
    public void enqueue(List<DomainEvent> domainEvents) {
        for (DomainEvent event : domainEvents) {
            AggregateReference aggregate = aggregateReference(event);
            jdbcTemplate.update("""
                    INSERT INTO loans.outbox_events
                    (outbox_event_id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
                    VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
                    """, UUID.randomUUID(), aggregate.type(), aggregate.id(), event.getClass().getSimpleName(),
                    "{\"event_type\":\"" + event.getClass().getSimpleName() + "\"}", timestamp(event.occurredAt()));
        }
    }

    @Override
    public void enqueueEmail(OutboundEmailMessage message, Instant occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO loans.outbox_events
                (outbox_event_id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
                """, UUID.randomUUID(), OutboundEmailOutboxEvent.aggregateType(message.kind()), message.referenceId(),
                OutboundEmailOutboxEvent.eventType(message.kind()), emailPayloadCipher.encrypt(message),
                timestamp(occurredAt));
    }

    private static AggregateReference aggregateReference(DomainEvent event) {
        if (event instanceof LoanCreated value) return new AggregateReference("Loan", value.loanId().value());
        if (event instanceof LoanActivated value) return new AggregateReference("Loan", value.loanId().value());
        if (event instanceof PaymentPlanRecalculated value) return new AggregateReference("Loan", value.loanId().value());
        if (event instanceof PaymentSubmittedForReview value) return new AggregateReference("ReportedPayment", value.reportedPaymentId().value());
        if (event instanceof PaymentApproved value) return new AggregateReference("ReportedPayment", value.reportedPaymentId().value());
        if (event instanceof PaymentRejected value) return new AggregateReference("ReportedPayment", value.reportedPaymentId().value());
        if (event instanceof PaymentReversed value) return new AggregateReference("ReportedPayment", value.reportedPaymentId().value());
        if (event instanceof UserAccountActivated value) return new AggregateReference("UserAccount", value.userAccountId().value());
        throw new IllegalArgumentException("Unsupported domain event type: " + event.getClass().getName());
    }

    private record AggregateReference(String type, UUID id) {
    }
}
