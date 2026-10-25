package com.nuvio.tv.ui.screens.player

import android.util.Log
import androidx.media3.common.Tracks
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Settings switch: off hides the button, the timeline gaps and the chapter name; on reads them from the playing file again. */
internal fun PlayerRuntimeController.applyChaptersEnabled(enabled: Boolean) {
    if (enabled == chaptersEnabled) return
    chaptersEnabled = enabled
    refreshChapters()
}

/** The file's own chapters, else the media server's named chapters for the title. */
internal fun PlayerRuntimeController.chaptersFor(tracks: Tracks?): List<PlayerChapter> {
    if (!chaptersEnabled) return emptyList()
    val (source, chapters) = when {
        isUsingMpvEngine() -> "mpv" to (if (mpvChaptersStreamUrl == currentStreamUrl) mpvChapters else emptyList())
        tracks != null -> PlayerChapters.fileChapters(tracks) ?: ("server" to serverChapters)
        else -> "server" to serverChapters
    }
    if (chapters.isNotEmpty()) {
        val key = "$currentStreamUrl|$source|${chapters.size}"
        if (key != chaptersLogKey) {
            chaptersLogKey = key
            Log.i(PlayerRuntimeController.TAG, "CHAPTERS: source=$source count=${chapters.size}")
        }
    }
    return chapters
}

/** mpv lists the chapters itself; they come with the file, so once read for a stream they are kept. */
internal fun PlayerRuntimeController.refreshMpvChapters(view: NuvioMpvSurfaceView) {
    if (!chaptersEnabled) return
    if (mpvChaptersStreamUrl == currentStreamUrl && mpvChapters.isNotEmpty()) {
        if (_uiState.value.chapters.isEmpty()) refreshChapters()
        return
    }
    val chapters = PlayerChapters.fromMpv(view.readChapters())
    if (chapters.isEmpty()) return
    mpvChapters = chapters
    mpvChaptersStreamUrl = currentStreamUrl
    refreshChapters()
}

internal fun PlayerRuntimeController.refreshChapters() {
    val chapters = chaptersFor(_exoPlayer?.currentTracks)
    _uiState.update { it.copy(chapters = chapters, showChaptersPanel = it.showChaptersPanel && chapters.isNotEmpty()) }
}

internal fun PlayerRuntimeController.fetchServerChapters() {
    if (!chaptersEnabled) return
    val url = reportedServerUrl ?: return
    val session = serverPlayback.session(url) ?: return
    val key = "${session.target.item.encode()}:${session.mediaSourceId}"
    if (serverChaptersFetchedKey == key) return
    serverChaptersFetchedKey = key
    scope.launch {
        val chapters = PlayerChapters.fromServer(serverPlayback.chapters(url))
        if (serverChaptersFetchedKey != key) return@launch
        serverChapters = chapters
        if (chapters.isNotEmpty()) refreshChapters()
    }
}

internal fun PlayerRuntimeController.clearServerChapters() {
    serverChaptersFetchedKey = null
    if (serverChapters.isEmpty()) return
    serverChapters = emptyList()
    refreshChapters()
}

internal fun PlayerRuntimeController.showChaptersPanel() {
    if (_uiState.value.chapters.isEmpty()) return
    _uiState.update {
        it.copy(
            showChaptersPanel = true,
            showControls = true,
            showEpisodesPanel = false,
            showSourcesPanel = false,
            showAudioOverlay = false,
            showSubtitleOverlay = false,
            showSubtitleStylePanel = false,
            showSubtitleTimingDialog = false,
            showSpeedDialog = false,
            showMoreDialog = false
        )
    }
}

internal fun PlayerRuntimeController.dismissChaptersPanel() {
    _uiState.update { it.copy(showChaptersPanel = false) }
    scheduleHideControls()
}

internal fun PlayerRuntimeController.selectChapter(startMs: Long) {
    _uiState.update { it.copy(showChaptersPanel = false) }
    onEvent(PlayerEvent.OnSeekTo(startMs, exact = true))
}

/** Next or previous chapter from where playback is; a short pill names the chapter. */
internal fun PlayerRuntimeController.jumpChapter(forward: Boolean) {
    val chapters = _uiState.value.chapters
    if (chapters.isEmpty() || _playbackTimeline.value.isLive) return
    val position = _playbackTimeline.value.playbackPosition
    val target = if (forward) PlayerChapters.next(chapters, position) else PlayerChapters.previous(chapters, position)
    if (target == null) return
    onEvent(PlayerEvent.OnSeekTo(target.startMs, exact = true))
    _uiState.update { it.copy(showAspectRatioIndicator = true, aspectRatioIndicatorText = chapterLabel(context, target)) }
    hideAspectRatioIndicatorJob?.cancel()
    hideAspectRatioIndicatorJob = scope.launch {
        delay(2000)
        _uiState.update { it.copy(showAspectRatioIndicator = false) }
    }
}
