-- 设计方案3D转换系统 初始建表脚本
-- 作者: design3d team
-- 日期: 2026-08-10

CREATE TABLE sessions (
    id          VARCHAR(36)  PRIMARY KEY,
    title       VARCHAR(200) NOT NULL DEFAULT '新对话',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE messages (
    id          VARCHAR(36)  PRIMARY KEY,
    session_id  VARCHAR(36)  NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    msg_type    VARCHAR(20)  NOT NULL,
    content     TEXT,
    metadata    TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_role     CHECK (role     IN ('user', 'assistant', 'system')),
    CONSTRAINT chk_msg_type CHECK (msg_type IN ('text', 'image', 'model', 'json_input')),
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);

CREATE TABLE model_files (
    id          VARCHAR(36)  PRIMARY KEY,
    message_id  VARCHAR(36)  NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    file_path   VARCHAR(500) NOT NULL,
    file_size   BIGINT       NOT NULL DEFAULT 0,
    format      VARCHAR(10)  NOT NULL DEFAULT 'glb',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
);

CREATE INDEX idx_messages_session   ON messages(session_id);
CREATE INDEX idx_messages_created   ON messages(created_at);
CREATE INDEX idx_model_files_msg    ON model_files(message_id);
