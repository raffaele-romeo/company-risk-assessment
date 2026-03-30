package com.tunicpay.riskassessment.service;

import com.tunicpay.riskassessment.datasource.DataSource;
import com.tunicpay.riskassessment.datasource.DataSourceResult;
import com.tunicpay.riskassessment.datasource.GatheredData;
import com.tunicpay.riskassessment.model.Confidence;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConfidenceCalculator {

    private final Map<String, DataSource<?>> sourcesByName;

    public ConfidenceCalculator(List<DataSource<?>> dataSources) {
        this.sourcesByName = dataSources.stream()
                .collect(Collectors.toMap(DataSource::name, Function.identity()));
    }

    @SuppressWarnings("unchecked")
    public Confidence compute(GatheredData gathered) {
        List<String> sourcesUsed = new ArrayList<>();
        List<String> sourcesFailed = new ArrayList<>();

        int sectionsWithData = 0;
        int totalSections = 0;
        int successfulSources = 0;

        for (var entry : gathered.all().entrySet()) {
            String name = entry.getKey();
            DataSourceResult<?> result = entry.getValue();
            DataSource<Object> source = (DataSource<Object>) sourcesByName.get(name);

            totalSections += source.totalSections();

            if (result.success()) {
                sourcesUsed.add(result.sourceName());
                successfulSources++;
                sectionsWithData += source.populatedSections(result.data());
            } else {
                sourcesFailed.add(result.sourceName());
            }
        }

        int totalSourceCount = sourcesByName.size();
        double completeness = totalSections > 0 ? (double) sectionsWithData / totalSections : 0.0;
        double sourceCoverage = totalSourceCount > 0 ? (double) successfulSources / totalSourceCount : 0.0;

        return new Confidence(
                Math.round(completeness * 100.0) / 100.0,
                Math.round(sourceCoverage * 100.0) / 100.0,
                sourcesUsed,
                sourcesFailed
        );
    }
}
