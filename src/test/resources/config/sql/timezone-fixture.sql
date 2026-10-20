-- Fixture for the test-only DateTimeWrapper entity; not part of the application schema.
CREATE TABLE IF NOT EXISTS jhi_date_time_wrapper (
    id BIGINT PRIMARY KEY,
    instant TIMESTAMP(6),
    local_date DATE,
    local_date_time TIMESTAMP(6),
    local_time TIME(6),
    offset_date_time TIMESTAMP(6),
    offset_time TIME(6),
    zoned_date_time TIMESTAMP(6)
);
