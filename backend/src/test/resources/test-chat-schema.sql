CREATE TABLE IF NOT EXISTS chat_message (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL,
    content    TEXT         NOT NULL,
    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL
);
