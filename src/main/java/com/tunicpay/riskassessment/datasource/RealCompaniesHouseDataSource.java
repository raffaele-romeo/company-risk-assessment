package com.tunicpay.riskassessment.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunicpay.riskassessment.config.AppConfig;
import com.tunicpay.riskassessment.exception.DataParseException;
import com.tunicpay.riskassessment.exception.UpstreamApiException;
import com.tunicpay.riskassessment.model.CompanyProfile;
import com.tunicpay.riskassessment.model.Officer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnExpression("'${app.companies-house.api-key:}' != ''")
public class RealCompaniesHouseDataSource implements DataSource<CompaniesHouseData> {

    private static final Logger log = LoggerFactory.getLogger(RealCompaniesHouseDataSource.class);

    private final AppConfig.CompaniesHouse config;
    private final String authHeader;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RealCompaniesHouseDataSource(AppConfig appConfig, HttpClient httpClient, ObjectMapper objectMapper) {
        this.config = appConfig.getCompaniesHouse();
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((config.getApiKey() + ":").getBytes(StandardCharsets.UTF_8));
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        log.info("Using REAL Companies House data source");
    }

    @Override
    public String name() {
        return "companies-house";
    }

    @Override
    public DataSourceResult<CompaniesHouseData> fetch(String companyNumber, String companyName, String jurisdiction) {
        long start = System.currentTimeMillis();
        try {
            var profileFuture = fetchProfileAsync(companyNumber);
            var officersFuture = fetchOfficersAsync(companyNumber);
            var accountsFuture = fetchFilingHistoryAsync(companyNumber, "accounts");
            var confirmationFuture = fetchFilingHistoryAsync(companyNumber, "confirmation-statement");
            var liquidationFuture = fetchFilingHistoryAsync(companyNumber, "liquidation");

            CompletableFuture.allOf(profileFuture, officersFuture, accountsFuture, confirmationFuture, liquidationFuture).join();

            CompanyProfile profile = profileFuture.join();
            List<Officer> officers = officersFuture.join();
            JsonNode accountsItems = accountsFuture.join();
            JsonNode confirmationItems = confirmationFuture.join();
            JsonNode liquidationItems = liquidationFuture.join();

            var data = new CompaniesHouseData(
                    profile,
                    officers,
                    parseAccountFilings(accountsItems),
                    parseConfirmationStatementFilings(confirmationItems),
                    parseLiquidationFilings(liquidationItems)
            );

            long duration = System.currentTimeMillis() - start;
            log.info("Companies House data fetched in {}ms for {}", duration, companyNumber);
            return DataSourceResult.success(data, name(), duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Companies House data fetch failed for {} in {}ms", companyNumber, duration, e);
            return DataSourceResult.failure(name(), duration, e.getMessage());
        }
    }

    private CompletableFuture<CompanyProfile> fetchProfileAsync(String companyNumber) {
        String url = config.getBaseUrl() + "/company/" + companyNumber;
        return sendAsync(url).thenApply(this::parseProfile);
    }

    private CompletableFuture<List<Officer>> fetchOfficersAsync(String companyNumber) {
        String url = config.getBaseUrl() + "/company/" + companyNumber + "/officers";
        return sendAsyncAllowNotFound(url).thenApply(body -> body == null ? List.of() : parseOfficers(body));
    }

    private CompletableFuture<JsonNode> fetchFilingHistoryAsync(String companyNumber, String category) {
        String url = config.getBaseUrl() + "/company/" + companyNumber
                + "/filing-history?category=" + category + "&items_per_page=100";
        return sendAsyncAllowNotFound(url).thenApply(body -> body == null ? objectMapper.createArrayNode() : parseFilingItems(body));
    }

    private CompanyProfile parseProfile(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return new CompanyProfile(
                    root.path("company_name").asText(""),
                    root.path("company_number").asText(""),
                    root.path("company_status").asText(""),
                    root.path("date_of_creation").asText(null),
                    root.path("date_of_cessation").asText(null),
                    formatAddress(root.path("registered_office_address"))
            );
        } catch (Exception e) {
            throw new DataParseException("Failed to parse company profile", e);
        }
    }

    private List<Officer> parseOfficers(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("items");
            List<Officer> officers = new ArrayList<>();
            for (JsonNode item : items) {
                officers.add(new Officer(
                        item.path("name").asText(""),
                        item.path("officer_role").asText(""),
                        item.path("appointed_on").asText(null),
                        item.path("resigned_on").asText(null)
                ));
            }
            return officers;
        } catch (Exception e) {
            throw new DataParseException("Failed to parse officers", e);
        }
    }

    private JsonNode parseFilingItems(String body) {
        try {
            return objectMapper.readTree(body).path("items");
        } catch (Exception e) {
            throw new DataParseException("Failed to parse filing history", e);
        }
    }

    private CompletableFuture<HttpResponse<String>> sendAsyncRaw(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 401) {
                        throw new UpstreamApiException("CompaniesHouse", 401,
                                "API key is invalid or expired");
                    }
                    return response;
                });
    }

    private CompletableFuture<String> sendAsync(String url) {
        return sendAsyncRaw(url).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new UpstreamApiException("CompaniesHouse", response.statusCode(),
                        "request failed for " + url);
            }
            return response.body();
        });
    }

    private CompletableFuture<String> sendAsyncAllowNotFound(String url) {
        return sendAsyncRaw(url).thenApply(response -> {
            if (response.statusCode() == 404) {
                log.info("404 for {} — returning empty result", url);
                return null;
            }
            if (response.statusCode() != 200) {
                throw new UpstreamApiException("CompaniesHouse", response.statusCode(),
                        "request failed for " + url);
            }
            return response.body();
        });
    }

    private List<CompaniesHouseData.RawAccountFiling> parseAccountFilings(JsonNode items) {
        List<CompaniesHouseData.RawAccountFiling> filings = new ArrayList<>();
        for (JsonNode item : items) {
            filings.add(new CompaniesHouseData.RawAccountFiling(
                    item.path("date").asText(null),
                    item.path("description_values").path("made_up_date").asText(null),
                    item.path("type").asText("")
            ));
        }
        return filings;
    }

    private List<CompaniesHouseData.RawConfirmationStatementFiling> parseConfirmationStatementFilings(JsonNode items) {
        List<CompaniesHouseData.RawConfirmationStatementFiling> filings = new ArrayList<>();
        for (JsonNode item : items) {
            filings.add(new CompaniesHouseData.RawConfirmationStatementFiling(
                    item.path("date").asText(null),
                    item.path("description_values").path("made_up_date").asText(null),
                    item.path("type").asText("")
            ));
        }
        return filings;
    }

    private List<CompaniesHouseData.RawLiquidationFiling> parseLiquidationFilings(JsonNode items) {
        List<CompaniesHouseData.RawLiquidationFiling> filings = new ArrayList<>();
        for (JsonNode item : items) {
            filings.add(new CompaniesHouseData.RawLiquidationFiling(
                    item.path("date").asText(null),
                    item.path("type").asText(""),
                    item.path("description").asText("")
            ));
        }
        return filings;
    }

    private String formatAddress(JsonNode address) {
        if (address.isMissingNode()) return null;
        var parts = new ArrayList<String>();
        for (String field : List.of("address_line_1", "address_line_2", "locality", "region", "postal_code", "country")) {
            String value = address.path(field).asText(null);
            if (value != null && !value.isBlank()) {
                parts.add(value);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }
}
