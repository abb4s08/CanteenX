package edu.canteenx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("CanteenX — Spring Web MVC Integration Tests")
public class WebControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/health returns 200 OK and live database status")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("UP")))
                .andExpect(jsonPath("$.data.database", is("CONNECTED")));
    }

    @Test
    @DisplayName("GET /api/canteens returns campus outlets list")
    void testGetCanteens() throws Exception {
        mockMvc.perform(get("/api/canteens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("POST /api/auth/login authenticates student and issues session bearer token")
    void testAuthLoginSuccess() throws Exception {
        String loginJson = "{\"email\":\"student@canteenx.local\",\"password\":\"student123\"}";
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.token", startsWith("CTX-")))
                .andExpect(jsonPath("$.data.role", is("STUDENT")));
    }

    @Test
    @DisplayName("POST /api/auth/login with bad password returns 403 Forbidden")
    void testAuthLoginBadPassword() throws Exception {
        String badLogin = "{\"email\":\"student@canteenx.local\",\"password\":\"wrongpw\"}";
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLogin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Email or password is incorrect")));
    }

    @Test
    @DisplayName("GET /api/menu returns active menu items")
    void testGetMenu() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }
}
