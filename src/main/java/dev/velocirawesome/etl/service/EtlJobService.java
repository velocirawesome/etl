package dev.velocirawesome.etl.service;

import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.model.entity.JobStatus;
import dev.velocirawesome.etl.repository.EtlJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EtlJobService {

    private static final Logger logger = LoggerFactory.getLogger(EtlJobService.class);

    private final EtlJobRepository jobRepository;
    private final ExtractionService extractionService;
    private final TransformationService transformationService;
    private final LoadingService loadingService;

    public EtlJobService(EtlJobRepository jobRepository,
                         ExtractionService extractionService,
                         TransformationService transformationService,
                         LoadingService loadingService) {
        this.jobRepository = jobRepository;
        this.extractionService = extractionService;
        this.transformationService = transformationService;
        this.loadingService = loadingService;
    }

    @Async("taskExecutor")
    public void executePipeline(Long jobId, String sourceUrl) {
        try {
            logger.info("Starting ETL pipeline for job {}", jobId);

            // Phase 1: Extraction
            logger.info("Job {} - Phase 1: Extraction starting", jobId);
            List<JsonNode> extractedData = extractionService.fetchData(sourceUrl);
            jobRepository.updateRecordsExtracted(jobId, (long) extractedData.size());
            logger.info("Job {} - Phase 1: Extraction complete. Extracted {} records", jobId, extractedData.size());

            // Phase 2: Transformation
            logger.info("Job {} - Phase 2: Transformation starting", jobId);
            List<Country> transformedData = transformationService.transform(extractedData);
            jobRepository.updateRecordsTransformed(jobId, (long) transformedData.size());
            logger.info("Job {} - Phase 2: Transformation complete. Transformed {} records", jobId, transformedData.size());

            // Phase 3: Loading
            logger.info("Job {} - Phase 3: Loading starting", jobId);
            loadingService.load(jobId, transformedData);
            jobRepository.updateRecordsLoaded(jobId, (long) transformedData.size());
            logger.info("Job {} - Phase 3: Loading complete. Loaded {} records", jobId, transformedData.size());

            // Update job status to SUCCESS
            jobRepository.updateJobStatus(jobId, JobStatus.SUCCESS, LocalDateTime.now());
            logger.info("Job {} completed successfully", jobId);

        } catch (Exception e) {
            logger.error("ETL pipeline failed for job {}", jobId, e);
            jobRepository.updateJobStatusWithError(jobId, JobStatus.FAILED, LocalDateTime.now(), e.getMessage());
        }
    }
}
