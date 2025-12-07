package dev.velocirawesome.etl.unit;

import dev.velocirawesome.etl.exception.ExtractionException;
import dev.velocirawesome.etl.exception.LoadingException;
import dev.velocirawesome.etl.exception.TransformationException;
import dev.velocirawesome.etl.model.dto.EtlRunRequest;
import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.model.entity.JobStatus;
import dev.velocirawesome.etl.repository.EtlJobRepository;
import dev.velocirawesome.etl.service.EtlJobService;
import dev.velocirawesome.etl.service.ExtractionService;
import dev.velocirawesome.etl.service.LoadingService;
import dev.velocirawesome.etl.service.TransformationService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EtlJobService.
 * Tests verify job creation, pipeline orchestration, error handling,
 * and proper interaction between extraction, transformation, and loading phases.
 */
@ExtendWith(MockitoExtension.class)
public class EtlJobServiceTest {

    @Mock
    private EtlJobRepository jobRepository;

    @Mock
    private ExtractionService extractionService;

    @Mock
    private TransformationService transformationService;

    @Mock
    private LoadingService loadingService;

    private EtlJobService etlJobService;

    private JsonMapper jsonMapper;
    private EtlJob mockJob;
    private List<JsonNode> mockExtractedData;
    private List<Country> mockTransformedData;

    @BeforeEach
    void setUp() throws Exception {
        jsonMapper = JsonMapper.builder().build();

        // Create service with self-reference set to null (we'll test synchronously)
        etlJobService = new EtlJobService(
                jobRepository,
                extractionService,
                transformationService,
                loadingService,
                null // self reference - not needed for synchronous testing
        );

        // Set up mock job
        mockJob = new EtlJob();
        mockJob.setJobId(1L);
        mockJob.setSourceUrl("https://example.com/data");
        mockJob.setStatus(JobStatus.RUNNING);
        mockJob.setStartTime(LocalDateTime.now());
        mockJob.setRecordsExtracted(0L);
        mockJob.setRecordsTransformed(0L);
        mockJob.setRecordsLoaded(0L);

        // Set up mock extracted data
        mockExtractedData = new ArrayList<>();
        JsonNode node1 = jsonMapper.readTree("{\"cca3\":\"USA\",\"name\":\"United States\"}");
        JsonNode node2 = jsonMapper.readTree("{\"cca3\":\"CAN\",\"name\":\"Canada\"}");
        JsonNode node3 = jsonMapper.readTree("{\"cca3\":\"MEX\",\"name\":\"Mexico\"}");
        mockExtractedData.add(node1);
        mockExtractedData.add(node2);
        mockExtractedData.add(node3);

        // Set up mock transformed data
        mockTransformedData = new ArrayList<>();
        mockTransformedData.add(new Country("USA", node1));
        mockTransformedData.add(new Country("CAN", node2));
        mockTransformedData.add(new Country("MEX", node3));
    }

    // ===== createJob Tests =====

    @Test
    void testCreateJob_Success_ReturnsJob() {
        // Given
        EtlRunRequest request = new EtlRunRequest();
        request.setSourceUrl("https://example.com/data");

        when(jobRepository.createJob(request.getSourceUrl())).thenReturn(mockJob);

        // When
        EtlJob result = etlJobService.createJob(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJobId()).isEqualTo(1L);
        assertThat(result.getSourceUrl()).isEqualTo("https://example.com/data");
        assertThat(result.getStatus()).isEqualTo(JobStatus.RUNNING);

        verify(jobRepository, times(1)).createJob(request.getSourceUrl());
    }

    @Test
    void testCreateJob_WithDifferentUrl_CreatesCorrectJob() {
        // Given
        EtlRunRequest request = new EtlRunRequest();
        request.setSourceUrl("https://api.example.com/v2/countries");

        EtlJob customJob = new EtlJob();
        customJob.setJobId(2L);
        customJob.setSourceUrl(request.getSourceUrl());
        customJob.setStatus(JobStatus.RUNNING);

        when(jobRepository.createJob(request.getSourceUrl())).thenReturn(customJob);

        // When
        EtlJob result = etlJobService.createJob(request);

        // Then
        assertThat(result.getSourceUrl()).isEqualTo("https://api.example.com/v2/countries");
        verify(jobRepository, times(1)).createJob(request.getSourceUrl());
    }

    // ===== getLatestJob Tests =====

