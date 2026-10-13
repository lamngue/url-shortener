package com.example.shortener.redirect;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.shortener.config.SecurityConfig;


@WebMvcTest(RedirectController.class)
@Import(SecurityConfig.class)
public class RedirectControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RedirectService redirectService;

    @Test
    void testServiceFindsURL() throws Exception {
        when(redirectService.redirect("02tdk01")).thenReturn(Optional.of("https://example.com/some/long/path"));
        mvc.perform(get("/02tdk01"))
           .andExpect(status().isFound())
           .andExpect(header().string("Location", "https://example.com/some/long/path"));
    }

    @Test
    void testServiceDoesNotFindURL() throws Exception {
        when(redirectService.redirect("02tdk01")).thenReturn(Optional.empty());
        mvc.perform(get("/02tdk01"))
           .andExpect(status().isNotFound());
    }
}
