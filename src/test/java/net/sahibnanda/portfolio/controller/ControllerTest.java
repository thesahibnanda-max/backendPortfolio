package net.sahibnanda.portfolio.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ControllerTest extends AbstractRepositoryIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void signUpReturnsCreatedWithAuthTokenHeader() throws Exception {
    mockMvc
        .perform(
            post("/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"mia","password":"Str0ng!Pass"}"""))
        .andExpect(status().isCreated())
        .andExpect(header().exists("X-Auth-Token"));
  }

  @Test
  void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
    mockMvc.perform(
        post("/signup").contentType(MediaType.APPLICATION_JSON).content("""
            {"username":"noah","password":"Str0ng!Pass"}"""));

    mockMvc
        .perform(
            post("/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"noah","password":"Wrong!Pass1"}"""))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createChatWithoutAuthTokenReturnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/chats").contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"My Chat"}"""))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createChatWithWrongJsonKeyCasingReturnsBadRequest() throws Exception {
    String signUpBody = mockMvc
        .perform(
            post("/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"quinn","password":"Str0ng!Pass"}"""))
        .andReturn().getResponse().getHeader("X-Auth-Token");

    // "chat_title" (snake_case) doesn't match the "chatTitle" property, so
    // it's silently dropped rather than deserialized -- this should be
    // caught as a validation error, not reach the database as a null.
    mockMvc
        .perform(post("/chats").header("X-Auth-Token", signUpBody)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"chat_title":"Chat Number 1"}"""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createChatThenListThenGetThenRenameRoundTrips() throws Exception {
    String signUpBody = mockMvc
        .perform(
            post("/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"olivia","password":"Str0ng!Pass"}"""))
        .andReturn().getResponse().getHeader("X-Auth-Token");

    mockMvc
        .perform(post("/chats").header("X-Auth-Token", signUpBody)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"My Chat"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.chats", hasSize(1)));

    String listResponse =
        mockMvc.perform(get("/chats").header("X-Auth-Token", signUpBody))
            .andExpect(status().isOk()).andReturn().getResponse()
            .getContentAsString();
    JsonNode chats = new ObjectMapper().readTree(listResponse).get("chats");
    String chatId = chats.get(0).get("chatId").asText();

    mockMvc.perform(get("/chats/" + chatId).header("X-Auth-Token", signUpBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chat.chatTitle").value("My Chat"));

    mockMvc
        .perform(patch("/chats/" + chatId).header("X-Auth-Token", signUpBody)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"Renamed"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chat.chatTitle").value("Renamed"));
  }

  @Test
  void healthReturnsOk() throws Exception {
    mockMvc.perform(get("/health")).andExpect(status().isOk());
  }
}
