package com.tunicpay.riskassessment.service;

import com.tunicpay.riskassessment.datasource.GatheredData;
import com.tunicpay.riskassessment.model.Confidence;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConfidenceCalculator {

    private static final int TOTAL_SECTIONS = 6;
    private static final int TOTAL_SOURCES = 2;

    public Confidence compute(GatheredData gathered) {
        List<String> sourcesUsed = new ArrayList<>();
        List<String> sourcesFailed = new ArrayList<>();

        int sectionsWithData = 0;
        int successfulSources = 0;

        if (gathered.companiesHouse().success()) {
            sourcesUsed.add(gathered.companiesHouse().sourceName());
            successfulSources++;
            var data = gathered.companiesHouse().data();
            if (data.profile() != null) sectionsWithData++;
            if (data.officers() != null && !data.officers().isEmpty()) sectionsWithData++;
            if (data.accountFilings() != null) sectionsWithData++;
            if (data.confirmationStatementFilings() != null) sectionsWithData++;
            if (data.liquidationFilings() != null) sectionsWithData++;
        } else {
            sourcesFailed.add(gathered.companiesHouse().sourceName());
        }

        if (gathered.adverseMedia().success()) {
            sourcesUsed.add(gathered.adverseMedia().sourceName());
            successfulSources++;
            sectionsWithData++;
        } else {
            sourcesFailed.add(gathered.adverseMedia().sourceName());
        }

        double completeness = (double) sectionsWithData / TOTAL_SECTIONS;
        double sourceCoverage = (double) successfulSources / TOTAL_SOURCES;

        return new Confidence(
                Math.round(completeness * 100.0) / 100.0,
                Math.round(sourceCoverage * 100.0) / 100.0,
                sourcesUsed,
                sourcesFailed
        );
    }
}
