package com.tunicpay.riskassessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunicpay.riskassessment.config.AppConfig;
import com.tunicpay.riskassessment.exception.UpstreamApiException;
import com.tunicpay.riskassessment.model.CompanyCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@ConditionalOnExpression("'${app.companies-house.api-key:}' != ''")
public class RealCompaniesHouseSearchService implements CompaniesHouseSearchService {

    private static final Logger log = LoggerFactory.getLogger(RealCompaniesHouseSearchService.class);

    private final AppConfig.CompaniesHouse config;
    private final String authHeader;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RealCompaniesHouseSearchService(AppConfig appConfig, HttpClient httpClient, ObjectMapper objectMapper) {
        this.config = appConfig.getCompaniesHouse();
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((config.getApiKey() + ":").getBytes(StandardCharsets.UTF_8));
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        log.info("Using REAL Companies House search service");
    }

    @Override
    public List<CompanyCandidate> searchByName(String companyName, String jurisdiction) {
        log.info("Real search by name: {}", companyName);
        String encoded = URLEncoder.encode(companyName, StandardCharsets.UTF_8);
        String url = config.getBaseUrl() + "/search/companies?q=" + encoded + "&items_per_page=20";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", authHeader)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw new UpstreamApiException("CompaniesHouse", 401,
                        "API key is invalid or expired");
            }
            if (response.statusCode() != 200) {
                log.error("Companies House search returned status {}", response.statusCode());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.path("items");
            List<CompanyCandidate> results = new ArrayList<>();
            for (JsonNode item : items) {
                results.add(new CompanyCandidate(
                        item.path("title").asText(""),
                        item.path("company_number").asText(""),
                        item.path("company_status").asText(""),
                        item.path("date_of_creation").asText(null),
                        item.path("date_of_cessation").asText(null),
                        false
                ));
            }
            return results;
        } catch (UpstreamApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Companies House search failed", e);
            return List.of();
        }
    }

    @Override
    public CompanyCandidate lookupByNumber(String companyNumber, String jurisdiction) {
        log.info("Real lookup by number: {}", companyNumber);
        String url = config.getBaseUrl() + "/company/" + companyNumber;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", authHeader)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw new UpstreamApiException("CompaniesHouse", 401,
                        "API key is invalid or expired");
            }
            if (response.statusCode() == 404) {
                log.info("No company found with number {}", companyNumber);
                return null;
            }
            if (response.statusCode() != 200) {
                log.error("Companies House lookup returned status {}", response.statusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            return new CompanyCandidate(
                    root.path("company_name").asText(""),
                    root.path("company_number").asText(""),
                    root.path("company_status").asText(""),
                    root.path("date_of_creation").asText(null),
                    root.path("date_of_cessation").asText(null),
                    false
            );
        } catch (UpstreamApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Companies House lookup failed", e);
            return null;
        }
    }
}
