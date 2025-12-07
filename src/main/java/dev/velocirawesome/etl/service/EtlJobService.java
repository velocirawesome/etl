package dev.velocirawesome.etl.service;

import dev.velocirawesome.etl.model.dto.EtlRunRequest;
import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.model.entity.JobStatus;
import dev.velocirawesome.etl.repository.EtlJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
	private EtlJobService self;

    public EtlJobService(EtlJobRepository jobRepository,
                         ExtractionService extractionService,
                         TransformationService transformationService,
                         LoadingService loadingService,
                         @Lazy EtlJobService self) {
        this.jobRepository = jobRepository;
        this.extractionService = extractionService;
        this.transformationService = transformationService;
        this.loadingService = loadingService;
        this.self = self;
    }
    
    public EtlJob createJob(EtlRunRequest request) {
    	
        // Create job
        var job = jobRepository.createJob(request.getSourceUrl());
        logger.info("Created job {} with sourceUrl: {}", job.getJobId(), request.getSourceUrl());
        
        return job;
    }

    @Async("taskExecutor")
    public void executePipeline(EtlJob job, Integer delayMs) {
    	
        var jobId = job.getJobId();
        var sourceUrl = job.getSourceUrl();
		try {
			logger.info("Starting ETL pipeline for job {}", jobId);

            // Phase 1: Extraction (outside transaction)
            logger.info("Job {} - Phase 1: Extraction starting", jobId);
            List<JsonNode> extractedData = extractionService.fetchData(sourceUrl);
            jobRepository.updateRecordsExtracted(jobId, (long) extractedData.size());
            logger.info("Job {} - Phase 1: Extraction complete. Extracted {} records", jobId, extractedData.size());

            // Phase 2: Transformation (outside transaction)
            logger.info("Job {} - Phase 2: Transformation starting", jobId);
            List<Country> transformedData = transformationService.transform(extractedData);
            jobRepository.updateRecordsTransformed(jobId, (long) transformedData.size());
            logger.info("Job {} - Phase 2: Transformation complete. Transformed {} records", jobId, transformedData.size());

            // Phase 3: Loading (inside transaction for consistency)
            logger.info("Job {} - Phase 3: Loading starting", jobId);
            self.executeLoadingPhaseTransacted(jobId, transformedData);
            logger.info("Job {} - Phase 3: Loading complete. Loaded {} records", jobId, transformedData.size());

            // Simulate async delay if specified
            if (delayMs != null && delayMs > 0) {
                logger.info("Job {} - Delaying for {} ms", jobId, delayMs);
                Thread.sleep(delayMs);
            }
            
            logger.info("Job {} completed successfully", jobId);

        } catch (Exception e) {
            logger.error("ETL pipeline failed for job {}", jobId, e);
            jobRepository.updateJobStatusWithError(jobId, JobStatus.FAILED, LocalDateTime.now(), e.getMessage());
        }
    }

    /**
     * Loading phase wrapped in a single transaction for atomicity.
     * Ensures that table creation, data inserts, record count update, and status update
     * succeed together or fail together, preventing orphaned tables or partial data.
     *
     * Extraction and transformation happen outside this transaction to avoid holding
     * database connections during network I/O or CPU-intensive processing.
     *
     * @param jobId the job ID
     * @param transformedData the transformed country data to load
     * @throws Exception if loading fails - transaction will rollback
     */
    @Transactional
    protected void executeLoadingPhaseTransacted(Long jobId, List<Country> transformedData) throws Exception {
        // Load countries into job-specific table (CREATE TABLE + INSERT)
        loadingService.load(jobId, transformedData);
        
        // Update records loaded count
        jobRepository.updateRecordsLoaded(jobId, (long) transformedData.size());
        
        // Update job status to SUCCESS
        jobRepository.updateJobStatus(jobId, JobStatus.SUCCESS, null);
    }

	public EtlJob getLatestJob() {
		return jobRepository.getLatestJob();
	}
}
