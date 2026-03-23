package com.tunicpay.riskassessment.model;

import java.util.List;

public record Confidence(
        double completenessScore,
        double sourceCoverage,
        List<String> sourcesUsed,
        List<String> sourcesFailed
) {}
