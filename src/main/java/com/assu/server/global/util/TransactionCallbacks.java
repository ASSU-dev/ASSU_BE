package com.assu.server.global.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class TransactionCallbacks {

    private TransactionCallbacks() {
    }

    public static void runAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    // 롤백이 확정된 경우에만 실행한다. 커밋 결과를 알 수 없는 경우(STATUS_UNKNOWN)는 실제로 커밋됐을 수 있어 보상하지 않는다
    public static void runAfterRollback(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    task.run();
                }
            }
        });
    }
}
