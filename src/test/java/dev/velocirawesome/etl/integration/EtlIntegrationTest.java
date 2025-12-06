package dev.velocirawesome.etl.integration;

import dev.velocirawesome.etl.model.entity.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EtlIntegrationTest {

    @Test
    public void testJobStatusEnum() {
        // Basic integration test to verify JobStatus enum works
        assertEquals(JobStatus.RUNNING, JobStatus.valueOf("RUNNING"));
        assertEquals(JobStatus.SUCCESS, JobStatus.valueOf("SUCCESS"));
        assertEquals(JobStatus.FAILED, JobStatus.valueOf("FAILED"));
    }

    @Test
    public void testApplicationContextLoads() {
        // Test that the application context loads successfully
        assertTrue(true);
    }
}
