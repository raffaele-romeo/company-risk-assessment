package com.tunicpay.riskassessment.controller;

import com.tunicpay.riskassessment.model.*;
import com.tunicpay.riskassessment.service.AssessmentService;
import com.tunicpay.riskassessment.service.CompaniesHouseSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private static final Logger log = LoggerFactory.getLogger(CompanyController.class);
    private static final Pattern COMPANY_NUMBER_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");
    private static final int MAX_NAME_LENGTH = 200;

    private final CompaniesHouseSearchService searchService;
    private final AssessmentService assessmentService;

    public CompanyController(CompaniesHouseSearchService searchService, AssessmentService assessmentService) {
        this.searchService = searchService;
        this.assessmentService = assessmentService;
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam(name = "company_name", required = false) String companyName,
            @RequestParam(name = "registration_number", required = false) String registrationNumber,
            @RequestParam(name = "jurisdiction") String jurisdiction) {

        log.info("Search request: name={}, number={}, jurisdiction={}", companyName, registrationNumber, jurisdiction);

        boolean hasName = companyName != null && !companyName.isBlank();
        boolean hasNumber = registrationNumber != null && !registrationNumber.isBlank();

        if (!hasName && !hasNumber) {
            throw new IllegalArgumentException("At least one of company_name or registration_number is required");
        }

        validateJurisdiction(jurisdiction);
        if (hasName) validateCompanyName(companyName);
        if (hasNumber) validateCompanyNumber(registrationNumber);

        if (hasNumber) {
            CompanyCandidate result = searchService.lookupByNumber(registrationNumber, jurisdiction);
            if (result == null) {
                return new SearchResponse(List.of(),
                        "No company found with registration number " + registrationNumber);
            }

            boolean nameMismatch = hasName
                    && !result.name().equalsIgnoreCase(companyName);
            CompanyCandidate candidate = new CompanyCandidate(
                    result.name(), result.number(), result.status(),
                    result.incorporationDate(), result.dateOfCessation(), nameMismatch);

            return new SearchResponse(List.of(candidate),
                    nameMismatch ? "Company found but name differs from provided name" : "Company found");
        }

        List<CompanyCandidate> results = searchService.searchByName(companyName, jurisdiction);
        if (results.isEmpty()) {
            return new SearchResponse(List.of(),
                    "No companies found. Try alternative names or use a registration number.");
        }

        return new SearchResponse(results,
                results.size() == 1 ? "Single match found" : results.size() + " matches found");
    }

    @GetMapping("/assess")
    public CompanyAssessment assess(
            @RequestParam(name = "company_number") String companyNumber,
            @RequestParam(name = "company_name") String companyName,
            @RequestParam(name = "jurisdiction") String jurisdiction) {

        log.info("Assess request: number={}, name={}, jurisdiction={}", companyNumber, companyName, jurisdiction);

        validateCompanyNumber(companyNumber);
        validateCompanyName(companyName);
        validateJurisdiction(jurisdiction);

        return assessmentService.assess(companyNumber, companyName, jurisdiction);
    }

    private static void validateCompanyNumber(String companyNumber) {
        if (companyNumber == null || companyNumber.isBlank()) {
            throw new IllegalArgumentException("Company number must not be blank");
        }
        if (!COMPANY_NUMBER_PATTERN.matcher(companyNumber).matches()) {
            throw new IllegalArgumentException("Company number must be 1-10 alphanumeric characters");
        }
    }

    private static void validateCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name must not be blank");
        }
        if (companyName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Company name must not exceed " + MAX_NAME_LENGTH + " characters");
        }
    }

    private static void validateJurisdiction(String jurisdiction) {
        if (jurisdiction == null || jurisdiction.isBlank()) {
            throw new IllegalArgumentException("Jurisdiction must not be blank");
        }
        if (jurisdiction.length() > 10) {
            throw new IllegalArgumentException("Jurisdiction code must not exceed 10 characters");
        }
    }
}
