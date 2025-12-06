package dev.velocirawesome.etl.controller;

import dev.velocirawesome.etl.model.dto.CountryRecord;
import dev.velocirawesome.etl.model.dto.ErrorResponse;
import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.repository.CountryRepository;
import dev.velocirawesome.etl.repository.EtlJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/country")
public class CountryController {

    private static final Logger logger = LoggerFactory.getLogger(CountryController.class);

    private final EtlJobRepository jobRepository;
    private final CountryRepository countryRepository;

    public CountryController(EtlJobRepository jobRepository, CountryRepository countryRepository) {
        this.jobRepository = jobRepository;
        this.countryRepository = countryRepository;
    }

    @GetMapping
    public ResponseEntity<?> getCountries() {
        try {
            // Get latest successful job
            EtlJob job = jobRepository.getLatestSuccessfulJob();

            if (job == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("NOT_FOUND", "No successful ETL jobs found"));
            }

            // Get countries from job-specific table
            List<Country> countries = countryRepository.getCountriesByJobId(job.getJobId());

            // Map to CountryRecord DTOs
            List<CountryRecord> response = countries.stream()
                    .map(c -> new CountryRecord(c.getCode(), c.getData()))
                    .collect(Collectors.toList());

            logger.info("Retrieved {} countries from job {}", response.size(), job.getJobId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error retrieving countries", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        logger.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
    }
}
