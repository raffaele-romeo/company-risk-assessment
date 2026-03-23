package com.tunicpay.riskassessment.model;

public record LiquidationFiling(
        String filingDate,
        String type,
        String description
) {}
