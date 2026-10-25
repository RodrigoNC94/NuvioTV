package com.nuvio.tv.ui.screens.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

data class PlayerMediaInfo(
    val videoCodec: String? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val audioCodec: String? = null,
    val audioProfile: String? = null,
    val audioChannels: Int? = null,
    val hdr: List<String> = emptyList(),
    val bitDepth: Int? = null
)

internal fun PlayerRuntimeController.playerMediaInfo(): PlayerMediaInfo =
    if (isUsingMpvEngine()) {
        mpvView?.readPlayerMediaInfo() ?: PlayerMediaInfo()
    } else {
        exoMediaInfo(_exoPlayer?.videoFormat, _exoPlayer?.audioFormat)
    }

/** File name, stream name and description of what is playing, for badges the player cannot read. */
internal fun PlayerRuntimeController.playingReleaseText(): String =
    listOfNotNull(currentFilename, _uiState.value.currentStreamName, currentStreamDescription)
        .joinToString(" ")

internal fun mpvMediaInfo(property: (String) -> String?): PlayerMediaInfo =
    PlayerMediaInfo(
        videoCodec = property("current-tracks/video/codec")?.let(::mpvCodecLabel),
        videoWidth = property("video-params/w")?.toIntOrNull()?.takeIf { it > 0 },
        videoHeight = property("video-params/h")?.toIntOrNull()?.takeIf { it > 0 },
        audioCodec = property("current-tracks/audio/codec")?.let(::mpvCodecLabel),
        audioProfile = property("current-tracks/audio/codec-profile"),
        audioChannels = property("audio-params/channel-count")?.toIntOrNull()?.takeIf { it > 0 },
        hdr = buildList {
            if (!property("current-tracks/video/dolby-vision-profile").isNullOrBlank()) add("DV")
            when (property("video-params/gamma")) {
                "pq" -> add("HDR10")
                "hlg" -> add("HLG")
            }
        },
        bitDepth = property("video-params/pixelformat")?.let(::mpvBitDepth)
    )

private fun exoMediaInfo(video: Format?, audio: Format?): PlayerMediaInfo =
    PlayerMediaInfo(
        videoCodec = video?.let(::exoVideoCodec),
        videoWidth = video?.width?.takeIf { it > 0 },
        videoHeight = video?.height?.takeIf { it > 0 },
        audioCodec = audio?.sampleMimeType?.let(::exoCodecLabel),
        audioChannels = audio?.channelCount?.takeIf { it > 0 },
        hdr = video?.hdrFormats().orEmpty(),
        bitDepth = video?.colorInfo?.lumaBitdepth?.takeIf { it > 0 } ?: video?.codecs?.let(::codecsBitDepth)
    )

/** A Dolby Vision track names its base layer in the codecs string: dvav/dva1 AVC, dav1 AV1, dvhe/dvh1 HEVC. */
private fun exoVideoCodec(video: Format): String? {
    val mime = video.sampleMimeType ?: return null
    if (mime != MimeTypes.VIDEO_DOLBY_VISION) return exoCodecLabel(mime)
    val codecs = video.codecs.orEmpty().lowercase()
    return when {
        codecs.startsWith("dvav") || codecs.startsWith("dva1") -> "AVC"
        codecs.startsWith("dav1") -> "AV1"
        else -> "HEVC"
    }
}

@OptIn(UnstableApi::class)
private fun exoCodecLabel(mime: String): String? =
    CustomDefaultTrackNameProvider.formatNameFromMime(mime) ?: when (mime) {
        MimeTypes.AUDIO_DTS_X -> "DTS:X"
        MimeTypes.VIDEO_VC1 -> "VC-1"
        MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
        MimeTypes.VIDEO_MP4V -> "MPEG-4"
        else -> null
    }

/**
 * Bit depth from the RFC 6381 codecs string, for containers that leave colorInfo empty:
 * HEVC Main 10 (hvc1.2…), AVC High 10 (avc1.6E…), AV1 and VP9 (av01.0.08M.10, vp09.02.10.10).
 * Dolby Vision is always at least 10-bit.
 */
internal fun codecsBitDepth(codecs: String): Int? {
    val parts = codecs.substringBefore(',').trim().lowercase().split('.')
    return when (parts.firstOrNull()) {
        "hvc1", "hev1" -> parts.getOrNull(1)?.trimStart('a', 'b', 'c')?.toIntOrNull()?.let { if (it == 2) 10 else null }
        "avc1", "avc3" -> parts.getOrNull(1)?.take(2)?.toIntOrNull(16)?.let { if (it == 110) 10 else null }
        "av01", "vp09" -> parts.getOrNull(3)?.toIntOrNull()?.takeIf { it > 8 }
        "dvhe", "dvh1", "dvav", "dva1", "dav1" -> 10
        else -> null
    }
}

/** Shared by the badges and the stats HDR row; the transfer falls back to the HEVC SPS. */
internal fun Format.hdrFormats(): List<String> = buildList {
    if (sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION) add("DV")
    when (PlaybackVideoColorTransfer.of(this@hdrFormats)) {
        C.COLOR_TRANSFER_ST2084 -> add("HDR10")
        C.COLOR_TRANSFER_HLG -> add("HLG")
    }
}

private fun mpvBitDepth(pixelFormat: String): Int? = when {
    pixelFormat.contains("12") -> 12
    pixelFormat.contains("10") -> 10
    else -> null
}

private fun mpvCodecLabel(codec: String): String? =
    when (codec.trim().lowercase()) {
        "" -> null
        "h264" -> "AVC"
        "ac3" -> "AC-3"
        "eac3" -> "E-AC-3"
        "truehd" -> "TrueHD"
        "opus" -> "Opus"
        "vorbis" -> "Vorbis"
        else -> codec.trim().uppercase()
    }
