package com.tunicpay.riskassessment.model;

import java.util.List;

public record LiquidationData(
        boolean hasLiquidation,
        List<LiquidationFiling> filings
) {}
