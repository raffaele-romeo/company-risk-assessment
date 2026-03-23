package com.tunicpay.riskassessment.model;

import java.util.List;

public record SearchResponse(
        List<CompanyCandidate> candidates,
        String message
) {}
