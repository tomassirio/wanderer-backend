package com.tomassirio.wanderer.command.handler.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitTest {

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void run_withoutTransaction_runsImmediately() {
        AtomicInteger runs = new AtomicInteger();
        AfterCommit.run(runs::incrementAndGet);
        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void run_insideTransaction_waitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger runs = new AtomicInteger();

        AfterCommit.run(runs::incrementAndGet);
        assertThat(runs.get()).isZero();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(runs.get()).isEqualTo(1);
    }
}
