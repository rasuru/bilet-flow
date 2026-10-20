package com.biletflow.biletflow.paymentsmonetization.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PaymentPollingConfiguration {

    @Bean("paymentPollingExecutor")
    public ThreadPoolTaskExecutor paymentPollingExecutor(@Value("${biletflow.payments.poll-workers:4}") int workers) {
        if (workers < 1 || workers > 32) throw new IllegalArgumentException("Payment poll workers must be between 1 and 32");
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("payment-reconcile-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }
}
