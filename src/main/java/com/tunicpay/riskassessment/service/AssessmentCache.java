package com.tunicpay.riskassessment.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tunicpay.riskassessment.config.AppConfig;
import com.tunicpay.riskassessment.model.CompanyAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class AssessmentCache {

    private static final Logger log = LoggerFactory.getLogger(AssessmentCache.class);

    private final Cache<String, CompanyAssessment> cache;

    public AssessmentCache(AppConfig appConfig) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(appConfig.getAssessment().getCacheMaxSize())
                .expireAfterWrite(appConfig.getAssessment().getCacheTtlHours(), TimeUnit.HOURS)
                .build();
    }

    public Optional<CompanyAssessment> get(String companyNumber, String jurisdiction) {
        String key = key(jurisdiction, companyNumber);
        CompanyAssessment entry = cache.getIfPresent(key);
        if (entry == null) {
            log.debug("Cache miss for {}", key);
            return Optional.empty();
        }
        log.debug("Cache hit for {}", key);
        return Optional.of(entry);
    }

    public void put(String companyNumber, String jurisdiction, CompanyAssessment assessment) {
        String key = key(jurisdiction, companyNumber);
        cache.put(key, assessment);
        log.debug("Cached assessment for {}", key);
    }

    private static String key(String jurisdiction, String companyNumber) {
        return jurisdiction + ":" + companyNumber;
    }
}
