package dev.velocirawesome.etl.service;

import dev.velocirawesome.etl.exception.LoadingException;
import dev.velocirawesome.etl.model.entity.Country;
import dev.velocirawesome.etl.repository.CountryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LoadingService {

    private static final Logger logger = LoggerFactory.getLogger(LoadingService.class);
    private final CountryRepository countryRepository;

    public LoadingService(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    /**
     * Loads transformed country data into a job-specific table.
     * 
     * This method uses PROPAGATION.SUPPORTS to participate in the parent loading phase
     * transaction, ensuring that table creation and inserts are atomic.
     * All operations succeed together or fail together - no orphaned tables or partial loads.
     *
     * @param jobId the job ID
     * @param countries the list of transformed Country objects to load
     * @throws LoadingException if loading fails for any reason
     */
    @Transactional(propagation = Propagation.SUPPORTS)
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
