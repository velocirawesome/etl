package dev.velocirawesome.etl.controller;

import dev.velocirawesome.etl.exception.ResourceNotFoundException;
import dev.velocirawesome.etl.exception.ValidationException;
import dev.velocirawesome.etl.model.dto.EtlRunRequest;
import dev.velocirawesome.etl.model.dto.EtlRunResponse;
import dev.velocirawesome.etl.model.dto.EtlStatusResponse;
import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.service.EtlJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/etl")
public class EtlController {

    private static final Logger logger = LoggerFactory.getLogger(EtlController.class);

    private final EtlJobService etlJobService;

    public EtlController(EtlJobService etlJobService) {
        this.etlJobService = etlJobService;
    }

    @PostMapping("/run")
    public ResponseEntity<EtlRunResponse> runEtlJob(@RequestBody EtlRunRequest request) {
        if (request.getSourceUrl() == null || request.getSourceUrl().isEmpty()) {
            throw new ValidationException("sourceUrl is required");
        }

        // Create job record
        EtlJob job = etlJobService.createJob(request);

        // Start async pipeline
        etlJobService.executePipeline(job, request.getDelayMs());

        // Return 202 Accepted with jobId
        EtlRunResponse response = new EtlRunResponse(
                job.getJobId(),
                job.getStatus(),
                "ETL job started asynchronously"
        );

        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/status")
    public ResponseEntity<EtlStatusResponse> getStatus() {
        EtlJob job = etlJobService.getLatestJob();

        if (job == null) {
            throw new ResourceNotFoundException("No ETL jobs found");
        }

        EtlStatusResponse response = mapToStatusResponse(job);
        return ResponseEntity.ok(response);
    }

    private EtlStatusResponse mapToStatusResponse(EtlJob job) {
        return new EtlStatusResponse(
                job.getJobId(),
                job.getSourceUrl(),
                job.getStatus(),
                job.getStartTime(),
                job.getEndTime(),
                job.getRecordsExtracted() != null ? job.getRecordsExtracted() : 0L,
                job.getRecordsTransformed() != null ? job.getRecordsTransformed() : 0L,
                job.getRecordsLoaded() != null ? job.getRecordsLoaded() : 0L,
                job.getErrorMessage()
        );
    }
}
