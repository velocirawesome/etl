-- ETL Jobs table: parent table tracking all ETL pipeline executions
CREATE TABLE IF NOT EXISTS etl_jobs (
    job_id BIGINT PRIMARY KEY,
    source_url VARCHAR(2048) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    records_extracted BIGINT DEFAULT 0,
    records_transformed BIGINT DEFAULT 0,
    records_loaded BIGINT DEFAULT 0,
    error_message VARCHAR(4096)
);

-- Index on status and start_time for efficient querying
CREATE INDEX IF NOT EXISTS idx_etl_jobs_status_start_time ON etl_jobs(status, start_time DESC);

-- Index on start_time for getLatestJob queries
CREATE INDEX IF NOT EXISTS idx_etl_jobs_start_time ON etl_jobs(start_time DESC);
