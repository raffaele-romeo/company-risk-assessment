package com.tunicpay.riskassessment.datasource;

import com.tunicpay.riskassessment.model.CompanyProfile;
import com.tunicpay.riskassessment.model.Officer;

import java.util.List;

public record CompaniesHouseData(
        CompanyProfile profile,
        List<Officer> officers,
        List<RawAccountFiling> accountFilings,
        List<RawConfirmationStatementFiling> confirmationStatementFilings,
        List<RawLiquidationFiling> liquidationFilings
) {

    public record RawAccountFiling(
            String filingDate,
            String madeUpDate,
            String type
    ) {}

    public record RawConfirmationStatementFiling(
            String filingDate,
            String madeUpDate,
            String type
    ) {}

    public record RawLiquidationFiling(
            String filingDate,
            String type,
            String description
    ) {}
}
