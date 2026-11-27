package com.assu.server.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionCallbacksTest {

    private final List<String> calls = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void runAfterCommit_whenNoTransaction_thenRunsImmediately() {
        TransactionCallbacks.runAfterCommit(() -> calls.add("commit"));

        assertThat(calls).containsExactly("commit");
    }

    @Test
    void runAfterRollback_whenNoTransaction_thenDoesNothing() {
        TransactionCallbacks.runAfterRollback(() -> calls.add("rollback"));

        assertThat(calls).isEmpty();
    }

    @Test
    void callbacks_whenCommitted_thenOnlyCommitCallbackRuns() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionCallbacks.runAfterCommit(() -> calls.add("commit"));
        TransactionCallbacks.runAfterRollback(() -> calls.add("rollback"));

        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(calls).containsExactly("commit");
    }

    @Test
    void callbacks_whenRolledBack_thenOnlyRollbackCallbackRuns() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionCallbacks.runAfterCommit(() -> calls.add("commit"));
        TransactionCallbacks.runAfterRollback(() -> calls.add("rollback"));

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(calls).containsExactly("rollback");
    }

    @Test
    void runAfterRollback_whenCommitOutcomeUnknown_thenDoesNotRun() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionCallbacks.runAfterRollback(() -> calls.add("rollback"));

        complete(TransactionSynchronization.STATUS_UNKNOWN);

        assertThat(calls).isEmpty();
    }

    private void complete(int status) {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(s -> s.afterCompletion(status));
    }
}
