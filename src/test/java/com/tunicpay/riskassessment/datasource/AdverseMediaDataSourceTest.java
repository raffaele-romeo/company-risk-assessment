package com.tunicpay.riskassessment.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunicpay.riskassessment.llm.OpenRouterClient;
import com.tunicpay.riskassessment.model.AdverseMediaFinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdverseMediaDataSourceTest {

    private OpenRouterClient llmClient;
    private AdverseMediaDataSource dataSource;

    @BeforeEach
    void setUp() {
        llmClient = mock(OpenRouterClient.class);
        dataSource = new AdverseMediaDataSource(llmClient, new ObjectMapper());
    }

    @SuppressWarnings("unchecked")
    private void stubChatCompletionWithRetry(String response) {
        when(llmClient.chatCompletionWithRetry(anyString(), anyString(), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<String, ?> processor = invocation.getArgument(2);
                    return processor.apply(response);
                });
    }

    @Test
    void parsesValidJsonArray() {
        String json = """
                [{"source":"BBC","headline":"Company fined","date":"2024-01-01","summary":"Details"}]
                """;
        stubChatCompletionWithRetry(json);

        DataSourceResult<List<AdverseMediaFinding>> result = dataSource.fetch("123", "TEST", "GB");

        assertTrue(result.success());
        assertEquals(1, result.data().size());
        assertEquals("BBC", result.data().get(0).source());
        assertEquals("Company fined", result.data().get(0).headline());
    }

    @Test
    void parsesEmptyArray() {
        stubChatCompletionWithRetry("[]");

        DataSourceResult<List<AdverseMediaFinding>> result = dataSource.fetch("123", "TEST", "GB");

        assertTrue(result.success());
        assertTrue(result.data().isEmpty());
    }

    @Test
    void stripsMarkdownCodeFences() {
        String json = """
                ```json
                [{"source":"BBC","headline":"Fined","date":null,"summary":"Details"}]
                ```
                """;
        stubChatCompletionWithRetry(json);

        DataSourceResult<List<AdverseMediaFinding>> result = dataSource.fetch("123", "TEST", "GB");

        assertTrue(result.success());
        assertEquals(1, result.data().size());
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsFailureWhenBothAttemptsFail() {
        when(llmClient.chatCompletionWithRetry(anyString(), anyString(), any(Function.class)))
                .thenThrow(new RuntimeException("LLM failed"));

        DataSourceResult<List<AdverseMediaFinding>> result = dataSource.fetch("123", "TEST", "GB");

        assertFalse(result.success());
        assertNotNull(result.failureReason());
    }

    @Test
    void parsesMultipleFindings() {
        String json = """
                [
                  {"source":"BBC","headline":"Fraud","date":"2024-01-01","summary":"First"},
                  {"source":"FT","headline":"Collapse","date":null,"summary":"Second"}
                ]
                """;
        stubChatCompletionWithRetry(json);

        DataSourceResult<List<AdverseMediaFinding>> result = dataSource.fetch("123", "TEST", "GB");

        assertTrue(result.success());
        assertEquals(2, result.data().size());
        assertNull(result.data().get(1).date());
    }

    @Test
    void handlesNullDateInFinding() {
        String json = """
                [{"source":"Reuters","headline":"Issue","date":null,"summary":"No date available"}]
                """;
        stubChatCompletionWithRetry(json);

        DataSourceResult<List<AdverseMediaFinding>> result = dataSource.fetch("123", "TEST", "GB");

        assertTrue(result.success());
        assertNull(result.data().get(0).date());
    }
}
