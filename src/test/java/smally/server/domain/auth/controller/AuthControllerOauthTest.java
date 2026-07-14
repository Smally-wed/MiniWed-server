package smally.server.domain.auth.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import smally.server.core.jwt.JwtTokenProvider;
import smally.server.domain.auth.dto.TokenResponse;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.service.AuthService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerOauthTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AuthService authService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void oauthLogin_토큰을_반환한다() throws Exception {
        when(authService.oauthLogin(eq(OauthProvider.KAKAO), eq("token123")))
                .thenReturn(TokenResponse.of("access", "refresh"));

        mockMvc.perform(post("/api/auth/v1/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"token123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh"));
    }

    @Test
    void oauthLogin_지원하지_않는_provider면_400() throws Exception {
        mockMvc.perform(post("/api/auth/v1/oauth/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"token123\"}"))
                .andExpect(status().isBadRequest());
    }
}
