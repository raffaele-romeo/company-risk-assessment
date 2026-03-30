package com.tunicpay.riskassessment.service;

import com.tunicpay.riskassessment.config.AppConfig;
import com.tunicpay.riskassessment.controller.UpstreamFailureException;
import com.tunicpay.riskassessment.datasource.*;
import com.tunicpay.riskassessment.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssessmentServiceTest {

    private DataGatherer dataGatherer;
    private AssessmentCache cache;
    private AssessmentService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        dataGatherer = mock(DataGatherer.class);
        var appConfig = new AppConfig();
        cache = new AssessmentCache(appConfig);

        DataSource<CompaniesHouseData> chSource = mock(DataSource.class);
        DataSource<List<AdverseMediaFinding>> amSource = mock(DataSource.class);
        when(chSource.name()).thenReturn("companies-house");
        when(amSource.name()).thenReturn("adverse-media");
        when(chSource.totalSections()).thenReturn(5);
        when(amSource.totalSections()).thenReturn(1);
        when(chSource.populatedSections(any())).thenReturn(5);
        when(amSource.populatedSections(any())).thenReturn(1);

        service = new AssessmentService(
                dataGatherer,
                new FilingProcessor(),
                new ConfidenceCalculator(List.of(chSource, amSource)),
                cache
        );
    }

    private CompaniesHouseData buildChData() {
        return new CompaniesHouseData(
                new CompanyProfile("TEST LTD", "123", "active", "2020-01-01", null, "1 Test St"),
                List.of(new Officer("Smith", "director", "2020-01-01", null)),
                List.of(new CompaniesHouseData.RawAccountFiling("2024-08-01", "2024-01-31", "AA")),
                List.of(new CompaniesHouseData.RawConfirmationStatementFiling("2024-01-20", "2024-01-15", "CS01")),
                List.of()
        );
    }

    private GatheredData gathered(DataSourceResult<?> ch, DataSourceResult<?> am) {
        Map<String, DataSourceResult<?>> map = new LinkedHashMap<>();
        map.put("companies-house", ch);
        map.put("adverse-media", am);
        return new GatheredData(map);
    }

    @Test
    void assessReturnsFullAssessmentWhenAllSourcesSucceed() {
        var g = gathered(
                DataSourceResult.success(buildChData(), "companies-house", 100),
                DataSourceResult.success(List.of(), "adverse-media", 50)
        );
        when(dataGatherer.gather("123", "TEST LTD", "GB")).thenReturn(g);

        CompanyAssessment result = service.assess("123", "TEST LTD", "GB");

        assertNotNull(result.company());
        assertEquals("TEST LTD", result.company().name());
        assertEquals(1, result.officers().size());
        assertEquals(1, result.accounts().size());
        assertEquals(1, result.confirmationStatements().size());
        assertFalse(result.liquidation().hasLiquidation());
        assertNotNull(result.confidence());
        assertEquals(1.0, result.confidence().sourceCoverage());
        assertNotNull(result.assessedAt());
    }

    @Test
    void assessHandlesPartialFailureWhenChFails() {
        var adverseMedia = List.of(
                new AdverseMediaFinding("BBC", "Test headline", "2024-01-01", "Test summary")
        );
        var g = gathered(
                DataSourceResult.failure("companies-house", 5000, "timeout"),
                DataSourceResult.success(adverseMedia, "adverse-media", 50)
        );
        when(dataGatherer.gather("123", "TEST LTD", "GB")).thenReturn(g);

        CompanyAssessment result = service.assess("123", "TEST LTD", "GB");

        assertNull(result.company());
        assertTrue(result.officers().isEmpty());
        assertTrue(result.accounts().isEmpty());
        assertEquals(1, result.adverseMedia().size());
        assertEquals(0.5, result.confidence().sourceCoverage());
    }

    @Test
    void assessHandlesPartialFailureWhenAdverseMediaFails() {
        var g = gathered(
                DataSourceResult.success(buildChData(), "companies-house", 100),
                DataSourceResult.failure("adverse-media", 5000, "timeout")
        );
        when(dataGatherer.gather("123", "TEST LTD", "GB")).thenReturn(g);

        CompanyAssessment result = service.assess("123", "TEST LTD", "GB");

        assertNotNull(result.company());
        assertTrue(result.adverseMedia().isEmpty());
        assertEquals(0.5, result.confidence().sourceCoverage());
    }

    @Test
    void assessThrowsWhenAllSourcesFail() {
        var g = gathered(
                DataSourceResult.failure("companies-house", 5000, "timeout"),
                DataSourceResult.failure("adverse-media", 5000, "timeout")
        );
        when(dataGatherer.gather("123", "TEST LTD", "GB")).thenReturn(g);

        assertThrows(UpstreamFailureException.class,
                () -> service.assess("123", "TEST LTD", "GB"));
    }

    @Test
    void assessReturnsCachedResultOnSecondCall() {
        var g = gathered(
                DataSourceResult.success(buildChData(), "companies-house", 100),
                DataSourceResult.success(List.of(), "adverse-media", 50)
        );
        when(dataGatherer.gather("123", "TEST LTD", "GB")).thenReturn(g);

        CompanyAssessment first = service.assess("123", "TEST LTD", "GB");
        CompanyAssessment second = service.assess("123", "TEST LTD", "GB");

        assertSame(first, second);
        verify(dataGatherer, times(1)).gather("123", "TEST LTD", "GB");
    }

    @Test
    void assessComputesLiquidationCorrectly() {
        var chData = new CompaniesHouseData(
                new CompanyProfile("GONE LTD", "999", "dissolved", "2010-01-01", "2023-06-15", "1 Test St"),
                List.of(),
                List.of(),
                List.of(),
                List.of(new CompaniesHouseData.RawLiquidationFiling("2023-03-01", "LRESSP", "Statement of affairs"))
        );
        var g = gathered(
                DataSourceResult.success(chData, "companies-house", 100),
                DataSourceResult.success(List.of(), "adverse-media", 50)
        );
        when(dataGatherer.gather("999", "GONE LTD", "GB")).thenReturn(g);

        CompanyAssessment result = service.assess("999", "GONE LTD", "GB");

        assertTrue(result.liquidation().hasLiquidation());
        assertEquals(1, result.liquidation().filings().size());
    }
}
