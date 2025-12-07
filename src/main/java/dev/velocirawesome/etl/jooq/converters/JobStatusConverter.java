package dev.velocirawesome.etl.jooq.converters;

import org.jooq.Converter;
import dev.velocirawesome.etl.model.entity.JobStatus;

/**
 * jOOQ Converter to map between database VARCHAR status and the JobStatus enum.
 */
public class JobStatusConverter implements Converter<String, JobStatus> {

    @Override
    public JobStatus from(String databaseObject) {
        if (databaseObject == null) return null;
        try {
            return JobStatus.valueOf(databaseObject);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String to(JobStatus userObject) {
        return userObject == null ? null : userObject.name();
    }

    @Override
    public Class<String> fromType() {
        return String.class;
    }

    @Override
    public Class<JobStatus> toType() {
        return JobStatus.class;
    }
}
