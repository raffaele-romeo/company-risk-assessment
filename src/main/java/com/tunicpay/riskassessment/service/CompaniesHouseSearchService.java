package com.tunicpay.riskassessment.service;

import com.tunicpay.riskassessment.model.CompanyCandidate;

import java.util.List;

public interface CompaniesHouseSearchService {

    List<CompanyCandidate> searchByName(String companyName, String jurisdiction);

    CompanyCandidate lookupByNumber(String companyNumber, String jurisdiction);
}
