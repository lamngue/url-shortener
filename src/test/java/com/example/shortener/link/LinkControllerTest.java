package com.example.shortener.link;

import com.example.shortener.config.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SecurityConfig is imported so the real rules apply. jwt() puts a test token on the request
// without signing it; signature and claim checks are tested in JwtDecoderTest.
@WebMvcTest(LinkController.class)
@Import(SecurityConfig.class)
class LinkControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LinkService linkService;

    @MockitoBean
    private LinkStatsService linkStatsService;

    @Test
    void deleteByNonOwnerReturns403() throws Exception {
        // the fake service says: this caller is not the owner
        when(linkService.deactivate("abc123", "alice")).thenReturn(new LinkService.NotOwner());

        // send DELETE with a test token for alice, expect 403
        mvc.perform(delete("/api/links/abc123").with(jwt().jwt(builder -> builder.subject("alice"))))
                .andExpect(status().isForbidden());

        verify(linkService).deactivate("abc123", "alice");
    }

    @Test 
    void deleteWithoutAuthenticationReturns401() throws Exception {
        mvc.perform(delete("/api/links/abc123"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(linkService);
    }

    @Test
    void deleteByOwnerReturns204() throws Exception {
        // the fake service says: this caller is the owner
        when(linkService.deactivate("abc123", "alice")).thenReturn(new LinkService.Owner());

        // send DELETE with a test token for alice, expect 204
        mvc.perform(delete("/api/links/abc123").with(jwt().jwt(builder -> builder.subject("alice"))))
                .andExpect(status().isNoContent());

        verify(linkService).deactivate("abc123", "alice");
    }

    @Test 
    void deleteNotFoundReturns404() throws Exception {
        // the fake service says: this code does not exist
        when(linkService.deactivate("abc123", "alice")).thenReturn(new LinkService.NotFound());

        // send DELETE with a test token for alice, expect 404
        mvc.perform(delete("/api/links/abc123").with(jwt().jwt(builder -> builder.subject("alice"))))
                .andExpect(status().isNotFound());

        verify(linkService).deactivate("abc123", "alice");
    }

    @Test 
    void getStatsWithAuthenticatedUser() throws Exception {
        // the fake service says: this caller is the owner, and returns some stats
        List<LinkStatsResponse.DailyClicks> days = List.of(
                new LinkStatsResponse.DailyClicks(LocalDate.parse("2026-10-10"), 5),
                new LinkStatsResponse.DailyClicks(LocalDate.parse("2026-10-11"), 3));
        when(linkStatsService.stats("abc123", "alice"))
                .thenReturn(new LinkStatsService.Owner(new LinkStatsResponse("abc123", 8, days)));

        // send GET with a test token for alice, expect 200 and the JSON body
        mvc.perform(get("/api/links/abc123/stats").with(jwt().jwt(builder -> builder.subject("alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("abc123"))
                .andExpect(jsonPath("$.totalClicks").value(8))
                .andExpect(jsonPath("$.days[0].day").value("2026-10-10"))
                .andExpect(jsonPath("$.days[0].clicks").value(5))
                .andExpect(jsonPath("$.days[1].day").value("2026-10-11"))
                .andExpect(jsonPath("$.days[1].clicks").value(3));

        verify(linkStatsService).stats("abc123", "alice");
    }

    @Test 
    void getStatsWithUnauthorizedUserReturns403() throws Exception {
        // the fake service says: this caller is not the owner
        when(linkStatsService.stats("abc123", "bob")).thenReturn(new LinkStatsService.NotOwner());

        // send GET with a test token
        mvc.perform(get("/api/links/abc123/stats").with(jwt().jwt(builder -> builder.subject("bob"))))
                .andExpect(status().isForbidden());

        verify(linkStatsService).stats("abc123", "bob");
    }

    @Test
    void getStatsWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/links/abc123/stats"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(linkStatsService);
    }
}
