package com.tfg.gonzalo.backend.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.Retryable;

public class DatabaseRetryLoggingListener implements RetryListener {

    private static final Logger log = LoggerFactory.getLogger(DatabaseRetryLoggingListener.class);

    @Override
    public void beforeRetry(RetryPolicy retryPolicy, Retryable retryable, RetryState retryState) {
        if (retryState.getRetryCount() == 0) {
            return;
        }
        log.warn("Retrying database operation after transient failure on attempt {}: {}",
                retryState.getRetryCount(), retryState.getLastException().getMessage());
    }
}
