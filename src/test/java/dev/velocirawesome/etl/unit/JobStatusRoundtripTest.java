package dev.velocirawesome.etl.unit;

import dev.velocirawesome.etl.jooq.generated.tables.EtlJobs;
import dev.velocirawesome.etl.model.entity.JobStatus;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class JobStatusRoundtripTest {

    @Autowired
    private DSLContext dsl;

    @Test
    @Transactional
    public void testInsertAndSelectUsesJobStatusEnum() {
        // Arrange
        long jobId = System.currentTimeMillis() % 100000L; // simple unique id for test
        String sourceUrl = "https://example.com/roundtrip";
        LocalDateTime now = LocalDateTime.now();

        // Act - insert using the enum directly (should trigger jOOQ converter)
        dsl.insertInto(EtlJobs.ETL_JOBS)
                .columns(
                        EtlJobs.ETL_JOBS.JOB_ID,
                        EtlJobs.ETL_JOBS.SOURCE_URL,
                        EtlJobs.ETL_JOBS.STATUS,
                        EtlJobs.ETL_JOBS.START_TIME,
                        EtlJobs.ETL_JOBS.RECORDS_EXTRACTED,
                        EtlJobs.ETL_JOBS.RECORDS_TRANSFORMED,
                        EtlJobs.ETL_JOBS.RECORDS_LOADED,
                        EtlJobs.ETL_JOBS.ERROR_MESSAGE
                )
                .values(jobId, sourceUrl, JobStatus.RUNNING, now, 0L, 0L, 0L, null)
                .execute();

        Record record = dsl.select()
                .from(EtlJobs.ETL_JOBS)
                .where(EtlJobs.ETL_JOBS.JOB_ID.eq(jobId))
                .fetchOne();

        // Assert - jOOQ should return a JobStatus instance (converter applied)
        Object raw = record.get(EtlJobs.ETL_JOBS.STATUS);
        assertThat(raw).isInstanceOf(JobStatus.class);
        assertThat(raw).isEqualTo(JobStatus.RUNNING);
    }
}
