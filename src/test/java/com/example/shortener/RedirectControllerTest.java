package com.example.shortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RedirectService redirectService;

    @Test
    void knownCodeRedirectsWith302AndLocation() throws Exception {
        when(redirectService.resolve("02tdk01")).thenReturn(Optional.of("https://example.com/some/long/path"));

        mvc.perform(get("/02tdk01"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/some/long/path"));
    }

    @Test
    void unknownCodeReturns404WithPlainTextBody() throws Exception {
        when(redirectService.resolve("zzzzzzz")).thenReturn(Optional.empty());

        mvc.perform(get("/zzzzzzz"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Link not found"))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void stringThatIsNotACodeAlsoReaches404() throws Exception {
        // The service decides validity; the controller just maps "not found" to 404.
        when(redirectService.resolve("favicon.ico")).thenReturn(Optional.empty());

        mvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound());
    }
}
