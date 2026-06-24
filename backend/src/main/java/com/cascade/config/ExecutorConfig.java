package com.cascade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    /**
     * Virtual-thread executor for running workflows concurrently.
     * Each workflow run gets its own virtual thread — cheap and non-blocking.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService workflowExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
