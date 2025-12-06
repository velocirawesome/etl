package dev.velocirawesome.etl.repository;

import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.model.entity.JobStatus;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.max;

/**
 * Repository for managing ETL Job persistence using jOOQ and H2 database.
 * Provides concurrent-safe job creation and status updates through transaction isolation.
 */
@Repository
public class EtlJobRepository {

    private final DSLContext dsl;

    public EtlJobRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * T018: Creates a new ETL job with RUNNING status.
     * Generates a concurrent-safe job ID using MAX(job_id) + 1 within a transaction.
     * Concurrent safety relies on database transaction isolation, not Java synchronization.
     *
     * @param sourceUrl the source URL for the ETL job
     * @return the newly created EtlJob object
     */
    @Transactional
    public EtlJob createJob(String sourceUrl) {
        // Get max job_id and increment - transaction isolation ensures concurrency safety
        Long maxJobId = dsl.select(
                max(field("job_id", Long.class))
        ).from(table("etl_jobs"))
                .fetchOne(0, Long.class);

        Long newJobId = (maxJobId == null) ? 1L : maxJobId + 1L;
        LocalDateTime now = LocalDateTime.now();

        // Insert new job with RUNNING status and all counts initialized to 0
        dsl.insertInto(table("etl_jobs"))
                .columns(
                        field("job_id"),
                        field("source_url"),
                        field("status"),
                        field("start_time"),
                        field("records_extracted"),
                        field("records_transformed"),
                        field("records_loaded"),
                        field("error_message")
                )
                .values(newJobId, sourceUrl, JobStatus.RUNNING.toString(), now, 0L, 0L, 0L, null)
                .execute();

        // Return the created job
        return new EtlJob(newJobId, sourceUrl, JobStatus.RUNNING, now);
    }

    /**
     * T019: Updates the number of records extracted for a job.
     *
     * @param jobId the job ID
     * @param count the number of records extracted
     */
    @Transactional
    public void updateRecordsExtracted(Long jobId, Long count) {
        dsl.update(table("etl_jobs"))
                .set(field("records_extracted"), count)
                .where(field("job_id").eq(jobId))
                .execute();
    }

    /**
     * T019: Updates the number of records transformed for a job.
     *
     * @param jobId the job ID
     * @param count the number of records transformed
     */
    @Transactional
    public void updateRecordsTransformed(Long jobId, Long count) {
        dsl.update(table("etl_jobs"))
                .set(field("records_transformed"), count)
                .where(field("job_id").eq(jobId))
                .execute();
    }

    /**
     * T019: Updates the number of records loaded for a job.
     *
     * @param jobId the job ID
     * @param count the number of records loaded
     */
    @Transactional
    public void updateRecordsLoaded(Long jobId, Long count) {
        dsl.update(table("etl_jobs"))
                .set(field("records_loaded"), count)
                .where(field("job_id").eq(jobId))
                .execute();
    }

    /**
     * T019: Updates the job status and optionally sets error message.
     * Sets endTime if status is SUCCESS or FAILED.
     *
     * @param jobId the job ID
     * @param status the new job status
     * @param errorMessage optional error message (null if no error)
     */
    @Transactional
    public void updateJobStatus(Long jobId, JobStatus status, String errorMessage) {
        LocalDateTime endTime = null;

        // Set endTime if job is completed (SUCCESS or FAILED)
        if (status == JobStatus.SUCCESS || status == JobStatus.FAILED) {
            endTime = LocalDateTime.now();
        }

        dsl.update(table("etl_jobs"))
                .set(field("status"), status.toString())
                .set(field("end_time"), endTime)
                .set(field("error_message"), errorMessage)
                .where(field("job_id").eq(jobId))
                .execute();
    }

    /**
     * T020: Retrieves the latest ETL job ordered by start_time descending.
     * Returns null if no jobs exist in the database.
     *
     * @return the latest EtlJob or null if none exist
     */
    public EtlJob getLatestJob() {
        var record = dsl.select()
                .from(table("etl_jobs"))
                .orderBy(field("start_time").desc())
                .limit(1)
                .fetchOne();

        if (record == null) {
            return null;
        }

        return mapRecordToEtlJob(record);
    }

    /**
     * Retrieves the latest successful ETL job (status = SUCCESS).
     * Returns null if no successful jobs exist in the database.
     *
     * @return the latest successful EtlJob or null if none exist
     */
    public EtlJob getLatestSuccessfulJob() {
        var record = dsl.select()
                .from(table("etl_jobs"))
                .where(field("status").eq(JobStatus.SUCCESS.toString()))
                .orderBy(field("start_time").desc())
                .limit(1)
                .fetchOne();

        if (record == null) {
            return null;
        }

        return mapRecordToEtlJob(record);
    }

    /**
     * Updates job status with error message.
     * This is a helper method for error handling in service layer.
     *
     * @param jobId the job ID
     * @param status the new job status
     * @param endTime the job end time
     * @param errorMessage the error message
     */
    @Transactional
    public void updateJobStatusWithError(Long jobId, JobStatus status, LocalDateTime endTime, String errorMessage) {
        dsl.update(table("etl_jobs"))
                .set(field("status"), status.toString())
                .set(field("end_time"), endTime)
                .set(field("error_message"), errorMessage)
                .where(field("job_id").eq(jobId))
                .execute();
    }

    /**
     * Maps a jOOQ Record to an EtlJob entity object.
     *
     * @param record the jOOQ Record from database query
     * @return mapped EtlJob object with all fields populated
     */
    private EtlJob mapRecordToEtlJob(org.jooq.Record record) {
        EtlJob job = new EtlJob();
        job.setJobId(record.get("job_id", Long.class));
        job.setSourceUrl(record.get("source_url", String.class));
        job.setStatus(JobStatus.valueOf(record.get("status", String.class)));
        job.setStartTime(record.get("start_time", LocalDateTime.class));
        job.setEndTime(record.get("end_time", LocalDateTime.class));
        job.setRecordsExtracted(record.get("records_extracted", Long.class));
        job.setRecordsTransformed(record.get("records_transformed", Long.class));
        job.setRecordsLoaded(record.get("records_loaded", Long.class));
        job.setErrorMessage(record.get("error_message", String.class));
        return job;
    }
}
