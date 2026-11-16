package com.medichannel.exception;

import com.medichannel.controller.AuthController;
import com.medichannel.dto.auth.LoginRequest;
import com.medichannel.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Invalid or malformed JSON returns 400 Bad Request")
    void testMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json-body"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    @DisplayName("Invalid enum value in request body returns 400 Bad Request")
    void testInvalidEnumReturns400() throws Exception {
        String invalidPayload = """
                {
                  "email": "patient@test.com",
                  "password": "StrongPassword#123",
                  "fullName": "Test Patient",
                  "nic": "199012345678",
                  "dateOfBirth": "1990-01-01",
                  "gender": "NOT_A_VALID_GENDER",
                  "phone": "0771234567",
                  "address": "Colombo"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Validation failure on blank fields returns 400 with field errors")
    void testValidationFailureReturns400WithFields() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("Unsupported HTTP method returns 405 Method Not Allowed")
    void testMethodNotAllowedReturns405() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").value("Method not allowed"));
    }

    @Test
    @DisplayName("Unsupported Media Type returns 415")
    void testUnsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<xml></xml>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value("Unsupported media type"));
    }

    @Test
    @DisplayName("Data integrity violation returns 409 Conflict without leaking SQL details")
    void testDataIntegrityViolationReturns409() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenThrow(
                new DataIntegrityViolationException("duplicate key value violates unique constraint idx_user_email")
        );

        String validBody = """
                {
                  "email": "test@lankacare.lk",
                  "password": "Password123#"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data"));
    }
}
