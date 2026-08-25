package com.project.backend.infrastructure.notification;

import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.InvitationEmailDeliveryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.project.backend.infrastructure.persistence.JdbcTime.timestamp;

/** Delivers encrypted invitation messages from the transactional outbox at least once. */
@Component
public final class InvitationEmailOutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(InvitationEmailOutboxPublisher.class);
    private static final String EVENT_TYPE = "LoanInvitationEmailRequested";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;
    private final InvitationEmailPayloadCipher payloadCipher;
    private final InvitationEmailDeliveryPort emailDelivery;
    private final InvitationEmailProperties properties;
    private final ClockPort clock;

    public InvitationEmailOutboxPublisher(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactions,
            InvitationEmailPayloadCipher payloadCipher,
            InvitationEmailDeliveryPort emailDelivery,
            InvitationEmailProperties properties,
            ClockPort clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = transactions;
        this.payloadCipher = payloadCipher;
        this.emailDelivery = emailDelivery;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.invitation.email.poll-delay:PT5S}")
    public void publishPendingInvitations() {
        if (!properties.enabled()) {
            return;
        }
        while (Boolean.TRUE.equals(transactions.execute(status -> publishOne()))) {
            // Drain successful messages. A failure is retried on a later scheduled run.
        }
    }

    private boolean publishOne() {
        List<OutboxRecord> records = jdbcTemplate.query("""
                SELECT outbox_event_id, payload::text AS payload
                FROM loans.outbox_events
                WHERE event_type = ? AND published_at IS NULL AND attempts < ?
                ORDER BY occurred_at, outbox_event_id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
                """, (resultSet, rowNumber) -> new OutboxRecord(
                resultSet.getObject("outbox_event_id", UUID.class), resultSet.getString("payload")),
                EVENT_TYPE, properties.maxAttempts());
        if (records.isEmpty()) {
            return false;
        }
        OutboxRecord record = records.getFirst();
        try {
            emailDelivery.deliver(payloadCipher.decrypt(record.payload()));
            jdbcTemplate.update("""
                    UPDATE loans.outbox_events
                    SET published_at = ?, last_error = NULL
                    WHERE outbox_event_id = ?
                    """, timestamp(clock.now()), record.id());
            return true;
        } catch (RuntimeException exception) {
            jdbcTemplate.update("""
                    UPDATE loans.outbox_events
                    SET attempts = attempts + 1, last_error = ?
                    WHERE outbox_event_id = ?
                    """, failureMessage(exception), record.id());
            LOGGER.warn("Invitation email outbox event {} failed; it will be retried until the configured attempt limit", record.id());
            return false;
        }
    }

    private static String failureMessage(RuntimeException exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) {
            value = exception.getClass().getSimpleName();
        }
        return value.length() <= 1_000 ? value : value.substring(0, 1_000);
    }

    private record OutboxRecord(UUID id, String payload) { }
}
