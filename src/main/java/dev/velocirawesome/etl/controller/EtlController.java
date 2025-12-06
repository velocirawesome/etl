package dev.velocirawesome.etl.controller;

import dev.velocirawesome.etl.model.dto.EtlRunRequest;
import dev.velocirawesome.etl.model.dto.EtlRunResponse;
import dev.velocirawesome.etl.model.dto.EtlStatusResponse;
import dev.velocirawesome.etl.model.dto.ErrorResponse;
import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.repository.EtlJobRepository;
import dev.velocirawesome.etl.service.EtlJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/etl")
public class EtlController {

    private static final Logger logger = LoggerFactory.getLogger(EtlController.class);

    private final EtlJobRepository jobRepository;
    private final EtlJobService etlJobService;

    public EtlController(EtlJobRepository jobRepository, EtlJobService etlJobService) {
        this.jobRepository = jobRepository;
        this.etlJobService = etlJobService;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runEtlJob(@RequestBody EtlRunRequest request) {
        try {
            if (request.getSourceUrl() == null || request.getSourceUrl().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("VALIDATION_ERROR", "sourceUrl is required"));
            }

            // Create job
            var job = jobRepository.createJob(request.getSourceUrl());
            logger.info("Created job {} with sourceUrl: {}", job.getJobId(), request.getSourceUrl());

            // Start async pipeline
            etlJobService.executePipeline(job.getJobId(), request.getSourceUrl(), request.getDelayMs());

            // Return 202 Accepted with jobId
            EtlRunResponse response = new EtlRunResponse(
                    job.getJobId(),
                    job.getStatus().toString(),
                    "ETL job started asynchronously"
            );

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            logger.error("Error starting ETL job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        try {
            EtlJob job = jobRepository.getLatestJob();

            if (job == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("NOT_FOUND", "No ETL jobs found"));
            }

            EtlStatusResponse response = mapToStatusResponse(job);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error retrieving job status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        }
    }

    private EtlStatusResponse mapToStatusResponse(EtlJob job) {
        return new EtlStatusResponse(
                job.getJobId(),
                job.getSourceUrl(),
                job.getStatus().toString(),
                job.getStartTime(),
                job.getEndTime(),
                job.getRecordsExtracted() != null ? job.getRecordsExtracted() : 0L,
                job.getRecordsTransformed() != null ? job.getRecordsTransformed() : 0L,
                job.getRecordsLoaded() != null ? job.getRecordsLoaded() : 0L,
                job.getErrorMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        logger.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
    }
}
