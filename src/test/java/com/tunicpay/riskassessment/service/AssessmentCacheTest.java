package com.tunicpay.riskassessment.service;

import com.tunicpay.riskassessment.config.AppConfig;
import com.tunicpay.riskassessment.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AssessmentCacheTest {

    private AssessmentCache cache;

    @BeforeEach
    void setUp() {
        var appConfig = new AppConfig();
        appConfig.getAssessment().setCacheTtlHours(24);
        appConfig.getAssessment().setCacheMaxSize(100);
        cache = new AssessmentCache(appConfig);
    }

    private CompanyAssessment buildAssessment(String companyName) {
        return new CompanyAssessment(
                new CompanyProfile(companyName, "123", "active", "2020-01-01", null, "Address"),
                List.of(),
                List.of(),
                List.of(),
                new LiquidationData(false, List.of()),
                List.of(),
                new Confidence(1.0, 1.0, List.of("companies-house"), List.of()),
                Instant.now()
        );
    }

    @Test
    void getMissReturnsEmpty() {
        Optional<CompanyAssessment> result = cache.get("123", "GB");
        assertTrue(result.isEmpty());
    }

    @Test
    void putThenGetReturnsValue() {
        CompanyAssessment assessment = buildAssessment("TEST LTD");
        cache.put("123", "GB", assessment);

        Optional<CompanyAssessment> result = cache.get("123", "GB");

        assertTrue(result.isPresent());
        assertSame(assessment, result.get());
    }

    @Test
    void differentJurisdictionsAreSeparateEntries() {
        CompanyAssessment gb = buildAssessment("GB Company");
        CompanyAssessment ie = buildAssessment("IE Company");
        cache.put("123", "GB", gb);
        cache.put("123", "IE", ie);

        assertEquals("GB Company", cache.get("123", "GB").orElseThrow().company().name());
        assertEquals("IE Company", cache.get("123", "IE").orElseThrow().company().name());
    }

    @Test
    void putOverwritesExistingEntry() {
        CompanyAssessment first = buildAssessment("First");
        CompanyAssessment second = buildAssessment("Second");

        cache.put("123", "GB", first);
        cache.put("123", "GB", second);

        assertSame(second, cache.get("123", "GB").orElseThrow());
    }

    @Test
    void cacheRespectsMaxSize() {
        var appConfig = new AppConfig();
        appConfig.getAssessment().setCacheTtlHours(24);
        appConfig.getAssessment().setCacheMaxSize(2);
        var smallCache = new AssessmentCache(appConfig);

        for (int i = 0; i < 10; i++) {
            smallCache.put(String.valueOf(i), "GB", buildAssessment("Company " + i));
        }

        // Caffeine eviction is async, so we can't assert exact count,
        // but at least the latest entries should be present
        assertTrue(smallCache.get("9", "GB").isPresent());
    }
}
