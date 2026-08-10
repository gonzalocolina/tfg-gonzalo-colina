package com.tfg.gonzalo.backend.support;

import java.util.function.Supplier;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseRetryExecutor {

    private final RetryTemplate retryTemplate;

    public DatabaseRetryExecutor(RetryTemplate databaseRetryTemplate) {
        this.retryTemplate = databaseRetryTemplate;
    }

    public <T> T execute(Supplier<T> action) {
        return retryTemplate.invoke(action::get);
    }

    public void execute(Runnable action) {
        retryTemplate.invoke(() -> {
            action.run();
            return null;
        });
    }
}
