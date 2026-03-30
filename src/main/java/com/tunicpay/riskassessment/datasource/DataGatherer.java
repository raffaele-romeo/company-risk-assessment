package com.tunicpay.riskassessment.datasource;

import com.tunicpay.riskassessment.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class DataGatherer {

    private static final Logger log = LoggerFactory.getLogger(DataGatherer.class);

    private final List<DataSource<?>> dataSources;
    private final ExecutorService executor;
    private final int timeoutSeconds;

    public DataGatherer(
            List<DataSource<?>> dataSources,
            ExecutorService dataSourceExecutor,
            AppConfig appConfig) {
        this.dataSources = dataSources;
        this.executor = dataSourceExecutor;
        this.timeoutSeconds = appConfig.getAssessment().getSourceTimeoutSeconds();
    }

    public GatheredData gather(String companyNumber, String companyName, String jurisdiction) {
        log.info("Gathering data for company {} ({})", companyNumber, companyName);
        long start = System.currentTimeMillis();

        Map<String, CompletableFuture<DataSourceResult<?>>> futures = new LinkedHashMap<>();
        for (DataSource<?> source : dataSources) {
            futures.put(source.name(), fetchAsync(source, companyNumber, companyName, jurisdiction, start));
        }

        CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();

        Map<String, DataSourceResult<?>> results = new LinkedHashMap<>();
        futures.forEach((name, future) -> results.put(name, future.join()));

        var gathered = new GatheredData(results);

        long duration = System.currentTimeMillis() - start;
        log.info("Data gathering completed in {}ms. {}", duration,
                results.entrySet().stream()
                        .map(e -> e.getKey() + ": " + (e.getValue().success() ? "success" : "failed"))
                        .reduce((a, b) -> a + ", " + b).orElse("no sources"));

        return gathered;
    }

    private <T> CompletableFuture<DataSourceResult<?>> fetchAsync(
            DataSource<T> source, String companyNumber, String companyName, String jurisdiction, long start) {
        return CompletableFuture
                .<DataSourceResult<?>>supplyAsync(() -> source.fetch(companyNumber, companyName, jurisdiction), executor)
                .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.error("{} source timed out or failed: {}", source.name(), ex.getMessage());
                    return DataSourceResult.failure(source.name(),
                            System.currentTimeMillis() - start, ex.getMessage());
                });
    }
}
