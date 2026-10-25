-- =============================================================================
-- TUẦN 1 (NGƯỜI 5): SƠ ĐỒ CƠ SỞ DỮ LIỆU BAN ĐẦU (POSTGRESQL 16 DDL)
-- Đối chiếu BA: 1_BA/06_Thiet_ke_Co_so_du_lieu_ERD.md
-- =============================================================================

-- 1. Bảng Sự kiện (Events)
CREATE TABLE IF NOT EXISTS events (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    pin_code        CHAR(6) NOT NULL UNIQUE CHECK (char_length(pin_code) = 6),
    secret_seed     BYTEA NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL
);

-- 2. Bảng Kênh Bộ đàm trong Sự kiện (Channels)
CREATE TABLE IF NOT EXISTS channels (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    channel_index       SMALLINT NOT NULL CHECK (channel_index BETWEEN 1 AND 32),
    name                VARCHAR(60) NOT NULL,
    department_code     VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    default_priority    SMALLINT NOT NULL DEFAULT 1 CHECK (default_priority BETWEEN 1 AND 3),
    max_speak_seconds   SMALLINT NOT NULL DEFAULT 60,
    UNIQUE(event_id, channel_index)
);

-- 3. Bảng Phiên Người dùng tham gia Sự kiện (Event Sessions)
CREATE TABLE IF NOT EXISTS event_sessions (
    user_id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id            BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    display_name        VARCHAR(60) NOT NULL,
    department_code     VARCHAR(30) NOT NULL DEFAULT 'VOLUNTEER',
    role_priority       SMALLINT NOT NULL DEFAULT 1 CHECK (role_priority BETWEEN 1 AND 3),
    assigned_channel_id SMALLINT NOT NULL DEFAULT 2,
    hmac_secret         BYTEA NOT NULL,
    udp_endpoint        VARCHAR(64),
    last_heartbeat_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Bảng Nhật ký Bắt tay Báo hiệu WebRTC SDP/ICE (WebRTC Signaling Logs)
CREATE TABLE IF NOT EXISTS webrtc_signaling_logs (
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    channel_id      SMALLINT NOT NULL,
    sender_user_id  INT NOT NULL,
    target_user_id  INT,
    signal_type     VARCHAR(24) NOT NULL CHECK (signal_type IN ('SDP_OFFER', 'SDP_ANSWER', 'ICE_CANDIDATE')),
    payload_json    JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Bảng Nhật ký Vận hành & Chiếm quyền Mic (PTT Audit Logs)
CREATE TABLE IF NOT EXISTS ptt_audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    channel_id      SMALLINT NOT NULL,
    actor_user_id   INT NOT NULL,
    action_type     VARCHAR(32) NOT NULL,
    detail_json     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Các chỉ mục (Indexes) tăng tốc truy vấn thời gian thực
CREATE INDEX IF NOT EXISTS idx_events_pin_active ON events(pin_code) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_sessions_event_channel ON event_sessions(event_id, assigned_channel_id);
CREATE INDEX IF NOT EXISTS idx_signaling_event_channel ON webrtc_signaling_logs(event_id, channel_id, created_at DESC);

-- Khởi tạo Dữ liệu Mẫu (5 Kênh Sự kiện Mặc định)
INSERT INTO events (name, pin_code, secret_seed, expires_at)
VALUES ('Đêm Nhạc Acoustic Hội Trường Lớn 2026', '482910', decode('0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', 'hex'), NOW() + INTERVAL '24 hours')
ON CONFLICT (pin_code) DO NOTHING;

INSERT INTO channels (event_id, channel_index, name, department_code, default_priority)
VALUES
    (1, 1, 'CHỈ HUY',              'DIRECTOR',  3),
    (1, 2, 'SÂN KHẤU',             'STAGE',     2),
    (1, 3, 'ÂM THANH & ÁNH SÁNG',  'TECH_AV',   2),
    (1, 4, 'AN NINH - Y TẾ',       'SECURITY',  2),
    (1, 5, 'HẬU CẦN',              'LOGISTICS', 1)
ON CONFLICT DO NOTHING;
