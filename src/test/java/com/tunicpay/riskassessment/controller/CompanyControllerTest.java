package com.tunicpay.riskassessment.controller;

import com.tunicpay.riskassessment.model.CompanyCandidate;
import com.tunicpay.riskassessment.model.SearchResponse;
import com.tunicpay.riskassessment.service.AssessmentService;
import com.tunicpay.riskassessment.service.CompaniesHouseSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyControllerTest {

    private CompaniesHouseSearchService searchService;
    private AssessmentService assessmentService;
    private CompanyController controller;

    @BeforeEach
    void setUp() {
        searchService = mock(CompaniesHouseSearchService.class);
        assessmentService = mock(AssessmentService.class);
        controller = new CompanyController(searchService, assessmentService);
    }

    // --- Search validation ---

    @Test
    void searchThrowsWhenNoIdentifierProvided() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.search(null, null, "GB"));
    }

    @Test
    void searchThrowsWhenBlankIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.search("  ", "  ", "GB"));
    }

    // --- Search by name ---

    @Test
    void searchByNameReturnsResults() {
        var candidates = List.of(
                new CompanyCandidate("ACME LTD", "123", "active", "2020-01-01", null, false),
                new CompanyCandidate("ACME CORP", "456", "active", "2019-01-01", null, false)
        );
        when(searchService.searchByName("ACME", "GB")).thenReturn(candidates);

        SearchResponse response = controller.search("ACME", null, "GB");

        assertEquals(2, response.candidates().size());
        assertEquals("2 matches found", response.message());
    }

    @Test
    void searchByNameSingleMatch() {
        var candidates = List.of(
                new CompanyCandidate("ACME LTD", "123", "active", "2020-01-01", null, false)
        );
        when(searchService.searchByName("ACME LTD", "GB")).thenReturn(candidates);

        SearchResponse response = controller.search("ACME LTD", null, "GB");

        assertEquals(1, response.candidates().size());
        assertEquals("Single match found", response.message());
    }

    @Test
    void searchByNameNoResults() {
        when(searchService.searchByName("NONEXISTENT", "GB")).thenReturn(List.of());

        SearchResponse response = controller.search("NONEXISTENT", null, "GB");

        assertTrue(response.candidates().isEmpty());
        assertTrue(response.message().contains("No companies found"));
    }

    // --- Search by number ---

    @Test
    void searchByNumberReturnsResult() {
        var candidate = new CompanyCandidate("ACME LTD", "123", "active", "2020-01-01", null, false);
        when(searchService.lookupByNumber("123", "GB")).thenReturn(candidate);

        SearchResponse response = controller.search(null, "123", "GB");

        assertEquals(1, response.candidates().size());
        assertEquals("ACME LTD", response.candidates().getFirst().name());
        assertEquals("Company found", response.message());
    }

    @Test
    void searchByNumberNotFound() {
        when(searchService.lookupByNumber("999", "GB")).thenReturn(null);

        SearchResponse response = controller.search(null, "999", "GB");

        assertTrue(response.candidates().isEmpty());
        assertTrue(response.message().contains("No company found"));
    }

    // --- Name mismatch ---

    @Test
    void searchByNumberWithMatchingNameNoMismatch() {
        var candidate = new CompanyCandidate("ACME LTD", "123", "active", "2020-01-01", null, false);
        when(searchService.lookupByNumber("123", "GB")).thenReturn(candidate);

        SearchResponse response = controller.search("ACME LTD", "123", "GB");

        assertFalse(response.candidates().getFirst().nameMismatch());
        assertEquals("Company found", response.message());
    }

    @Test
    void searchByNumberWithDifferentNameSetsMismatch() {
        var candidate = new CompanyCandidate("ACME LTD", "123", "active", "2020-01-01", null, false);
        when(searchService.lookupByNumber("123", "GB")).thenReturn(candidate);

        SearchResponse response = controller.search("WRONG NAME", "123", "GB");

        assertTrue(response.candidates().getFirst().nameMismatch());
        assertTrue(response.message().contains("name differs"));
    }

    @Test
    void searchByNumberWithNameIsCaseInsensitive() {
        var candidate = new CompanyCandidate("ACME LTD", "123", "active", "2020-01-01", null, false);
        when(searchService.lookupByNumber("123", "GB")).thenReturn(candidate);

        SearchResponse response = controller.search("acme ltd", "123", "GB");

        assertFalse(response.candidates().getFirst().nameMismatch());
    }

    // --- Number takes priority over name ---

    @Test
    void searchWithBothIdentifiersUsesNumberLookup() {
        var candidate = new CompanyCandidate("ACME LTD", "123", "active", "2020-01-01", null, false);
        when(searchService.lookupByNumber("123", "GB")).thenReturn(candidate);

        controller.search("some name", "123", "GB");

        verify(searchService).lookupByNumber("123", "GB");
        verify(searchService, never()).searchByName(anyString(), anyString());
    }
}
