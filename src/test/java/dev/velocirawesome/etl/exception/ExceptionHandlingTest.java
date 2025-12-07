package dev.velocirawesome.etl.exception;

import dev.velocirawesome.etl.model.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ExceptionHandlingTest {

    @Autowired
    private WebApplicationContext applicationContext;

    private RestTestClient restTestClient;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    void testValidationExceptionOnEmptySourceUrl() {
        // Test that ValidationException is properly handled for empty sourceUrl
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/etl/run")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                {"sourceUrl":""}
                """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(errorResponse).isNotNull()
                .withFailMessage("Error response should not be null");
        assertThat(errorResponse.getError()).isEqualTo("VALIDATION_ERROR")
                .withFailMessage("Error code should be VALIDATION_ERROR");
        assertThat(errorResponse.getMessage()).contains("sourceUrl is required")
                .withFailMessage("Error message should mention sourceUrl requirement");
    }

    @Test
    void testValidationExceptionOnNullSourceUrl() {
        // Test that ValidationException is properly handled for null sourceUrl
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/etl/run")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                {"sourceUrl":null}
                """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(errorResponse.getMessage()).contains("sourceUrl is required");
    }

    @Test
    void testValidationExceptionOnMissingSourceUrl() {
        // Test that ValidationException is properly handled when sourceUrl is missing entirely
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/etl/run")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                {}
                """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(errorResponse.getMessage()).contains("sourceUrl is required");
    }

    @Test
    void testErrorResponseStructure() {
        // Test the proper structure of error responses
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/etl/run")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                {"sourceUrl":""}
                """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getError()).isNotEmpty()
                .withFailMessage("Error code must not be empty");
        assertThat(errorResponse.getMessage()).isNotEmpty()
                .withFailMessage("Error message must not be empty");
    }

    @Test
    void testGlobalExceptionHandlerForValidationError() {
        // Test that the GlobalExceptionHandler properly catches and handles ValidationException
        ErrorResponse errorResponse = restTestClient.post()
                .uri("/etl/run")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                {"sourceUrl":""}
                """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        // Verify the error response is properly formatted
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getError()).isNotBlank();
        assertThat(errorResponse.getMessage()).isNotBlank();
    }

    @Test
    void testMultipleValidationErrors() {
        // Test that all validation scenarios are properly handled
        String[] testCases = {"", null};
        
        for (int i = 0; i < testCases.length; i++) {
            String sourceUrl = testCases[i];
            String body = sourceUrl == null 
                ? "{\"sourceUrl\":null}" 
                : "{\"sourceUrl\":\"" + sourceUrl + "\"}";
            
            ErrorResponse errorResponse = restTestClient.post()
                    .uri("/etl/run")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(ErrorResponse.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(errorResponse).isNotNull()
                    .withFailMessage("Error response should not be null for test case " + i);
            assertThat(errorResponse.getError()).isEqualTo("VALIDATION_ERROR")
                    .withFailMessage("Error should be VALIDATION_ERROR for test case " + i);
        }
    }
}
