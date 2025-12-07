package dev.velocirawesome.etl.repository;

import dev.velocirawesome.etl.jooq.generated.tables.EtlJobs;
import dev.velocirawesome.etl.model.entity.EtlJob;
import dev.velocirawesome.etl.model.entity.JobStatus;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
                max(EtlJobs.ETL_JOBS.JOB_ID)
        ).from(EtlJobs.ETL_JOBS)
                .fetchOne(0, Long.class);

        Long newJobId = (maxJobId == null) ? 1L : maxJobId + 1L;
        LocalDateTime now = LocalDateTime.now();

        // Insert new job with RUNNING status and all counts initialized to 0
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
            .values(newJobId, sourceUrl, JobStatus.RUNNING, now, 0L, 0L, 0L, null)
                .execute();

        // Return the created job
        return new EtlJob(newJobId, sourceUrl, JobStatus.RUNNING, now);
    }

    /**
     * T019: Updates the number of records extracted for a job.
     * 
     * Uses PROPAGATION.SUPPORTS to allow participation in loading phase transaction
     * if called within it, while still supporting standalone calls for testing.
     *
     * @param jobId the job ID
     * @param count the number of records extracted
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void updateRecordsExtracted(Long jobId, Long count) {
        dsl.update(EtlJobs.ETL_JOBS)
                .set(EtlJobs.ETL_JOBS.RECORDS_EXTRACTED, count)
                .where(EtlJobs.ETL_JOBS.JOB_ID.eq(jobId))
                .execute();
    }

    /**
     * T019: Updates the number of records transformed for a job.
     * 
     * Uses PROPAGATION.SUPPORTS to allow participation in loading phase transaction
     * if called within it, while still supporting standalone calls for testing.
     *
     * @param jobId the job ID
     * @param count the number of records transformed
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void updateRecordsTransformed(Long jobId, Long count) {
        dsl.update(EtlJobs.ETL_JOBS)
                .set(EtlJobs.ETL_JOBS.RECORDS_TRANSFORMED, count)
                .where(EtlJobs.ETL_JOBS.JOB_ID.eq(jobId))
                .execute();
    }

    /**
     * T019: Updates the number of records loaded for a job.
     * 
     * Uses PROPAGATION.SUPPORTS to participate in loading phase transaction,
     * ensuring count update is atomic with table creation and inserts.
     *
     * @param jobId the job ID
     * @param count the number of records loaded
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void updateRecordsLoaded(Long jobId, Long count) {
        dsl.update(EtlJobs.ETL_JOBS)
                .set(EtlJobs.ETL_JOBS.RECORDS_LOADED, count)
                .where(EtlJobs.ETL_JOBS.JOB_ID.eq(jobId))
                .execute();
    }

    /**
     * T019: Updates the job status and optionally sets error message.
     * Sets endTime if status is SUCCESS or FAILED.
     * 
     * Uses PROPAGATION.SUPPORTS to participate in loading phase transaction,
     * ensuring status update is atomic with all loading operations.
     *
     * @param jobId the job ID
     * @param status the new job status
     * @param errorMessage optional error message (null if no error)
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void updateJobStatus(Long jobId, JobStatus status, String errorMessage) {
        LocalDateTime endTime = null;

        // Set endTime if job is completed (SUCCESS or FAILED)
        if (status == JobStatus.SUCCESS || status == JobStatus.FAILED) {
            endTime = LocalDateTime.now();
        }

        dsl.update(EtlJobs.ETL_JOBS)
            .set(EtlJobs.ETL_JOBS.STATUS, status)
            .set(EtlJobs.ETL_JOBS.END_TIME, endTime)
            .set(EtlJobs.ETL_JOBS.ERROR_MESSAGE, errorMessage)
            .where(EtlJobs.ETL_JOBS.JOB_ID.eq(jobId))
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
                .from(EtlJobs.ETL_JOBS)
                .orderBy(EtlJobs.ETL_JOBS.START_TIME.desc())
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
            .from(EtlJobs.ETL_JOBS)
            .where(EtlJobs.ETL_JOBS.STATUS.eq(JobStatus.SUCCESS))
            .orderBy(EtlJobs.ETL_JOBS.START_TIME.desc())
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
     * Note: This is called from async context (executePipeline catch block) with its own
     * transaction context, independent from the main pipeline transaction.
     *
     * @param jobId the job ID
     * @param status the new job status
     * @param endTime the job end time
     * @param errorMessage the error message
     */
    @Transactional
    public void updateJobStatusWithError(Long jobId, JobStatus status, LocalDateTime endTime, String errorMessage) {
        dsl.update(EtlJobs.ETL_JOBS)
            .set(EtlJobs.ETL_JOBS.STATUS, status)
            .set(EtlJobs.ETL_JOBS.END_TIME, endTime)
            .set(EtlJobs.ETL_JOBS.ERROR_MESSAGE, errorMessage)
            .where(EtlJobs.ETL_JOBS.JOB_ID.eq(jobId))
            .execute();
    }

    /**
     * Deletes all ETL jobs from the database.
     * This is primarily useful for testing to ensure a clean state.
     */
    @Transactional
    public void deleteAll() {
        dsl.deleteFrom(EtlJobs.ETL_JOBS).execute();
    }

    /**
     * Maps a jOOQ Record to an EtlJob entity object.
     *
     * @param record the jOOQ Record from database query
     * @return mapped EtlJob object with all fields populated
     */
    private EtlJob mapRecordToEtlJob(org.jooq.Record record) {
        EtlJob job = new EtlJob();
        job.setJobId(record.get(EtlJobs.ETL_JOBS.JOB_ID));
        job.setSourceUrl(record.get(EtlJobs.ETL_JOBS.SOURCE_URL));
        // `record.get(EtlJobs.ETL_JOBS.STATUS)` may be a String (older generated code) or a JobStatus (new codegen with converter)
        Object rawStatus = record.get(EtlJobs.ETL_JOBS.STATUS);
        if (rawStatus instanceof JobStatus) {
            job.setStatus((JobStatus) rawStatus);
        } else if (rawStatus instanceof String) {
            job.setStatus(JobStatus.valueOf((String) rawStatus));
        } else if (rawStatus == null) {
            job.setStatus(null);
        } else {
            job.setStatus(JobStatus.valueOf(rawStatus.toString()));
        }
        job.setStartTime(record.get(EtlJobs.ETL_JOBS.START_TIME));
        job.setEndTime(record.get(EtlJobs.ETL_JOBS.END_TIME));
        job.setRecordsExtracted(record.get(EtlJobs.ETL_JOBS.RECORDS_EXTRACTED));
        job.setRecordsTransformed(record.get(EtlJobs.ETL_JOBS.RECORDS_TRANSFORMED));
        job.setRecordsLoaded(record.get(EtlJobs.ETL_JOBS.RECORDS_LOADED));
        job.setErrorMessage(record.get(EtlJobs.ETL_JOBS.ERROR_MESSAGE));
        return job;
    }
}
