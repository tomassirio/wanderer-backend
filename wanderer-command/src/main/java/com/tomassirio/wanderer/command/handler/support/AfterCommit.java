package com.tomassirio.wanderer.command.handler.support;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs work once the current transaction has committed, or right away when there is none.
 *
 * <p>Event handlers persist inside the caller's transaction and then trigger {@code @Async}
 * achievement checks. Started inline, those checks run on another thread before the commit and read
 * the old data (e.g. zero friends), so nothing unlocks. Deferring them fixes that.
 */
public final class AfterCommit {

    private AfterCommit() {}

    public static void run(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        task.run();
                    }
                });
    }
}
