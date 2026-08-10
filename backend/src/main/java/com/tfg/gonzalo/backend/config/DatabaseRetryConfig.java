package com.tfg.gonzalo.backend.config;

import com.tfg.gonzalo.backend.support.DatabaseExceptionSupport;
import com.tfg.gonzalo.backend.support.DatabaseRetryLoggingListener;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class DatabaseRetryConfig {

    @Bean
    public RetryTemplate databaseRetryTemplate(DatabaseRetryLoggingListener retryListener) {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(1)
                .delay(Duration.ofMillis(250))
                .predicate(DatabaseExceptionSupport::isTransientDatabaseFailure)
                .build();

        RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
        retryTemplate.setRetryListener(retryListener);
        return retryTemplate;
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public DatabaseRetryLoggingListener databaseRetryLoggingListener() {
        return new DatabaseRetryLoggingListener();
    }
}
