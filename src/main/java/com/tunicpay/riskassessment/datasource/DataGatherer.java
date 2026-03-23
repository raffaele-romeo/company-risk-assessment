package com.tunicpay.riskassessment.datasource;

import com.tunicpay.riskassessment.config.AppConfig;
import com.tunicpay.riskassessment.model.AdverseMediaFinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class DataGatherer {

    private static final Logger log = LoggerFactory.getLogger(DataGatherer.class);

    private final DataSource<CompaniesHouseData> companiesHouseSource;
    private final DataSource<List<AdverseMediaFinding>> adverseMediaSource;
    private final ExecutorService executor;
    private final int timeoutSeconds;

    public DataGatherer(
            DataSource<CompaniesHouseData> companiesHouseSource,
            DataSource<List<AdverseMediaFinding>> adverseMediaSource,
            ExecutorService dataSourceExecutor,
            AppConfig appConfig) {
        this.companiesHouseSource = companiesHouseSource;
        this.adverseMediaSource = adverseMediaSource;
        this.executor = dataSourceExecutor;
        this.timeoutSeconds = appConfig.getAssessment().getSourceTimeoutSeconds();
    }

    public GatheredData gather(String companyNumber, String companyName, String jurisdiction) {
        log.info("Gathering data for company {} ({})", companyNumber, companyName);
        long start = System.currentTimeMillis();

        var chFuture = CompletableFuture.supplyAsync(() ->
                companiesHouseSource.fetch(companyNumber, companyName, jurisdiction), executor
        ).orTimeout(timeoutSeconds, TimeUnit.SECONDS).exceptionally(ex -> {
            log.error("Companies House source timed out or failed: {}", ex.getMessage());
            return DataSourceResult.failure(companiesHouseSource.name(),
                    System.currentTimeMillis() - start, ex.getMessage());
        });

        var amFuture = CompletableFuture.supplyAsync(() ->
                adverseMediaSource.fetch(companyNumber, companyName, jurisdiction), executor
        ).orTimeout(timeoutSeconds, TimeUnit.SECONDS).exceptionally(ex -> {
            log.error("Adverse media source timed out or failed: {}", ex.getMessage());
            return DataSourceResult.failure(adverseMediaSource.name(),
                    System.currentTimeMillis() - start, ex.getMessage());
        });

        CompletableFuture.allOf(chFuture, amFuture).join();

        var result = new GatheredData(chFuture.join(), amFuture.join());

        long duration = System.currentTimeMillis() - start;
        log.info("Data gathering completed in {}ms. CH: {}, Adverse Media: {}",
                duration,
                result.companiesHouse().success() ? "success" : "failed",
                result.adverseMedia().success() ? "success" : "failed");

        return result;
    }
}
