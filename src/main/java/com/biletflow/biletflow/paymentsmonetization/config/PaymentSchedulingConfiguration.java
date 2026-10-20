package com.biletflow.biletflow.paymentsmonetization.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PaymentSchedulingConfiguration {

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler paymentTaskScheduler() {
        // Bank timeouts must never prevent outbox delivery or inventory-hold expiry.
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("payment-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
