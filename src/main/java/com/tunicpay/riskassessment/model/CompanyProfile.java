package com.tunicpay.riskassessment.model;

public record CompanyProfile(
        String name,
        String number,
        String status,
        String incorporationDate,
        String dateOfCessation,
        String registeredAddress
) {}
