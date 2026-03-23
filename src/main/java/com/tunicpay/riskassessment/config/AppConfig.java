package com.tunicpay.riskassessment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    private final OpenRouter openrouter = new OpenRouter();
    private final CompaniesHouse companiesHouse = new CompaniesHouse();
    private final Assessment assessment = new Assessment();

    public OpenRouter getOpenrouter() { return openrouter; }
    public CompaniesHouse getCompaniesHouse() { return companiesHouse; }
    public Assessment getAssessment() { return assessment; }

    @Bean(destroyMethod = "close")
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean(destroyMethod = "close")
    public ExecutorService dataSourceExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    public static class OpenRouter {
        private String apiKey = "";
        private String model = "anthropic/claude-sonnet-4";
        private String baseUrl = "https://openrouter.ai/api/v1";
        private double temperature = 0;
        private int seed = 42;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getSeed() { return seed; }
        public void setSeed(int seed) { this.seed = seed; }
    }

    public static class CompaniesHouse {
        private String apiKey = "";
        private String baseUrl = "https://api.company-information.service.gov.uk";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class Assessment {
        private int cacheTtlHours = 24;
        private int cacheMaxSize = 1000;
        private int sourceTimeoutSeconds = 5;

        public int getCacheTtlHours() { return cacheTtlHours; }
        public void setCacheTtlHours(int cacheTtlHours) { this.cacheTtlHours = cacheTtlHours; }
        public int getCacheMaxSize() { return cacheMaxSize; }
        public void setCacheMaxSize(int cacheMaxSize) { this.cacheMaxSize = cacheMaxSize; }
        public int getSourceTimeoutSeconds() { return sourceTimeoutSeconds; }
        public void setSourceTimeoutSeconds(int sourceTimeoutSeconds) { this.sourceTimeoutSeconds = sourceTimeoutSeconds; }
    }
}
