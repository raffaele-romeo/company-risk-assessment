package com.tunicpay.riskassessment.model;

import java.time.Instant;
import java.util.List;

public record CompanyAssessment(
        CompanyProfile company,
        List<Officer> officers,
        List<AccountFiling> accounts,
        List<ConfirmationStatementFiling> confirmationStatements,
        LiquidationData liquidation,
        List<AdverseMediaFinding> adverseMedia,
        Confidence confidence,
        Instant assessedAt
) {}
