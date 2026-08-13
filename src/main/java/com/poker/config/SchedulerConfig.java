package com.poker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class SchedulerConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService tableScheduler() {
        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors());

        ThreadFactory namedFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("poker-scheduler-" + threadNumber.getAndIncrement());
                thread.setDaemon(false);
                return thread;
            }
        };

        return Executors.newScheduledThreadPool(poolSize, namedFactory);
    }
}
