package com.tunicpay.riskassessment.datasource;

public record DataSourceResult<T>(
        boolean success,
        T data,
        String sourceName,
        long durationMs,
        String failureReason
) {

    public static <T> DataSourceResult<T> success(T data, String sourceName, long durationMs) {
        return new DataSourceResult<>(true, data, sourceName, durationMs, null);
    }

    public static <T> DataSourceResult<T> failure(String sourceName, long durationMs, String reason) {
        return new DataSourceResult<>(false, null, sourceName, durationMs, reason);
    }
}
