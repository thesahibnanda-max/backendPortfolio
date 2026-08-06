package net.sahibnanda.portfolio.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class CorsConfigTest extends AbstractRepositoryIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void anyOriginGetsCorsHeadersOnAnActualRequest() throws Exception {
    mockMvc
        .perform(get("/details/professional")
            .header("Origin", "https://anything.example.com"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin",
            "https://anything.example.com"))
        .andExpect(header().string("Access-Control-Expose-Headers", "*"));
  }

  @Test
  void preflightForAnyMethodAndAnyHeaderFromAnyOriginIsAllowed()
      throws Exception {
    mockMvc
        .perform(options("/chats")
            .header("Origin", "https://anything.example.com")
            .header("Access-Control-Request-Method", "POST").header(
                "Access-Control-Request-Headers", "X-Whatever-Custom-Header"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin",
            "https://anything.example.com"))
        .andExpect(header().exists("Access-Control-Allow-Methods"))
        .andExpect(header().exists("Access-Control-Allow-Headers"));
  }
}
