package com.tunicpay.riskassessment.service;

import com.tunicpay.riskassessment.datasource.CompaniesHouseData.RawAccountFiling;
import com.tunicpay.riskassessment.datasource.CompaniesHouseData.RawConfirmationStatementFiling;
import com.tunicpay.riskassessment.datasource.CompaniesHouseData.RawLiquidationFiling;
import com.tunicpay.riskassessment.model.AccountFiling;
import com.tunicpay.riskassessment.model.ConfirmationStatementFiling;
import com.tunicpay.riskassessment.model.LiquidationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FilingProcessorTest {

    private FilingProcessor calculator;

    @BeforeEach
    void setUp() {
        calculator = new FilingProcessor();
    }

    // --- Account filings ---

    @Test
    void accountFilingWithinFiveYearsIsIncluded() {
        var raw = List.of(new RawAccountFiling("2024-08-23", "2024-01-31", "AA"));
        List<AccountFiling> result = calculator.computeAccountFilings(raw, null);

        assertEquals(1, result.size());
        assertEquals("2024-08-23", result.getFirst().filingDate());
        assertEquals("2024-01-31", result.getFirst().madeUpDate());
        assertEquals("AA", result.getFirst().type());
    }

    @Test
    void accountFilingOlderThanFiveYearsIsExcluded() {
        var raw = List.of(new RawAccountFiling("2015-01-01", "2014-06-30", "AA"));
        List<AccountFiling> result = calculator.computeAccountFilings(raw, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void accountFilingFilterUsesDateOfCessation() {
        var raw = List.of(new RawAccountFiling("2016-06-01", "2016-01-31", "AA"));
        List<AccountFiling> result = calculator.computeAccountFilings(raw, "2020-01-01");

        assertEquals(1, result.size());
    }

    @Test
    void accountFilingBeforeCessationCutoffIsExcluded() {
        var raw = List.of(new RawAccountFiling("2014-06-01", "2014-01-31", "AA"));
        List<AccountFiling> result = calculator.computeAccountFilings(raw, "2020-01-01");

        assertTrue(result.isEmpty());
    }

    @Test
    void multipleAccountFilingsFiltered() {
        var raw = List.of(
                new RawAccountFiling("2024-08-23", "2024-01-31", "AA"),
                new RawAccountFiling("2010-01-01", "2009-06-30", "AA")
        );
        List<AccountFiling> result = calculator.computeAccountFilings(raw, null);

        assertEquals(1, result.size());
        assertEquals("2024-08-23", result.getFirst().filingDate());
    }

    @Test
    void accountFilingWithNullFilingDateIsExcluded() {
        var raw = List.of(new RawAccountFiling(null, "2024-01-31", "AA"));
        List<AccountFiling> result = calculator.computeAccountFilings(raw, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void emptyAccountFilingsReturnsEmptyList() {
        List<AccountFiling> result = calculator.computeAccountFilings(List.of(), null);
        assertTrue(result.isEmpty());
    }

    // --- Confirmation statement filings ---

    @Test
    void confirmationStatementWithinFiveYearsIsIncluded() {
        var raw = List.of(new RawConfirmationStatementFiling("2024-06-07", "2024-06-05", "CS01"));
        List<ConfirmationStatementFiling> result = calculator.computeConfirmationStatementFilings(raw, null);

        assertEquals(1, result.size());
        assertEquals("2024-06-07", result.getFirst().filingDate());
        assertEquals("2024-06-05", result.getFirst().madeUpDate());
    }

    @Test
    void confirmationStatementOlderThanFiveYearsIsExcluded() {
        var raw = List.of(new RawConfirmationStatementFiling("2015-01-01", "2015-01-01", "CS01"));
        List<ConfirmationStatementFiling> result = calculator.computeConfirmationStatementFilings(raw, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void confirmationStatementFilterUsesDateOfCessation() {
        var raw = List.of(new RawConfirmationStatementFiling("2016-06-07", "2016-06-05", "CS01"));
        List<ConfirmationStatementFiling> result = calculator.computeConfirmationStatementFilings(raw, "2020-01-01");

        assertEquals(1, result.size());
    }

    @Test
    void emptyConfirmationStatementsReturnsEmptyList() {
        List<ConfirmationStatementFiling> result = calculator.computeConfirmationStatementFilings(List.of(), null);
        assertTrue(result.isEmpty());
    }

    // --- Liquidation ---

    @Test
    void liquidationWithFilingsHasLiquidationTrue() {
        var raw = List.of(new RawLiquidationFiling("2023-03-01", "LRESSP", "Statement of affairs"));
        LiquidationData result = calculator.computeLiquidation(raw);

        assertTrue(result.hasLiquidation());
        assertEquals(1, result.filings().size());
        assertEquals("2023-03-01", result.filings().getFirst().filingDate());
        assertEquals("LRESSP", result.filings().getFirst().type());
        assertEquals("Statement of affairs", result.filings().getFirst().description());
    }

    @Test
    void liquidationWithNoFilingsHasLiquidationFalse() {
        LiquidationData result = calculator.computeLiquidation(List.of());

        assertFalse(result.hasLiquidation());
        assertTrue(result.filings().isEmpty());
    }
}
