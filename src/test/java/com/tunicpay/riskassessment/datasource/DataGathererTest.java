package com.tunicpay.riskassessment.datasource;

import com.tunicpay.riskassessment.config.AppConfig;
import com.tunicpay.riskassessment.model.CompanyProfile;
import com.tunicpay.riskassessment.model.Officer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataGathererTest {

    private DataSource<CompaniesHouseData> chSource;
    private DataSource<List<?>> amSource;
    private DataGatherer gatherer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        chSource = mock(DataSource.class);
        amSource = mock(DataSource.class);
        when(chSource.name()).thenReturn("companies-house");
        when(amSource.name()).thenReturn("adverse-media");

        var appConfig = new AppConfig();
        appConfig.getAssessment().setSourceTimeoutSeconds(5);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        gatherer = new DataGatherer(List.of(chSource, amSource), executor, appConfig);
    }

    private CompaniesHouseData buildChData() {
        return new CompaniesHouseData(
                new CompanyProfile("TEST", "123", "active", "2020-01-01", null, "Address"),
                List.of(new Officer("Name", "director", "2020-01-01", null)),
                List.of(), List.of(), List.of()
        );
    }

    @Test
    void gatherReturnsBothSourcesOnSuccess() {
        when(chSource.fetch("123", "TEST", "GB"))
                .thenReturn(DataSourceResult.success(buildChData(), "companies-house", 50));
        when(amSource.fetch("123", "TEST", "GB"))
                .thenReturn(DataSourceResult.success(List.of(), "adverse-media", 30));

        GatheredData result = gatherer.gather("123", "TEST", "GB");

        assertTrue(result.<CompaniesHouseData>get("companies-house").success());
        assertTrue(result.<List<?>>get("adverse-media").success());
    }

    @Test
    void gatherHandlesCompaniesHouseFailure() {
        when(chSource.fetch("123", "TEST", "GB"))
                .thenThrow(new RuntimeException("Connection refused"));
        when(amSource.fetch("123", "TEST", "GB"))
                .thenReturn(DataSourceResult.success(List.of(), "adverse-media", 30));

        GatheredData result = gatherer.gather("123", "TEST", "GB");

        assertFalse(result.<CompaniesHouseData>get("companies-house").success());
        assertTrue(result.<List<?>>get("adverse-media").success());
    }

    @Test
    void gatherHandlesAdverseMediaFailure() {
        when(chSource.fetch("123", "TEST", "GB"))
                .thenReturn(DataSourceResult.success(buildChData(), "companies-house", 50));
        when(amSource.fetch("123", "TEST", "GB"))
                .thenThrow(new RuntimeException("LLM error"));

        GatheredData result = gatherer.gather("123", "TEST", "GB");

        assertTrue(result.<CompaniesHouseData>get("companies-house").success());
        assertFalse(result.<List<?>>get("adverse-media").success());
    }

    @Test
    void gatherHandlesBothSourcesFailure() {
        when(chSource.fetch("123", "TEST", "GB"))
                .thenThrow(new RuntimeException("CH down"));
        when(amSource.fetch("123", "TEST", "GB"))
                .thenThrow(new RuntimeException("LLM down"));

        GatheredData result = gatherer.gather("123", "TEST", "GB");

        assertFalse(result.<CompaniesHouseData>get("companies-house").success());
        assertFalse(result.<List<?>>get("adverse-media").success());
    }

    @Test
    void gatherExecutesBothSourcesInParallel() {
        when(chSource.fetch("123", "TEST", "GB"))
                .thenAnswer(inv -> DataSourceResult.success(buildChData(), "companies-house", 100));
        when(amSource.fetch("123", "TEST", "GB"))
                .thenAnswer(inv -> DataSourceResult.success(List.of(), "adverse-media", 100));

        long start = System.currentTimeMillis();
        GatheredData result = gatherer.gather("123", "TEST", "GB");
        long duration = System.currentTimeMillis() - start;

        assertTrue(result.<CompaniesHouseData>get("companies-house").success());
        assertTrue(result.<List<?>>get("adverse-media").success());
        assertTrue(duration < 500, "Sources should run in parallel, took " + duration + "ms");
    }
}
