package com.tunicpay.riskassessment.service;

import com.tunicpay.riskassessment.model.CompanyCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnExpression("'${app.companies-house.api-key:}' == ''")
public class MockCompaniesHouseSearchService implements CompaniesHouseSearchService {

    private static final Logger log = LoggerFactory.getLogger(MockCompaniesHouseSearchService.class);

    private static final List<CompanyCandidate> FIXTURES = List.of(
            new CompanyCandidate("TUNIC PAY LTD", "12345678", "active", "2020-03-15", null, false),
            new CompanyCandidate("TUNIC & CO UK LIMITED", "87654321", "active", "2015-07-22", null, false),
            new CompanyCandidate("ACME CONSULTING LTD", "11044276", "active", "2017-10-18", null, false),
            new CompanyCandidate("GLOBEX CORPORATION LTD", "99887766", "dissolved", "2010-01-05", "2023-06-15", false),
            new CompanyCandidate("STERLING IMPORTS LIMITED", "55443322", "active", "2019-06-30", null, false)
    );

    public MockCompaniesHouseSearchService() {
        log.info("Using MOCK Companies House search service (no API key configured)");
    }

    @Override
    public List<CompanyCandidate> searchByName(String companyName, String jurisdiction) {
        log.info("Mock search by name: {}", companyName);
        String upper = companyName.toUpperCase();
        return FIXTURES.stream()
                .filter(c -> c.name().toUpperCase().contains(upper))
                .toList();
    }

    @Override
    public CompanyCandidate lookupByNumber(String companyNumber, String jurisdiction) {
        log.info("Mock lookup by number: {}", companyNumber);
        return FIXTURES.stream()
                .filter(c -> c.number().equals(companyNumber))
                .findFirst()
                .orElse(null);
    }
}
