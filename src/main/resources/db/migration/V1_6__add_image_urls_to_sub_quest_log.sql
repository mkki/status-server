CREATE TABLE users_sub_quest_log_image_urls (
    log_id    BIGINT        NOT NULL REFERENCES users_sub_quest_log (id) ON DELETE CASCADE,
    image_url VARCHAR(1000) NOT NULL
);
