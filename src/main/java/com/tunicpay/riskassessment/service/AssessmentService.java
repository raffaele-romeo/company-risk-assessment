package com.tunicpay.riskassessment.service;

import com.tunicpay.riskassessment.controller.UpstreamFailureException;
import com.tunicpay.riskassessment.datasource.CompaniesHouseData;
import com.tunicpay.riskassessment.datasource.DataGatherer;
import com.tunicpay.riskassessment.datasource.GatheredData;
import com.tunicpay.riskassessment.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);

    private final DataGatherer dataGatherer;
    private final FilingDeadlineCalculator deadlineCalculator;
    private final ConfidenceCalculator confidenceCalculator;
    private final AssessmentCache cache;

    public AssessmentService(DataGatherer dataGatherer,
                             FilingDeadlineCalculator deadlineCalculator,
                             ConfidenceCalculator confidenceCalculator,
                             AssessmentCache cache) {
        this.dataGatherer = dataGatherer;
        this.deadlineCalculator = deadlineCalculator;
        this.confidenceCalculator = confidenceCalculator;
        this.cache = cache;
    }

    public CompanyAssessment assess(String companyNumber, String companyName, String jurisdiction) {
        log.info("Starting assessment for {} ({})", companyName, companyNumber);
        long start = System.currentTimeMillis();

        Optional<CompanyAssessment> cached = cache.get(companyNumber, jurisdiction);
        if (cached.isPresent()) {
            return cached.get();
        }

        GatheredData gathered = dataGatherer.gather(companyNumber, companyName, jurisdiction);

        if (!gathered.companiesHouse().success() && !gathered.adverseMedia().success()) {
            throw new UpstreamFailureException("All data sources failed for company " + companyNumber);
        }

        CompanyProfile profile = null;
        List<Officer> officers = List.of();
        List<AccountFiling> accounts = List.of();
        List<ConfirmationStatementFiling> confirmationStatements = List.of();
        LiquidationData liquidation = new LiquidationData(false, List.of());

        if (gathered.companiesHouse().success()) {
            CompaniesHouseData chData = gathered.companiesHouse().data();
            profile = chData.profile();
            officers = chData.officers();
            String cessation = profile != null ? profile.dateOfCessation() : null;
            accounts = deadlineCalculator.computeAccountFilings(chData.accountFilings(), cessation);
            confirmationStatements = deadlineCalculator.computeConfirmationStatementFilings(
                    chData.confirmationStatementFilings(), cessation);

            liquidation = deadlineCalculator.computeLiquidation(chData.liquidationFilings());
        }

        List<AdverseMediaFinding> adverseMedia = gathered.adverseMedia().success()
                ? gathered.adverseMedia().data()
                : List.of();

        Confidence confidence = confidenceCalculator.compute(gathered);

        CompanyAssessment assessment = new CompanyAssessment(
                profile,
                officers,
                accounts,
                confirmationStatements,
                liquidation,
                adverseMedia,
                confidence,
                Instant.now()
        );

        cache.put(companyNumber, jurisdiction, assessment);

        long duration = System.currentTimeMillis() - start;
        log.info("Assessment completed for {} in {}ms, completeness={}, source_coverage={}",
                companyNumber, duration, confidence.completenessScore(), confidence.sourceCoverage());

        return assessment;
    }
}
