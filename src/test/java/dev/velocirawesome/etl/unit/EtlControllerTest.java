package dev.velocirawesome.etl.unit;

import dev.velocirawesome.etl.controller.EtlController;
import dev.velocirawesome.etl.model.dto.EtlRunRequest;
import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.model.entity.JobStatus;
import dev.velocirawesome.etl.service.EtlJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EtlController.class)
public class EtlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EtlJobService etlJobService;

    private EtlJob mockJob;

    @BeforeEach
    void setUp() {
        mockJob = new EtlJob();
        mockJob.setJobId(1L);
        mockJob.setSourceUrl("https://example.com/data");
        mockJob.setStatus(JobStatus.RUNNING);
        mockJob.setStartTime(LocalDateTime.now());
        mockJob.setRecordsExtracted(0L);
        mockJob.setRecordsTransformed(0L);
        mockJob.setRecordsLoaded(0L);
    }

    // ===== POST /etl/run Tests =====

    @Test
    void testRunEtlJob_Success() throws Exception {
        // Arrange
        when(etlJobService.createJob(any(EtlRunRequest.class))).thenReturn(mockJob);
        doNothing().when(etlJobService).executePipeline(any(EtlJob.class), any());

        // Act & Assert
        mockMvc.perform(post("/etl/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUrl\":\"https://example.com/data\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.message").value("ETL job started asynchronously"));

        // Verify
        verify(etlJobService, times(1)).createJob(any(EtlRunRequest.class));
        verify(etlJobService, times(1)).executePipeline(any(EtlJob.class), any());
    }

    @Test
    void testRunEtlJob_WithDelayMs() throws Exception {
        // Arrange
        when(etlJobService.createJob(any(EtlRunRequest.class))).thenReturn(mockJob);
        doNothing().when(etlJobService).executePipeline(any(EtlJob.class), any());

        // Act & Assert
        mockMvc.perform(post("/etl/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUrl\":\"https://example.com/data\",\"delayMs\":5000}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        // Verify executePipeline was called with delayMs parameter
        verify(etlJobService, times(1)).executePipeline(any(EtlJob.class), eq(5000));
    }

    @Test
    void testRunEtlJob_MissingSourceUrl() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/etl/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("sourceUrl is required"));

        // Verify service was not called
        verify(etlJobService, never()).createJob(any());
        verify(etlJobService, never()).executePipeline(any(), anyInt());
    }

    @Test
    void testRunEtlJob_EmptySourceUrl() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/etl/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUrl\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("sourceUrl is required"));

        // Verify service was not called
        verify(etlJobService, never()).createJob(any());
        verify(etlJobService, never()).executePipeline(any(), anyInt());
    }

    @Test
    void testRunEtlJob_ServiceException() throws Exception {
        // Arrange
        when(etlJobService.createJob(any(EtlRunRequest.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        mockMvc.perform(post("/etl/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUrl\":\"https://example.com/data\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Database connection failed"));
    }

    // ===== GET /etl/status Tests =====

    @Test
    void testGetStatus_Success_RunningJob() throws Exception {
        // Arrange
        when(etlJobService.getLatestJob()).thenReturn(mockJob);

        // Act & Assert
        mockMvc.perform(get("/etl/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.sourceUrl").value("https://example.com/data"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.recordsExtracted").value(0))
                .andExpect(jsonPath("$.recordsTransformed").value(0))
                .andExpect(jsonPath("$.recordsLoaded").value(0))
                .andExpect(jsonPath("$.errorMessage").doesNotExist());

        verify(etlJobService, times(1)).getLatestJob();
    }

    @Test
    void testGetStatus_Success_CompletedJob() throws Exception {
        // Arrange
        mockJob.setStatus(JobStatus.SUCCESS);
        mockJob.setRecordsExtracted(100L);
        mockJob.setRecordsTransformed(100L);
        mockJob.setRecordsLoaded(100L);
        mockJob.setEndTime(LocalDateTime.now());
        when(etlJobService.getLatestJob()).thenReturn(mockJob);

        // Act & Assert
        mockMvc.perform(get("/etl/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.recordsExtracted").value(100))
                .andExpect(jsonPath("$.recordsTransformed").value(100))
                .andExpect(jsonPath("$.recordsLoaded").value(100))
                .andExpect(jsonPath("$.endTime").exists());
    }

    @Test
    void testGetStatus_Success_FailedJob() throws Exception {
        // Arrange
        mockJob.setStatus(JobStatus.FAILED);
        mockJob.setErrorMessage("Connection timeout");
        mockJob.setEndTime(LocalDateTime.now());
        when(etlJobService.getLatestJob()).thenReturn(mockJob);

        // Act & Assert
        mockMvc.perform(get("/etl/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Connection timeout"))
                .andExpect(jsonPath("$.endTime").exists());
    }

    @Test
    void testGetStatus_NoJobsFound() throws Exception {
        // Arrange
        when(etlJobService.getLatestJob()).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/etl/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No ETL jobs found"));
    }

    @Test
    void testGetStatus_ServiceException() throws Exception {
        // Arrange
        when(etlJobService.getLatestJob())
                .thenThrow(new RuntimeException("Database query failed"));

        // Act & Assert
        mockMvc.perform(get("/etl/status"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Database query failed"));
    }

    @Test
    void testGetStatus_NullRecordCounts() throws Exception {
        // Arrange - Test that null counts are converted to 0
        mockJob.setRecordsExtracted(null);
        mockJob.setRecordsTransformed(null);
        mockJob.setRecordsLoaded(null);
        when(etlJobService.getLatestJob()).thenReturn(mockJob);

        // Act & Assert
        mockMvc.perform(get("/etl/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsExtracted").value(0))
                .andExpect(jsonPath("$.recordsTransformed").value(0))
                .andExpect(jsonPath("$.recordsLoaded").value(0));
    }

    // ===== Exception Handler Tests =====

    @Test
    void testExceptionHandler_GenericException() throws Exception {
        // Arrange
        when(etlJobService.getLatestJob())
                .thenThrow(new IllegalStateException("Unexpected error"));

        // Act & Assert
        mockMvc.perform(get("/etl/status"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }
}
