package com.cascade.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Submits workflow runs to the virtual-thread executor.
 * Each run gets its own virtual thread — lightweight enough to have thousands
 * in flight simultaneously.
 */
@Component
public class Scheduler {

    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);

    private final ExecutorService executor;
    private final ConcurrentHashMap<String, Future<?>> activeFutures = new ConcurrentHashMap<>();

    public Scheduler(@Qualifier("workflowExecutor") ExecutorService executor) {
        this.executor = executor;
    }

    public void submit(String runId, Runnable task) {
        Future<?> future = executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Uncaught exception in run {}: {}", runId, e.getMessage(), e);
            } finally {
                activeFutures.remove(runId);
            }
        });
        activeFutures.put(runId, future);
        log.debug("Submitted run {} to executor", runId);
    }

    public boolean isRunning(String runId) {
        Future<?> f = activeFutures.get(runId);
        return f != null && !f.isDone();
    }
}
