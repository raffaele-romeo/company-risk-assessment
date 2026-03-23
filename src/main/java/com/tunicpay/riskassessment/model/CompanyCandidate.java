package com.tunicpay.riskassessment.model;

public record CompanyCandidate(
        String name,
        String number,
        String status,
        String incorporationDate,
        String dateOfCessation,
        boolean nameMismatch
) {}
