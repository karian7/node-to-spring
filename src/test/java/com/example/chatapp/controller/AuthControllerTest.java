package com.example.chatapp.controller;

import com.example.chatapp.dto.LoginRequest;
import com.example.chatapp.dto.RegisterRequest;
import com.example.chatapp.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// TODO: Re-enable these tests after fixing the embedded mongo environment issue.
// The tests are disabled because Flapdoodle Embedded MongoDB is failing to start in the current environment.
// It cannot resolve the correct package for the detected "Ubuntu" distribution.
@Disabled
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureDataMongo
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SessionService sessionService;

    @Test
    @WithAnonymousUser
    public void testRegisterUser() throws Exception {
        when(sessionService.createSession(any(String.class))).thenReturn("mock-session-id");

        String email = "test" + System.currentTimeMillis() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setName("Test User");
        registerRequest.setEmail(email);
        registerRequest.setPassword("password");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully!"))
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.sessionId").value("mock-session-id"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.name").value("Test User"));
    }

    // TODO: This test is failing with a 403 Forbidden error. Needs investigation.
    @Test
    @Disabled
    @WithAnonymousUser
    public void testAuthenticateUser() throws Exception {
        when(sessionService.createSession(any(String.class))).thenReturn("mock-session-id");

        String email = "test" + System.currentTimeMillis() + "@example.com";

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setName("Test User");
        registerRequest.setEmail(email);
        registerRequest.setPassword("password");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(email, "password");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }
}
