package com.tunicpay.riskassessment.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunicpay.riskassessment.config.AppConfig;
import com.tunicpay.riskassessment.exception.DataParseException;
import com.tunicpay.riskassessment.exception.UpstreamApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenRouterClientTest {

    private HttpClient httpClient;
    private OpenRouterClient client;

    @BeforeEach
    void setUp() {
        httpClient = Mockito.mock(HttpClient.class, Mockito.RETURNS_DEEP_STUBS);
        var appConfig = new AppConfig();
        appConfig.getOpenrouter().setApiKey("test-key");
        appConfig.getOpenrouter().setModel("test-model");
        appConfig.getOpenrouter().setBaseUrl("https://example.com/api/v1");
        client = new OpenRouterClient(appConfig, httpClient, new ObjectMapper());
    }

    @SuppressWarnings("unchecked")
    private void stubHttpResponse(int statusCode, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private void stubHttpResponses(int statusCode1, String body1, int statusCode2, String body2) throws Exception {
        HttpResponse<String> response1 = mock(HttpResponse.class);
        when(response1.statusCode()).thenReturn(statusCode1);
        when(response1.body()).thenReturn(body1);

        HttpResponse<String> response2 = mock(HttpResponse.class);
        when(response2.statusCode()).thenReturn(statusCode2);
        when(response2.body()).thenReturn(body2);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response1)
                .thenReturn(response2);
    }

    @Test
    void chatCompletionReturnsContentOnSuccess() throws Exception {
        stubHttpResponse(200, """
                {"choices":[{"message":{"content":"Hello world"}}]}
                """);

        String result = client.chatCompletion("system", "user");

        assertEquals("Hello world", result);
    }

    @Test
    void chatCompletionThrowsUpstreamApiExceptionOnNon200() throws Exception {
        stubHttpResponse(429, "Rate limit exceeded");

        UpstreamApiException ex = assertThrows(UpstreamApiException.class,
                () -> client.chatCompletion("system", "user"));

        assertEquals(429, ex.getStatusCode());
        assertEquals("OpenRouter", ex.getServiceName());
    }

    @SuppressWarnings("unchecked")
    @Test
    void chatCompletionThrowsDataParseExceptionOnNetworkError() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        assertThrows(DataParseException.class,
                () -> client.chatCompletion("system", "user"));
    }

    @Test
    void chatCompletionReturnsEmptyStringWhenNoContent() throws Exception {
        stubHttpResponse(200, """
                {"choices":[{"message":{}}]}
                """);

        String result = client.chatCompletion("system", "user");

        assertEquals("", result);
    }

    @Test
    void chatCompletionWithRetryReturnsFirstAttemptOnSuccess() throws Exception {
        stubHttpResponse(200, """
                {"choices":[{"message":{"content":"First try"}}]}
                """);

        String result = client.chatCompletionWithRetry("system", "user", "previous error");

        assertEquals("First try", result);
    }

    @Test
    void chatCompletionWithRetryRetriesOnFirstFailure() throws Exception {
        stubHttpResponses(
                500, "Server error",
                200, """
                {"choices":[{"message":{"content":"Second try"}}]}
                """);

        String result = client.chatCompletionWithRetry("system", "user", "error");

        assertEquals("Second try", result);
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void getModelReturnsConfiguredModel() {
        assertEquals("test-model", client.getModel());
    }
}
