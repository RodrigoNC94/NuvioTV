package com.nuvio.tv.ui.screens.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Small boxes, 4K: the held player lets go of its memory for the thumbnails and loads again where it was. */
class SeekThumbnailsLetGoTest {
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val runtime = mockk<PlayerRuntimeController>(relaxed = true)

    @Before fun setUp() {
        mockkStatic("com.nuvio.tv.ui.screens.player.PlayerRuntimeControllerMpvKt")
        every { runtime.isUsingMpvEngine() } returns false
        every { runtime._exoPlayer } returns player
        every { runtime.partyBridge } returns null
        every { player.playWhenReady } returns false
        every { player.playbackState } returns Player.STATE_READY
        every { player.playerError } returns null
    }

    @After fun tearDown() {
        unmockkStatic("com.nuvio.tv.ui.screens.player.PlayerRuntimeControllerMpvKt")
    }

    @Test fun aHeldPlayerStopsAndKeepsItsTitleAndPosition() {
        assertTrue(runtime.letGoForSeekThumbnails())
        verify(exactly = 1) { player.stop() }
        verify(exactly = 0) { player.seekTo(any<Long>()) }
        verify(exactly = 0) { player.clearMediaItems() }
        verify(exactly = 0) { player.release() }
    }

    @Test fun aPlayingOrFailedPlayerIsNotLetGo() {
        every { player.playWhenReady } returns true
        assertFalse(runtime.letGoForSeekThumbnails())
        every { player.playWhenReady } returns false
        every { player.playerError } returns mockk<ExoPlaybackException>(relaxed = true)
        assertFalse(runtime.letGoForSeekThumbnails())
        every { player.playerError } returns null
        every { player.playbackState } returns Player.STATE_IDLE
        assertFalse(runtime.letGoForSeekThumbnails())
        verify(exactly = 0) { player.stop() }
    }

    @Test fun mpvAndPartiesAreLeftAlone() {
        every { runtime.isUsingMpvEngine() } returns true
        assertFalse(runtime.letGoForSeekThumbnails())
        every { runtime.isUsingMpvEngine() } returns false
        every { runtime.partyBridge } returns mockk(relaxed = true) { every { inParty } returns true }
        assertFalse(runtime.letGoForSeekThumbnails())
        verify(exactly = 0) { player.stop() }
    }

    @Test fun theFilmLoadsAgainAndPlaysWhenAsked() {
        every { player.playbackState } returns Player.STATE_IDLE
        runtime.loadAgainAfterSeekThumbnails(play = true)
        verify(exactly = 1) { player.prepare() }
        verify(exactly = 1) { player.play() }
        verify(exactly = 0) { player.seekTo(any<Long>()) }
    }

    @Test fun leftInTheBackgroundItLoadsPaused() {
        every { player.playbackState } returns Player.STATE_IDLE
        runtime.loadAgainAfterSeekThumbnails(play = false)
        verify(exactly = 1) { player.prepare() }
        verify(exactly = 0) { player.play() }
    }

    @Test fun aPlayerLoadedMeanwhileOrFailedIsNotPreparedTwice() {
        runtime.loadAgainAfterSeekThumbnails(play = true)
        every { player.playbackState } returns Player.STATE_IDLE
        every { player.playerError } returns mockk<ExoPlaybackException>(relaxed = true)
        runtime.loadAgainAfterSeekThumbnails(play = false)
        verify(exactly = 0) { player.prepare() }
        verify(exactly = 1) { player.play() }
    }
}