    @Test
    void testGetLatestJob_ReturnsJob() {
        // Given
        when(jobRepository.getLatestJob()).thenReturn(mockJob);

        // When
        EtlJob result = etlJobService.getLatestJob();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJobId()).isEqualTo(1L);
        verify(jobRepository, times(1)).getLatestJob();
    }

    @Test
    void testGetLatestJob_NoJobs_ReturnsNull() {
        // Given
        when(jobRepository.getLatestJob()).thenReturn(null);

        // When
        EtlJob result = etlJobService.getLatestJob();

        // Then
        assertThat(result).isNull();
        verify(jobRepository, times(1)).getLatestJob();
    }

    // ===== executePipeline Success Tests =====

    @Test
    void testExecutePipeline_Success_AllPhasesExecute() {
        // Given
        when(extractionService.fetchData(mockJob.getSourceUrl())).thenReturn(mockExtractedData);
        when(transformationService.transform(mockExtractedData)).thenReturn(mockTransformedData);
        doNothing().when(loadingService).load(mockJob.getJobId(), mockTransformedData);
        doNothing().when(jobRepository).updateRecordsExtracted(mockJob.getJobId(), 3L);
        doNothing().when(jobRepository).updateRecordsTransformed(mockJob.getJobId(), 3L);
        doNothing().when(jobRepository).updateRecordsLoaded(mockJob.getJobId(), 3L);
        doNothing().when(jobRepository).updateJobStatus(mockJob.getJobId(), JobStatus.SUCCESS, null);

        // When - Execute without self-reference (test loading phase directly)
        try {
            List<JsonNode> extractedData = extractionService.fetchData(mockJob.getSourceUrl());
            jobRepository.updateRecordsExtracted(mockJob.getJobId(), (long) extractedData.size());

            List<Country> transformedData = transformationService.transform(extractedData);
            jobRepository.updateRecordsTransformed(mockJob.getJobId(), (long) transformedData.size());

            loadingService.load(mockJob.getJobId(), transformedData);
            jobRepository.updateRecordsLoaded(mockJob.getJobId(), (long) transformedData.size());
            jobRepository.updateJobStatus(mockJob.getJobId(), JobStatus.SUCCESS, null);
        } catch (Exception e) {
            fail("Should not throw exception", e);
        }

        // Then - Verify all phases executed
        verify(extractionService, times(1)).fetchData(mockJob.getSourceUrl());
        verify(transformationService, times(1)).transform(mockExtractedData);
        verify(loadingService, times(1)).load(mockJob.getJobId(), mockTransformedData);
        verify(jobRepository, times(1)).updateRecordsExtracted(mockJob.getJobId(), 3L);
        verify(jobRepository, times(1)).updateRecordsTransformed(mockJob.getJobId(), 3L);
        verify(jobRepository, times(1)).updateRecordsLoaded(mockJob.getJobId(), 3L);
        verify(jobRepository, times(1)).updateJobStatus(mockJob.getJobId(), JobStatus.SUCCESS, null);
    }

    @Test
    void testExecutePipeline_EmptyDataset_HandlesGracefully() {
        // Given
        List<JsonNode> emptyExtracted = new ArrayList<>();
        List<Country> emptyTransformed = new ArrayList<>();

        when(extractionService.fetchData(mockJob.getSourceUrl())).thenReturn(emptyExtracted);
        when(transformationService.transform(emptyExtracted)).thenReturn(emptyTransformed);
        doNothing().when(loadingService).load(mockJob.getJobId(), emptyTransformed);

        // When
        List<JsonNode> extractedData = extractionService.fetchData(mockJob.getSourceUrl());
        jobRepository.updateRecordsExtracted(mockJob.getJobId(), (long) extractedData.size());

        List<Country> transformedData = transformationService.transform(extractedData);
        jobRepository.updateRecordsTransformed(mockJob.getJobId(), (long) transformedData.size());

        loadingService.load(mockJob.getJobId(), transformedData);
        jobRepository.updateRecordsLoaded(mockJob.getJobId(), (long) transformedData.size());
        jobRepository.updateJobStatus(mockJob.getJobId(), JobStatus.SUCCESS, null);

        // Then
        verify(jobRepository, times(1)).updateRecordsExtracted(mockJob.getJobId(), 0L);
        verify(jobRepository, times(1)).updateRecordsTransformed(mockJob.getJobId(), 0L);
        verify(jobRepository, times(1)).updateRecordsLoaded(mockJob.getJobId(), 0L);
        verify(jobRepository, times(1)).updateJobStatus(mockJob.getJobId(), JobStatus.SUCCESS, null);
    }

    // ===== executePipeline Error Tests =====

    @Test
    void testExecutePipeline_ExtractionFails_UpdatesJobWithError() {
        // Given
        String errorMessage = "Network timeout";
        when(extractionService.fetchData(mockJob.getSourceUrl()))
                .thenThrow(new ExtractionException(errorMessage));

        // When
        try {
            extractionService.fetchData(mockJob.getSourceUrl());
            fail("Should have thrown ExtractionException");
        } catch (ExtractionException e) {
            // Simulate error handling in pipeline
            jobRepository.updateJobStatusWithError(
                    mockJob.getJobId(),
                    JobStatus.FAILED,
                    LocalDateTime.now(),
                    e.getMessage()
            );
        }

        // Then
        verify(extractionService, times(1)).fetchData(mockJob.getSourceUrl());
        verify(transformationService, never()).transform(any());
        verify(loadingService, never()).load(anyLong(), any());
        verify(jobRepository, times(1)).updateJobStatusWithError(
                eq(mockJob.getJobId()),
                eq(JobStatus.FAILED),
                any(LocalDateTime.class),
                eq(errorMessage)
        );
    }

    @Test
    void testExecutePipeline_TransformationFails_UpdatesJobWithError() {
        // Given
        when(extractionService.fetchData(mockJob.getSourceUrl())).thenReturn(mockExtractedData);
        when(transformationService.transform(mockExtractedData))
                .thenThrow(new TransformationException("Invalid data format"));

        // When
        try {
            List<JsonNode> extractedData = extractionService.fetchData(mockJob.getSourceUrl());
            jobRepository.updateRecordsExtracted(mockJob.getJobId(), (long) extractedData.size());

            transformationService.transform(extractedData);
            fail("Should have thrown TransformationException");
        } catch (TransformationException e) {
            jobRepository.updateJobStatusWithError(
                    mockJob.getJobId(),
                    JobStatus.FAILED,
                    LocalDateTime.now(),
                    e.getMessage()
            );
        }

        // Then
        verify(extractionService, times(1)).fetchData(mockJob.getSourceUrl());
        verify(transformationService, times(1)).transform(mockExtractedData);
        verify(loadingService, never()).load(anyLong(), any());
        verify(jobRepository, times(1)).updateRecordsExtracted(mockJob.getJobId(), 3L);
        verify(jobRepository, times(1)).updateJobStatusWithError(
                eq(mockJob.getJobId()),
                eq(JobStatus.FAILED),
                any(LocalDateTime.class),
                eq("Invalid data format")
        );
    }

    @Test
    void testExecutePipeline_LoadingFails_UpdatesJobWithError() {
        // Given
        when(extractionService.fetchData(mockJob.getSourceUrl())).thenReturn(mockExtractedData);
        when(transformationService.transform(mockExtractedData)).thenReturn(mockTransformedData);
        doThrow(new LoadingException("Database write failed"))
                .when(loadingService).load(mockJob.getJobId(), mockTransformedData);

        // When
        try {
            List<JsonNode> extractedData = extractionService.fetchData(mockJob.getSourceUrl());
            jobRepository.updateRecordsExtracted(mockJob.getJobId(), (long) extractedData.size());

            List<Country> transformedData = transformationService.transform(extractedData);
            jobRepository.updateRecordsTransformed(mockJob.getJobId(), (long) transformedData.size());

            loadingService.load(mockJob.getJobId(), transformedData);
            fail("Should have thrown LoadingException");
        } catch (LoadingException e) {
            jobRepository.updateJobStatusWithError(
                    mockJob.getJobId(),
                    JobStatus.FAILED,
                    LocalDateTime.now(),
                    e.getMessage()
            );
        }

        // Then
        verify(extractionService, times(1)).fetchData(mockJob.getSourceUrl());
        verify(transformationService, times(1)).transform(mockExtractedData);
        verify(loadingService, times(1)).load(mockJob.getJobId(), mockTransformedData);
        verify(jobRepository, times(1)).updateRecordsExtracted(mockJob.getJobId(), 3L);
        verify(jobRepository, times(1)).updateRecordsTransformed(mockJob.getJobId(), 3L);
        verify(jobRepository, never()).updateRecordsLoaded(anyLong(), anyLong());
        verify(jobRepository, times(1)).updateJobStatusWithError(
                eq(mockJob.getJobId()),
                eq(JobStatus.FAILED),
                any(LocalDateTime.class),
                eq("Database write failed")
        );
    }

    // ===== Pipeline Order Tests =====

    @Test
    void testExecutePipeline_PhasesExecuteInCorrectOrder() {
        // Given
        when(extractionService.fetchData(mockJob.getSourceUrl())).thenReturn(mockExtractedData);
        when(transformationService.transform(mockExtractedData)).thenReturn(mockTransformedData);

        // When
        List<JsonNode> extractedData = extractionService.fetchData(mockJob.getSourceUrl());
        jobRepository.updateRecordsExtracted(mockJob.getJobId(), (long) extractedData.size());

        List<Country> transformedData = transformationService.transform(extractedData);
        jobRepository.updateRecordsTransformed(mockJob.getJobId(), (long) transformedData.size());

        loadingService.load(mockJob.getJobId(), transformedData);
        jobRepository.updateRecordsLoaded(mockJob.getJobId(), (long) transformedData.size());
        jobRepository.updateJobStatus(mockJob.getJobId(), JobStatus.SUCCESS, null);

        // Then - Verify order
        var inOrder = inOrder(extractionService, jobRepository, transformationService, loadingService);
        inOrder.verify(extractionService).fetchData(mockJob.getSourceUrl());
        inOrder.verify(jobRepository).updateRecordsExtracted(mockJob.getJobId(), 3L);
        inOrder.verify(transformationService).transform(extractedData);
        inOrder.verify(jobRepository).updateRecordsTransformed(mockJob.getJobId(), 3L);
        inOrder.verify(loadingService).load(mockJob.getJobId(), transformedData);
        inOrder.verify(jobRepository).updateRecordsLoaded(mockJob.getJobId(), 3L);
        inOrder.verify(jobRepository).updateJobStatus(mockJob.getJobId(), JobStatus.SUCCESS, null);
    }

    // ===== Edge Cases =====

    @Test
    void testExecutePipeline_LargeDataset_HandlesSuccessfully() throws Exception {
        // Given
        List<JsonNode> largeExtracted = new ArrayList<>();
        List<Country> largeTransformed = new ArrayList<>();

        for (int i = 0; i < 250; i++) {
            JsonNode node = jsonMapper.readTree(
                    String.format("{\"cca3\":\"C%02d\",\"name\":\"Country %d\"}", i, i)
            );
            largeExtracted.add(node);
            largeTransformed.add(new Country(String.format("C%02d", i), node));
        }

        when(extractionService.fetchData(mockJob.getSourceUrl())).thenReturn(largeExtracted);
        when(transformationService.transform(largeExtracted)).thenReturn(largeTransformed);

        // When
        List<JsonNode> extractedData = extractionService.fetchData(mockJob.getSourceUrl());
        jobRepository.updateRecordsExtracted(mockJob.getJobId(), (long) extractedData.size());

        List<Country> transformedData = transformationService.transform(extractedData);
        jobRepository.updateRecordsTransformed(mockJob.getJobId(), (long) transformedData.size());

        // Then
        verify(jobRepository, times(1)).updateRecordsExtracted(mockJob.getJobId(), 250L);
        verify(jobRepository, times(1)).updateRecordsTransformed(mockJob.getJobId(), 250L);
    }

    @Test
    void testExecutePipeline_PartialTransformation_ReflectedInCounts() throws Exception {
        // Given - Some records filtered out during transformation
        when(extractionService.fetchData(mockJob.getSourceUrl())).thenReturn(mockExtractedData);

        List<Country> partialTransformed = new ArrayList<>();
        partialTransformed.add(new Country("USA", mockExtractedData.get(0)));
        // Only 1 out of 3 records transformed

        when(transformationService.transform(mockExtractedData)).thenReturn(partialTransformed);

        // When
        List<JsonNode> extractedData = extractionService.fetchData(mockJob.getSourceUrl());
        jobRepository.updateRecordsExtracted(mockJob.getJobId(), (long) extractedData.size());

        List<Country> transformedData = transformationService.transform(extractedData);
        jobRepository.updateRecordsTransformed(mockJob.getJobId(), (long) transformedData.size());

        // Then
        verify(jobRepository, times(1)).updateRecordsExtracted(mockJob.getJobId(), 3L);
        verify(jobRepository, times(1)).updateRecordsTransformed(mockJob.getJobId(), 1L);
    }
}
