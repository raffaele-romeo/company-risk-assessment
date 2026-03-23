package com.tunicpay.riskassessment.service;

import com.tunicpay.riskassessment.datasource.CompaniesHouseData;
import com.tunicpay.riskassessment.model.AccountFiling;
import com.tunicpay.riskassessment.model.ConfirmationStatementFiling;
import com.tunicpay.riskassessment.model.LiquidationData;
import com.tunicpay.riskassessment.model.LiquidationFiling;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class FilingDeadlineCalculator {

    public List<AccountFiling> computeAccountFilings(List<CompaniesHouseData.RawAccountFiling> raw, String dateOfCessation) {
        LocalDate cutoff = cutoffDate(dateOfCessation);
        return raw.stream()
                .filter(r -> isWithinWindow(r.filingDate(), cutoff))
                .map(r -> new AccountFiling(r.filingDate(), r.madeUpDate(), r.type()))
                .toList();
    }

    public List<ConfirmationStatementFiling> computeConfirmationStatementFilings(
            List<CompaniesHouseData.RawConfirmationStatementFiling> raw, String dateOfCessation) {
        LocalDate cutoff = cutoffDate(dateOfCessation);
        return raw.stream()
                .filter(r -> isWithinWindow(r.filingDate(), cutoff))
                .map(r -> new ConfirmationStatementFiling(r.filingDate(), r.madeUpDate(), r.type()))
                .toList();
    }

    public LiquidationData computeLiquidation(List<CompaniesHouseData.RawLiquidationFiling> raw) {
        List<LiquidationFiling> filings = raw.stream()
                .map(r -> new LiquidationFiling(r.filingDate(), r.type(), r.description()))
                .toList();
        return new LiquidationData(!filings.isEmpty(), filings);
    }

    private LocalDate cutoffDate(String dateOfCessation) {
        LocalDate reference = parseDate(dateOfCessation);
        if (reference == null) {
            reference = LocalDate.now();
        }
        return reference.minusYears(5);
    }

    private boolean isWithinWindow(String filingDate, LocalDate cutoff) {
        LocalDate date = parseDate(filingDate);
        return date != null && !date.isBefore(cutoff);
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            return null;
        }
    }
}
