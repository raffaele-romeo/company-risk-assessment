package com.tunicpay.riskassessment.datasource;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GatheredData {

    private final Map<String, DataSourceResult<?>> results;

    public GatheredData(Map<String, DataSourceResult<?>> results) {
        this.results = new LinkedHashMap<>(results);
    }

    @SuppressWarnings("unchecked")
    public <T> DataSourceResult<T> get(String sourceName) {
        return (DataSourceResult<T>) results.get(sourceName);
    }

    public Map<String, DataSourceResult<?>> all() {
        return results;
    }

    public boolean allFailed() {
        return results.values().stream().noneMatch(DataSourceResult::success);
    }
}
