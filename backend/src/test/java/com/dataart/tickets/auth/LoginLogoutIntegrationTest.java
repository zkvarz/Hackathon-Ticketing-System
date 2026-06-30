package com.dataart.tickets.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login/logout/session lifecycle (HTS-011) over real HTTP (random port) so cookie flags and
 * session behavior are exercised faithfully, against a real Postgres via Testcontainers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class LoginLogoutIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        users.deleteAll();
        User verified = new User("verified@example.com", passwordEncoder.encode("password1"));
        verified.setEmailVerified(true);
        users.save(verified);

        User unverified = new User("unverified@example.com", passwordEncoder.encode("password1"));
        users.save(unverified); // email_verified defaults to false
    }

    private ResponseEntity<String> postJson(String path, String body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    // AC-1 + AC-4: verified login sets an HttpOnly session cookie; /me works with it; logout
    // invalidates the session so /me then returns 401.
    @Test
    void loginSetsHttpOnlyCookieMeWorksThenLogoutInvalidates() {
        ResponseEntity<String> login = postJson("/api/auth/login",
                loginBody("verified@example.com", "password1"), new HttpHeaders());

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains("verified@example.com");

        // With CSRF enabled the response also sets an XSRF-TOKEN cookie, so pick JSESSIONID.
        List<String> setCookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).as("Set-Cookie headers").isNotNull();
        String sessionCookie = setCookies.stream()
                .filter(c -> c.startsWith("JSESSIONID="))
                .findFirst()
                .orElseThrow();
        assertThat(sessionCookie).containsIgnoringCase("HttpOnly");
        assertThat(sessionCookie).containsIgnoringCase("SameSite=Lax");

        String cookie = sessionCookie.split(";", 2)[0]; // JSESSIONID=...
        HttpHeaders authed = new HttpHeaders();
        authed.add(HttpHeaders.COOKIE, cookie);

        ResponseEntity<String> me = rest.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(authed), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains("verified@example.com");

        ResponseEntity<String> logout = rest.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(authed), String.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> meAfter = rest.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(authed), String.class);
        assertThat(meAfter.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // AC-2: an unverified account is rejected with 403 EMAIL_NOT_VERIFIED.
    @Test
    void unverifiedUserGets403() {
        ResponseEntity<String> login = postJson("/api/auth/login",
                loginBody("unverified@example.com", "password1"), new HttpHeaders());

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(login.getBody()).contains("EMAIL_NOT_VERIFIED");
    }

    // AC-3: wrong password and unknown email both return the same generic 401 BAD_CREDENTIALS.
    // Asserted via MockMvc: the JDK HTTP client used by TestRestTemplate throws on a 401 to a
    // streamed POST, and these cases don't involve cookies, so the in-process client is cleaner.
    @Test
    void wrongPasswordAndUnknownEmailGiveGeneric401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("verified@example.com", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ghost@example.com", "password1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    // AC: /me without a session is 401 in the standard error model.
    @Test
    void meWithoutSessionIs401() {
        ResponseEntity<String> me = rest.getForEntity("/api/auth/me", String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me.getBody()).contains("UNAUTHENTICATED");
    }
}
