package com.schoolmanagement.audit;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Writes audit events to {@code audit_logs}. An event published inside a business transaction is saved just before
 * that transaction commits, so the trail and the change succeed or fail together (a rolled-back change leaves no
 * "it happened" row, and a failure to write the trail stops the change). An event published outside any
 * transaction, such as a failed sign-in, is saved on its own and can never turn the response into an error.
 */
@Component
class PersistingAuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(PersistingAuditEventListener.class);

    private final AuditLogRepository logs;
    private final Clock clock;

    PersistingAuditEventListener(AuditLogRepository logs, Clock clock) {
        this.logs = logs;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    void on(AuditEvent event) {
        boolean inTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        try {
            logs.save(AuditLog.from(event, clock.instant()));
        } catch (RuntimeException e) {
            if (inTransaction) {
                throw e;
            }
            log.error("Could not persist audit event {}", AuditSanitizer.text(event.action()), e);
        }
    }
}
