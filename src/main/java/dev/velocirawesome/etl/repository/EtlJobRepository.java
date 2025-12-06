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

@Repository
public class EtlJobRepository {

    private final DSLContext dsl;

    public EtlJobRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public synchronized EtlJob createJob(String sourceUrl) {
        // Get max job_id and increment
        Long maxJobId = dsl.select(
                max(field("job_id", Long.class))
        ).from(table("etl_jobs"))
                .fetchOne(0, Long.class);

        Long newJobId = (maxJobId == null) ? 1L : maxJobId + 1L;

        LocalDateTime now = LocalDateTime.now();

        dsl.insertInto(table("etl_jobs"))
                .columns(
                        field("job_id"),
                        field("source_url"),
                        field("status"),
                        field("start_time"),
                        field("records_extracted"),
                        field("records_transformed"),
                        field("records_loaded")
                )
                .values(newJobId, sourceUrl, JobStatus.RUNNING.toString(), now, 0L, 0L, 0L)
                .execute();

        EtlJob job = new EtlJob(newJobId, sourceUrl, JobStatus.RUNNING, now);
        job.setRecordsExtracted(0L);
        job.setRecordsTransformed(0L);
        job.setRecordsLoaded(0L);
        return job;
    }

    @Transactional
    public void updateRecordsExtracted(Long jobId, Long count) {
        dsl.update(table("etl_jobs"))
                .set(field("records_extracted"), count)
                .where(field("job_id").eq(jobId))
                .execute();
    }

    @Transactional
    public void updateRecordsTransformed(Long jobId, Long count) {
        dsl.update(table("etl_jobs"))
                .set(field("records_transformed"), count)
                .where(field("job_id").eq(jobId))
                .execute();
    }

    @Transactional
    public void updateRecordsLoaded(Long jobId, Long count) {
        dsl.update(table("etl_jobs"))
                .set(field("records_loaded"), count)
                .where(field("job_id").eq(jobId))
                .execute();
    }

    @Transactional
    public void updateJobStatus(Long jobId, JobStatus status, LocalDateTime endTime) {
        dsl.update(table("etl_jobs"))
                .set(field("status"), status.toString())
                .set(field("end_time"), endTime)
                .where(field("job_id").eq(jobId))
                .execute();
    }

    @Transactional
    public void updateJobStatusWithError(Long jobId, JobStatus status, LocalDateTime endTime, String errorMessage) {
        dsl.update(table("etl_jobs"))
                .set(field("status"), status.toString())
                .set(field("end_time"), endTime)
                .set(field("error_message"), errorMessage)
                .where(field("job_id").eq(jobId))
                .execute();
    }

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
