package com.assu.server.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(SwaggerSecurityConfigTest.TestConfig.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "swagger.auth.username=swagger-user",
        "swagger.auth.password=swagger-pass"
})
class SwaggerSecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void swagger_whenNoCredentials_thenUnauthorizedWithBasicChallenge() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"ASSU Swagger\""));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swagger_whenValidCredentials_thenOk() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(httpBasic("swagger-user", "swagger-pass")))
                .andExpect(status().isOk());
    }

    @Test
    void swagger_whenWrongPassword_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(httpBasic("swagger-user", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swagger_whenCredentialsAcceptedOnlyByGlobalManager_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(httpBasic("member@soongsil.ac.kr", "member-pass")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonSwaggerPath_isNotHandledBySwaggerChain() throws Exception {
        mockMvc.perform(get("/other"))
                .andExpect(status().isOk());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SwaggerSecurityConfig.class)
    static class TestConfig {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        AuthenticationManager authenticationManager() {
            return authentication -> new UsernamePasswordAuthenticationToken(
                    authentication.getPrincipal(), null, AuthorityUtils.createAuthorityList("ROLE_STUDENT"));
        }

        @Bean
        SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {

        @GetMapping({"/v3/api-docs", "/swagger-ui/index.html", "/other"})
        String ok() {
            return "ok";
        }
    }
}
