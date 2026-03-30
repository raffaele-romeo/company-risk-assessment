package com.tunicpay.riskassessment.service;

import com.tunicpay.riskassessment.datasource.CompaniesHouseData;
import com.tunicpay.riskassessment.datasource.DataSource;
import com.tunicpay.riskassessment.datasource.DataSourceResult;
import com.tunicpay.riskassessment.datasource.GatheredData;
import com.tunicpay.riskassessment.model.AdverseMediaFinding;
import com.tunicpay.riskassessment.model.CompanyProfile;
import com.tunicpay.riskassessment.model.Confidence;
import com.tunicpay.riskassessment.model.Officer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConfidenceCalculatorTest {

    private DataSource<CompaniesHouseData> chSource;
    private DataSource<List<AdverseMediaFinding>> amSource;
    private ConfidenceCalculator calculator;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        chSource = mock(DataSource.class);
        amSource = mock(DataSource.class);
        when(chSource.name()).thenReturn("companies-house");
        when(amSource.name()).thenReturn("adverse-media");
        when(chSource.totalSections()).thenReturn(5);
        when(amSource.totalSections()).thenReturn(1);
        when(amSource.populatedSections(any())).thenReturn(1);

        calculator = new ConfidenceCalculator(List.of(chSource, amSource));
    }

    private GatheredData gathered(DataSourceResult<?> ch, DataSourceResult<?> am) {
        Map<String, DataSourceResult<?>> map = new LinkedHashMap<>();
        map.put("companies-house", ch);
        map.put("adverse-media", am);
        return new GatheredData(map);
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
        when(chSource.populatedSections(chData)).thenReturn(5);

        Confidence result = calculator.compute(gathered(
                DataSourceResult.success(chData, "companies-house", 100),
                DataSourceResult.success(List.of(), "adverse-media", 50)
        ));

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
        when(chSource.populatedSections(chData)).thenReturn(5);

        Confidence result = calculator.compute(gathered(
                DataSourceResult.success(chData, "companies-house", 100),
                DataSourceResult.failure("adverse-media", 5000, "timeout")
        ));

        assertEquals(0.83, result.completenessScore());
        assertEquals(0.5, result.sourceCoverage());
        assertEquals(List.of("companies-house"), result.sourcesUsed());
        assertEquals(List.of("adverse-media"), result.sourcesFailed());
    }

    @Test
    void partialCoverageWhenCompaniesHouseFails() {
        Confidence result = calculator.compute(gathered(
                DataSourceResult.failure("companies-house", 5000, "timeout"),
                DataSourceResult.success(List.of(), "adverse-media", 50)
        ));

        assertEquals(0.17, result.completenessScore());
        assertEquals(0.5, result.sourceCoverage());
        assertEquals(List.of("adverse-media"), result.sourcesUsed());
        assertEquals(List.of("companies-house"), result.sourcesFailed());
    }

    @Test
    void zeroCoverageWhenAllSourcesFail() {
        Confidence result = calculator.compute(gathered(
                DataSourceResult.failure("companies-house", 5000, "timeout"),
                DataSourceResult.failure("adverse-media", 5000, "timeout")
        ));

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
        when(chSource.populatedSections(chData)).thenReturn(4);

        Confidence result = calculator.compute(gathered(
                DataSourceResult.success(chData, "companies-house", 100),
                DataSourceResult.success(List.of(), "adverse-media", 50)
        ));

        assertEquals(0.83, result.completenessScore());
        assertEquals(1.0, result.sourceCoverage());
    }
}
