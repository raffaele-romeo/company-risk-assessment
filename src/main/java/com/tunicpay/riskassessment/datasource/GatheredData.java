package com.tunicpay.riskassessment.datasource;

import com.tunicpay.riskassessment.model.AdverseMediaFinding;

import java.util.List;

public record GatheredData(
        DataSourceResult<CompaniesHouseData> companiesHouse,
        DataSourceResult<List<AdverseMediaFinding>> adverseMedia
) {}
