package com.example.shortener.link;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LinkController.class)
class LinkControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LinkService linkService;

    @MockitoBean
    private LinkStatsService linkStatsService;

    @Test
    void validUrlReturns201WithCodeAndShortUrl() throws Exception {
        when(linkService.create("https://example.com/some/long/path"))
                .thenReturn(new CreateLinkResponse("02tdk01", "http://localhost:8080/02tdk01"));

        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/some/long/path\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("02tdk01"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/02tdk01"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ftp://example.com/file", "not a url", "https:no-host", "javascript:alert(1)"})
    void invalidUrlReturns400AndNeverReachesTheService(String url) throws Exception {
        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"" + url + "\"}"))
                .andExpect(status().isBadRequest());

        verify(linkService, never()).create(anyString());
    }

    @Test
    void urlLongerThan2048CharactersReturns400() throws Exception {
        String tooLong = "https://example.com/" + "a".repeat(2048);

        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        verify(linkService, never()).create(anyString());
    }

    @Test
    void missingUrlReturns400() throws Exception {
        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivateReturns204() throws Exception {
        when(linkService.deactivate("02tdk01")).thenReturn(true);

        mvc.perform(delete("/api/links/02tdk01"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivateUnknownCodeReturns404WithMessage() throws Exception {
        when(linkService.deactivate("zzzzzzz")).thenReturn(false);

        mvc.perform(delete("/api/links/zzzzzzz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Code not found"));
    }

    @Test
    void statsReturnsTotalAndDays() throws Exception {
        when(linkStatsService.stats("02tdk01")).thenReturn(Optional.of(new LinkStatsResponse("02tdk01", 5, List.of(
                new LinkStatsResponse.DailyClicks(LocalDate.parse("2026-10-11"), 3),
                new LinkStatsResponse.DailyClicks(LocalDate.parse("2026-10-12"), 2)))));

        mvc.perform(get("/api/links/02tdk01/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("02tdk01"))
                .andExpect(jsonPath("$.totalClicks").value(5))
                .andExpect(jsonPath("$.days[0].day").value("2026-10-11"))
                .andExpect(jsonPath("$.days[0].clicks").value(3))
                .andExpect(jsonPath("$.days[1].day").value("2026-10-12"));
    }

    @Test
    void statsForUnknownOrDeactivatedCodeReturns404() throws Exception {
        when(linkStatsService.stats("zzzzzzz")).thenReturn(Optional.empty());

        mvc.perform(get("/api/links/zzzzzzz/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Code not found"));
    }
}
