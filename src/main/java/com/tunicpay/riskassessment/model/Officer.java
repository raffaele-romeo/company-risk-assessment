package com.tunicpay.riskassessment.model;

public record Officer(
        String name,
        String role,
        String appointedDate,
        String resignedDate
) {}
