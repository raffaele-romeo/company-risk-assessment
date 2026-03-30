package com.tunicpay.riskassessment.datasource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunicpay.riskassessment.exception.DataParseException;
import com.tunicpay.riskassessment.llm.OpenRouterClient;
import com.tunicpay.riskassessment.model.AdverseMediaFinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdverseMediaDataSource implements DataSource<List<AdverseMediaFinding>> {

    private static final Logger log = LoggerFactory.getLogger(AdverseMediaDataSource.class);
    private static final String PROMPT_VERSION = "v1";

    private static final String SYSTEM_PROMPT = """
            You are an adverse media researcher. Given a company name and registration \
            number, search your knowledge for any negative information about this company.

            Look for:
            - Fraud allegations or scam reports
            - Regulatory actions, fines, or sanctions
            - Lawsuits or legal proceedings
            - Insolvency, liquidation, or financial distress
            - Director disqualifications or bans
            - County Court Judgments (CCJs)
            - Significant negative news coverage
            - Consumer complaints or whistleblower reports
            - Health and safety violations
            - Environmental violations

            Rules:
            - Output ONLY a valid JSON array matching the schema below. No markdown, no explanation, no code fences.
            - Report findings based on your training knowledge. Include well-known public events and news.
            - If you genuinely have no knowledge of any negative information, return an empty array: []
            - Include source attribution (e.g. news outlet, regulator name) where possible.

            Schema for each finding:
            { "source": "string", "headline": "string", "date": "string (ISO date) | null", "summary": "string" }""";

    private final OpenRouterClient llmClient;
    private final ObjectMapper objectMapper;

    public AdverseMediaDataSource(OpenRouterClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "adverse-media";
    }

    @Override
    public int totalSections() {
        return 1;
    }

    @Override
    public int populatedSections(List<AdverseMediaFinding> data) {
        return 1;
    }

    public String promptVersion() {
        return PROMPT_VERSION;
    }

    @Override
    public DataSourceResult<List<AdverseMediaFinding>> fetch(String companyNumber, String companyName, String jurisdiction) {
        long start = System.currentTimeMillis();
        log.info("Fetching adverse media for {} ({}) via LLM, prompt_version={}", companyName, companyNumber, PROMPT_VERSION);

        String userPrompt = "Company: %s (registration number: %s, jurisdiction: %s)"
                .formatted(companyName, companyNumber, jurisdiction);

        try {
            List<AdverseMediaFinding> findings = llmClient.chatCompletionWithRetry(
                    SYSTEM_PROMPT, userPrompt, this::parseResponse);

            long duration = System.currentTimeMillis() - start;
            log.info("Adverse media: {} findings in {}ms for {}", findings.size(), duration, companyName);
            return DataSourceResult.success(findings, name(), duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Adverse media failed after retry in {}ms for {}: {}", duration, companyName, e.getMessage());
            return DataSourceResult.failure(name(), duration, e.getMessage());
        }
    }

    private List<AdverseMediaFinding> parseResponse(String response) {
        String trimmed = response.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        try {
            return objectMapper.readValue(trimmed, new TypeReference<>() {});
        } catch (Exception e) {
            throw new DataParseException("Invalid JSON from LLM: " + e.getMessage(), e);
        }
    }
}
