-- =============================================================================
-- TUẦN 1 (NGƯỜI 5): SƠ ĐỒ CƠ SỞ DỮ LIỆU BAN ĐẦU (POSTGRESQL 16 DDL)
-- Đã đồng bộ 100% với dự án Android DuAnLamChung (SignalingClient.kt, ChannelListScreen.kt, CreateEventScreen.kt)
-- =============================================================================

-- 1. Bảng Sự kiện (Events)
CREATE TABLE IF NOT EXISTS events (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    pin_code        CHAR(6) NOT NULL UNIQUE CHECK (char_length(pin_code) = 6 AND pin_code <> '000000'),
    secret_seed     BYTEA NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL
);

-- 2. Bảng Kênh Bộ đàm trong Sự kiện (Channels)
-- Mapping trực tiếp với UI Android: ch_01..ch_05 (Toàn sự kiện, Sân khấu, Hậu cần – An ninh, Kỹ thuật, Đón tiếp)
CREATE TABLE IF NOT EXISTS channels (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    channel_index       SMALLINT NOT NULL CHECK (channel_index BETWEEN 1 AND 32),
    channel_code        VARCHAR(16) NOT NULL DEFAULT 'ch_01', -- Mã kênh chuỗi khớp với Android ChannelItem.id ("ch_01".."ch_05")
    name                VARCHAR(60) NOT NULL,
    department_code     VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    default_priority    SMALLINT NOT NULL DEFAULT 1 CHECK (default_priority BETWEEN 1 AND 3),
    max_speak_seconds   SMALLINT NOT NULL DEFAULT 60,
    UNIQUE(event_id, channel_index),
    UNIQUE(event_id, channel_code)
);

-- 3. Bảng Phiên Người dùng tham gia Sự kiện (Event Sessions)
-- Lưu ý Mapping cấp bậc:
--   Android user_level (1 = Tổng đạo diễn, 2 = Trưởng ban, 3 = CTV)
--   Server role_priority (3 = Cao nhất/Tổng đạo diễn, 2 = Trưởng ban, 1 = CTV)
--   Công thức quy đổi: role_priority = 4 - user_level
CREATE TABLE IF NOT EXISTS event_sessions (
    user_id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_code           VARCHAR(64) NOT NULL UNIQUE, -- Khớp với user_id dạng chuỗi của SignalingClient.kt (VD: "Minh_Hau")
    event_id            BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    display_name        VARCHAR(60) NOT NULL,
    role_title          VARCHAR(60) NOT NULL DEFAULT 'Cộng tác viên', -- VD: "Trưởng ban Sân khấu", "Trưởng ban Hậu cần"
    department_code     VARCHAR(30) NOT NULL DEFAULT 'VOLUNTEER',
    user_level          SMALLINT NOT NULL DEFAULT 3 CHECK (user_level BETWEEN 1 AND 3),
    role_priority       SMALLINT NOT NULL DEFAULT 1 CHECK (role_priority BETWEEN 1 AND 3),
    assigned_channel_id SMALLINT NOT NULL DEFAULT 2,
    assigned_channel_code VARCHAR(16) NOT NULL DEFAULT 'ch_02',
    hmac_secret         BYTEA NOT NULL,
    udp_endpoint        VARCHAR(64),
    last_heartbeat_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Bảng Nhật ký Bắt tay Báo hiệu WebRTC SDP/ICE (WebRTC Signaling Logs)
-- Hỗ trợ cả chuẩn lowercase của Android ("offer", "answer", "ice_candidate") và chuẩn uppercase ("SDP_OFFER", "SDP_ANSWER", "ICE_CANDIDATE")
CREATE TABLE IF NOT EXISTS webrtc_signaling_logs (
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    channel_id      SMALLINT NOT NULL,
    sender_user_id  VARCHAR(64) NOT NULL,
    target_user_id  VARCHAR(64),
    signal_type     VARCHAR(24) NOT NULL CHECK (
        signal_type IN ('offer', 'answer', 'ice_candidate', 'SDP_OFFER', 'SDP_ANSWER', 'ICE_CANDIDATE')
    ),
    payload_json    JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Bảng Nhật ký Vận hành & Chiếm quyền Mic (PTT Audit Logs)
CREATE TABLE IF NOT EXISTS ptt_audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    channel_id      SMALLINT NOT NULL,
    actor_user_id   VARCHAR(64) NOT NULL,
    action_type     VARCHAR(32) NOT NULL,
    detail_json     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Các chỉ mục (Indexes) tăng tốc truy vấn thời gian thực
CREATE INDEX IF NOT EXISTS idx_events_pin_active ON events(pin_code) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_sessions_event_channel ON event_sessions(event_id, assigned_channel_id);
CREATE INDEX IF NOT EXISTS idx_signaling_event_channel ON webrtc_signaling_logs(event_id, channel_id, created_at DESC);

-- =============================================================================
-- KHỞI TẠO DỮ LIỆU MẪU KHỚP 100% VỚI GIAO DIỆN ANDROID (DuAnLamChung)
-- 1. PIN '240826': Mã sự kiện hiển thị trên OnboardingScreen.kt & CreateEventScreen.kt
-- 2. PIN '889900': Mã sự kiện mặc định trong FakeRepositories.kt & JoinChannelScreen.kt
-- 3. PIN '482910': Mã sự kiện dự phòng kiểm thử
-- (Mã '000000' bị chặn để kích hoạt màn hình JoinErrorScreen.kt trên Android)
-- =============================================================================
INSERT INTO events (name, pin_code, secret_seed, expires_at)
VALUES
    ('Đêm nhạc Chào Tân sinh viên', '240826', decode('0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', 'hex'), NOW() + INTERVAL '24 hours'),
    ('Đêm nhạc Chào Tân sinh viên (QR)', '889900', decode('abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789', 'hex'), NOW() + INTERVAL '24 hours'),
    ('Đêm Nhạc Acoustic Hội Trường Lớn 2026', '482910', decode('fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210', 'hex'), NOW() + INTERVAL '24 hours')
ON CONFLICT (pin_code) DO NOTHING;

-- Khởi tạo 5 kênh mặc định khớp với CreateEventScreen.kt & ChannelListScreen.kt:
-- ch_01: Toàn sự kiện | ch_02: Sân khấu | ch_03: Hậu cần – An ninh | ch_04: Kỹ thuật | ch_05: Đón tiếp
INSERT INTO channels (event_id, channel_index, channel_code, name, department_code, default_priority)
VALUES
    (1, 1, 'ch_01', 'Toàn sự kiện',       'ALL_EVENT',     3),
    (1, 2, 'ch_02', 'Sân khấu',           'STAGE',         2),
    (1, 3, 'ch_03', 'Hậu cần – An ninh',  'LOGISTICS_SEC', 2),
    (1, 4, 'ch_04', 'Kỹ thuật',           'TECHNICAL',     2),
    (1, 5, 'ch_05', 'Đón tiếp',           'RECEPTION',     1)
ON CONFLICT DO NOTHING;
