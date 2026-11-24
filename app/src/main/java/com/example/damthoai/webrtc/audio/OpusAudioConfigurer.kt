package com.example.damthoai.webrtc.audio

import android.util.Log
import org.webrtc.RtpSender

/**
 * Cấu hình bộ mã hóa nén âm thanh Opus tối ưu cho bộ đàm PTT (TV2 - Tuần 2 & Tuần 6).
 *
 * - Ưu tiên codec Opus (48kHz) lên đầu dòng m=audio trong SDP.
 * - Bật In-band FEC (`useinbandfec=1`) giúp phục hồi tiếng nói khi rớt gói tin.
 * - Bật DTX (`usedtx=1`) ngừng truyền khung im lặng để tiết kiệm băng thông 4G và pin chạy nền 4 giờ.
 * - Giới hạn băng thông thoại (`maxaveragebitrate`) ổn định và khung gói `ptime=20ms` độ trễ thấp.
 */
data class OpusAudioProfile(
    val maxAverageBitrateBps: Int = 24_000,
    val minPtimeMs: Int = 10,
    val ptimeMs: Int = 20,
    val maxPtimeMs: Int = 60,
    val useInbandFec: Boolean = true,
    val useDtx: Boolean = true,
    val stereo: Boolean = false,
    val constantBitrate: Boolean = false,
    val maxPlaybackRateHz: Int = 48_000
)

object OpusAudioConfigurer {

    private const val TAG = "OpusAudioConfigurer"
    private val RTPMAP_OPUS_REGEX = Regex("""^a=rtpmap:(\d+)\s+opus/48000(?:/\d+)?$""", RegexOption.IGNORE_CASE)

    /**
     * Chỉnh sửa SDP Offer/Answer để:
     * 1. Đưa payload type của Opus lên vị trí ưu tiên số 1 trong dòng `m=audio`.
     * 2. Gắn đầy đủ tham số `a=fmtp:<pt>` (FEC, DTX, bitrate, mono, minptime).
     * 3. Gắn `a=ptime` và `a=maxptime` tối ưu cho thoại thời gian thực.
     */
    fun preferOpusInSdp(sdp: String, profile: OpusAudioProfile = OpusAudioProfile()): String {
        if (sdp.isBlank()) return sdp
        val lineSeparator = if (sdp.contains("\r\n")) "\r\n" else "\n"
        val lines = sdp.split(Regex("\r?\n")).toMutableList()

        val audioMediaIndex = lines.indexOfFirst { it.startsWith("m=audio ") }
        if (audioMediaIndex == -1) return sdp

        val nextMediaIndex = ((audioMediaIndex + 1) until lines.size)
            .firstOrNull { lines[it].startsWith("m=") }
            ?: lines.size

        // Tìm payload type của Opus trong phần m=audio
        var opusPayloadType: String? = null
        var opusRtpmapIndex = -1
        for (i in (audioMediaIndex + 1) until nextMediaIndex) {
            val match = RTPMAP_OPUS_REGEX.matchEntire(lines[i].trim())
            if (match != null) {
                opusPayloadType = match.groupValues[1]
                opusRtpmapIndex = i
                break
            }
        }
        val pt = opusPayloadType ?: return sdp

        // 1. Đưa Opus lên đầu danh sách payload của m=audio
        val mLineParts = lines[audioMediaIndex].trim().split(Regex("\\s+"))
        if (mLineParts.size > 3) {
            val header = mLineParts.take(3)
            val payloads = mLineParts.drop(3).filter { it != pt }
            lines[audioMediaIndex] = (header + listOf(pt) + payloads).joinToString(" ")
        }

        // 2. Cập nhật hoặc thêm mới dòng a=fmtp:<pt>
        val desiredParams = linkedMapOf(
            "minptime" to profile.minPtimeMs.toString(),
            "useinbandfec" to if (profile.useInbandFec) "1" else "0",
            "usedtx" to if (profile.useDtx) "1" else "0",
            "maxaveragebitrate" to profile.maxAverageBitrateBps.coerceIn(8_000, 128_000).toString(),
            "stereo" to if (profile.stereo) "1" else "0",
            "sprop-stereo" to if (profile.stereo) "1" else "0",
            "cbr" to if (profile.constantBitrate) "1" else "0",
            "maxplaybackrate" to profile.maxPlaybackRateHz.toString()
        )

        val fmtpPrefix = "a=fmtp:$pt "
        val existingFmtpIndex = ((audioMediaIndex + 1) until nextMediaIndex)
            .firstOrNull { lines[it].startsWith(fmtpPrefix) }

        if (existingFmtpIndex != null) {
            val currentParams = parseFmtpParams(lines[existingFmtpIndex].removePrefix(fmtpPrefix))
            currentParams.putAll(desiredParams)
            lines[existingFmtpIndex] = fmtpPrefix + formatFmtpParams(currentParams)
        } else {
            val insertAt = (opusRtpmapIndex + 1).coerceAtMost(lines.size)
            lines.add(insertAt, fmtpPrefix + formatFmtpParams(desiredParams))
        }

        // 3. Cập nhật a=ptime và a=maxptime trong phần m=audio
        val updatedNextMedia = ((audioMediaIndex + 1) until lines.size)
            .firstOrNull { lines[it].startsWith("m=") }
            ?: lines.size

        upsertAudioAttribute(lines, audioMediaIndex, updatedNextMedia, "a=ptime:", "a=ptime:${profile.ptimeMs}")
        val updatedNextMedia2 = ((audioMediaIndex + 1) until lines.size)
            .firstOrNull { lines[it].startsWith("m=") }
            ?: lines.size
        upsertAudioAttribute(lines, audioMediaIndex, updatedNextMedia2, "a=maxptime:", "a=maxptime:${profile.maxPtimeMs}")

        return lines.joinToString(lineSeparator)
    }

    /**
     * Giới hạn bitrate trên audio RtpSender để tối ưu lưu lượng 4G và độ ổn định Opus.
     */
    fun configureAudioSender(sender: RtpSender?, bitrateBps: Int): Boolean {
        if (sender == null) return false
        return try {
            val params = sender.parameters ?: return false
            val clamped = bitrateBps.coerceIn(8_000, 128_000)
            params.encodings?.forEach { encoding ->
                encoding.maxBitrateBps = clamped
            }
            sender.parameters = params
            Log.d(TAG, "Đã áp dụng Opus maxBitrate=${clamped}bps lên audio RtpSender")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Không thể cấu hình bitrate cho audio RtpSender: ${e.message}")
            false
        }
    }

    private fun parseFmtpParams(raw: String): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        raw.split(';').forEach { token ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return@forEach
            val idx = trimmed.indexOf('=')
            if (idx > 0) {
                result[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim()
            }
        }
        return result
    }

    private fun formatFmtpParams(params: Map<String, String>): String =
        params.entries.joinToString(";") { (k, v) -> "$k=$v" }

    private fun upsertAudioAttribute(
        lines: MutableList<String>,
        mediaStart: Int,
        mediaEnd: Int,
        prefix: String,
        newLine: String
    ) {
        val existing = ((mediaStart + 1) until mediaEnd).firstOrNull { lines[it].startsWith(prefix) }
        if (existing != null) {
            lines[existing] = newLine
        } else {
            lines.add(mediaEnd, newLine)
        }
    }
}
