package com.tunicpay.riskassessment.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunicpay.riskassessment.llm.OpenRouterClient;
import com.tunicpay.riskassessment.model.AdverseMediaFinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AdverseMediaDataSourceTest {

    private OpenRouterClient llmClient;
    private AdverseMediaDataSource dataSource;

    @BeforeEach
    void setUp() {
        llmClient = mock(OpenRouterClient.class);
        dataSource = new AdverseMediaDataSource(llmClient, new ObjectMapper());
    }

    @Test
    void parsesValidJsonArray() {
        String json = """
                [{"source":"BBC","headline":"Company fined","date":"2024-01-01","summary":"Details"}]
                """;
        when(llmClient.chatCompletion(anyString(), anyString())).thenReturn(json);

        DataSourceResult<List<AdverseMediaFinding>> result = dataSource.fetch("123", "TEST", "GB");

        assertTrue(result.success());
        assertEquals(1, result.data().size());
        assertEquals("BBC", result.data().get(0).source());
        assertEquals("Company fined", result.data().get(0).headline());
    }

    @Test
    void parsesEmptyArray() {
        when(llmClient.chatCompletion(anyString(), anyString())).thenReturn("[]");

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
        when(llmClient.chatCompletion(anyString(), anyString())).thenReturn(json);

        DataSourceResult<List<AdverseMediaFinding>> result = dataSource.fetch("123", "TEST", "GB");

        assertTrue(result.success());
        assertEquals(1, result.data().size());
    }

    @Test
    void retriesOnInvalidJsonThenSucceeds() {
        when(llmClient.chatCompletion(anyString(), anyString()))
                .thenReturn("This is not valid JSON");
        when(llmClient.chatCompletionWithRetry(anyString(), anyString(), anyString()))
                .thenReturn("[]");

        DataSourceResult<List<AdverseMediaFinding>> result = dataSource.fetch("123", "TEST", "GB");

        assertTrue(result.success());
        assertTrue(result.data().isEmpty());
        verify(llmClient).chatCompletionWithRetry(anyString(), anyString(), anyString());
    }

    @Test
    void returnsFailureWhenBothAttemptsFail() {
        when(llmClient.chatCompletion(anyString(), anyString()))
                .thenReturn("not json");
        when(llmClient.chatCompletionWithRetry(anyString(), anyString(), anyString()))
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
        when(llmClient.chatCompletion(anyString(), anyString())).thenReturn(json);

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
        when(llmClient.chatCompletion(anyString(), anyString())).thenReturn(json);

        DataSourceResult<List<AdverseMediaFinding>> result = dataSource.fetch("123", "TEST", "GB");

        assertTrue(result.success());
        assertNull(result.data().get(0).date());
    }
}
