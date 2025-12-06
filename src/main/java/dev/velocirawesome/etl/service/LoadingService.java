package dev.velocirawesome.etl.service;

import dev.velocirawesome.etl.exception.LoadingException;
import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.repository.CountryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LoadingService {

    private static final Logger logger = LoggerFactory.getLogger(LoadingService.class);
    private final CountryRepository countryRepository;

    public LoadingService(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    @Transactional
    public void load(Long jobId, List<Country> countries) {
        try {
            logger.info("Starting loading of {} countries for job {}", countries.size(), jobId);

            // Create job-specific table
            countryRepository.createJobTable(jobId);

            // Insert countries
            countryRepository.insertCountries(jobId, countries);

            logger.info("Loading successful. Loaded {} countries for job {}", countries.size(), jobId);

        } catch (Exception e) {
            logger.error("Loading failed for job {}", jobId, e);
            throw new LoadingException("Failed to load countries for job " + jobId, e);
        }
    }
}
