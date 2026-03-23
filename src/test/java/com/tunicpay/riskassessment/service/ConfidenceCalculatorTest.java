package com.tunicpay.riskassessment.service;

import com.tunicpay.riskassessment.datasource.CompaniesHouseData;
import com.tunicpay.riskassessment.datasource.DataSourceResult;
import com.tunicpay.riskassessment.datasource.GatheredData;
import com.tunicpay.riskassessment.model.AdverseMediaFinding;
import com.tunicpay.riskassessment.model.CompanyProfile;
import com.tunicpay.riskassessment.model.Confidence;
import com.tunicpay.riskassessment.model.Officer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfidenceCalculatorTest {

    private ConfidenceCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ConfidenceCalculator();
    }

    @Test
    void fullCoverageWhenAllSourcesSucceed() {
        var chData = new CompaniesHouseData(
                new CompanyProfile("Test", "123", "active", "2020-01-01", null, "Address"),
                List.of(new Officer("Name", "director", "2020-01-01", null)),
                List.of(new CompaniesHouseData.RawAccountFiling("2024-01-01", "2023-12-31", "AA")),
                List.of(new CompaniesHouseData.RawConfirmationStatementFiling("2024-01-01", "2024-01-01", "CS01")),
                List.of()
        );
        var gathered = new GatheredData(
                DataSourceResult.success(chData, "companies-house", 100),
                DataSourceResult.success(List.of(), "adverse-media", 50)
        );

        Confidence result = calculator.compute(gathered);

        assertEquals(1.0, result.completenessScore());
        assertEquals(1.0, result.sourceCoverage());
        assertEquals(List.of("companies-house", "adverse-media"), result.sourcesUsed());
        assertTrue(result.sourcesFailed().isEmpty());
    }

    @Test
    void partialCoverageWhenAdverseMediaFails() {
        var chData = new CompaniesHouseData(
                new CompanyProfile("Test", "123", "active", "2020-01-01", null, "Address"),
                List.of(new Officer("Name", "director", "2020-01-01", null)),
                List.of(),
                List.of(),
                List.of()
        );
        var gathered = new GatheredData(
                DataSourceResult.success(chData, "companies-house", 100),
                DataSourceResult.failure("adverse-media", 5000, "timeout")
        );

        Confidence result = calculator.compute(gathered);

        assertEquals(0.83, result.completenessScore());
        assertEquals(0.5, result.sourceCoverage());
        assertEquals(List.of("companies-house"), result.sourcesUsed());
        assertEquals(List.of("adverse-media"), result.sourcesFailed());
    }

    @Test
    void partialCoverageWhenCompaniesHouseFails() {
        var gathered = new GatheredData(
                DataSourceResult.failure("companies-house", 5000, "timeout"),
                DataSourceResult.success(List.of(), "adverse-media", 50)
        );

        Confidence result = calculator.compute(gathered);

        assertEquals(0.17, result.completenessScore());
        assertEquals(0.5, result.sourceCoverage());
        assertEquals(List.of("adverse-media"), result.sourcesUsed());
        assertEquals(List.of("companies-house"), result.sourcesFailed());
    }

    @Test
    void zeroCoverageWhenAllSourcesFail() {
        var gathered = new GatheredData(
                DataSourceResult.failure("companies-house", 5000, "timeout"),
                DataSourceResult.failure("adverse-media", 5000, "timeout")
        );

        Confidence result = calculator.compute(gathered);

        assertEquals(0.0, result.completenessScore());
        assertEquals(0.0, result.sourceCoverage());
        assertTrue(result.sourcesUsed().isEmpty());
        assertEquals(List.of("companies-house", "adverse-media"), result.sourcesFailed());
    }

    @Test
    void emptyOfficersReducesCompleteness() {
        var chData = new CompaniesHouseData(
                new CompanyProfile("Test", "123", "active", "2020-01-01", null, "Address"),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        var gathered = new GatheredData(
                DataSourceResult.success(chData, "companies-house", 100),
                DataSourceResult.success(List.of(), "adverse-media", 50)
        );

        Confidence result = calculator.compute(gathered);

        assertEquals(0.83, result.completenessScore());
        assertEquals(1.0, result.sourceCoverage());
    }
}
