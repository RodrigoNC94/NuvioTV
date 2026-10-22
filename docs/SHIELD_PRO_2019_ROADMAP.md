# Nuvio Shield Pro 2019 — optimization roadmap

**Project:** [RodrigoNC94/NuvioTV](https://github.com/RodrigoNC94/NuvioTV)  
**Starting point:** `dev` at `1e99d4e5d20535f48234492278c503101f0eb1d4`, version `1.1.0-beta.7` (October 21, 2026).  
**Hardware:** NVIDIA Shield TV Pro 2019, Android TV 11, Tegra X1+, 3 GB RAM → **Samsung HW-Q990C HDMI input** → **Samsung QN90A HDMI input**.  
**Primary streams:** AIOStreams installed in Nuvio as **English**, with Cinemeta and TMDB supplying metadata; user's existing 4K/1080p, cached-source, English filters and Elite Badges are user preferences, not source-controlled configuration.

## Ground rules

1. **Nuvio stays Nuvio**. Preserve the brand, UI, catalog, addons, player selection and general Android TV compatibility; this is not Noah.
2. **Measure before tuning**. Keep generic fallback behavior and change Shield-specific policies only when device testing demonstrates a gain.
3. **Protect existing installation and data.** Early test APKs must use a unique package ID and stable signing identity so they install alongside the official Nuvio app, without overwriting its addon settings. Do not claim migration is automatic.
4. Keep the upstream GPLv3 license, history, attribution, and an easy path to merge upstream fixes.
5. No Real-Debrid/AIOStreams private credentials, API secrets, keystores, or sensitive logs in this public repository. Use GitHub Secrets for private build inputs and a securely retained signing key.
6. Deliver incremental, testable PRs, not a monolithic rewrite.

## 0 — Baseline build and release safety (blocking milestone)

- [ ] Verify fork checkout and `./gradlew :app:testFullDebugUnitTest :app:assembleFullDebug` in CI; do not assume the inherited workflow passes on a new fork.
- [ ] Preserve a tagged/base commit and inspect any missing Android SDK, Gradle, NDK, dependency, native library, API credential, or signing requirements.
- [ ] Audit CI: inherited `.github/workflows/pr-full-debug-build.yml` currently signs using a **new temporary keystore every run** and tests only `com.nuvio.tv.updater.*`; standardize the signing identity for updatable side-by-side test APKs and run broader relevant unit tests.
- [ ] Audit `app/build.gradle.kts`: source has `full` / `playstore` flavors and custom debug package ID, but full-release app ID `com.nuvio.tv` could conflict with independently signed upstream sideloaded releases.
- [ ] Audit the full-flavor in-app updater: `GITHUB_OWNER=NuvioMedia`, `GITHUB_REPO=NuvioTV`. Fork test APKs must not follow/prompt upstream APK updates with incompatible signatures. Disable fork auto-updating initially or implement a deliberate, tested fork release channel.
- [ ] Publish an *unmodified baseline* signed ARM64 test APK artifact before changing player code. Ensure it can launch alongside stock Nuvio and preserve settings across two consecutive test updates.
- [ ] Record the exact test build revision and a baseline test matrix.

**Acceptance:** reproducible successful build + APK on Shield; separate install; repeatable upgrade; stock build untouched; no exposed secrets.

## 1 — Lossless audio (highest priority)

**Existing relevant code:** `PlayerRuntimeControllerInitialization.kt`, `PlaybackSpeedAwareAudioSink.kt`, `PlayerAudioFallbackPolicy.kt`, `PlayerRuntimeControllerErrorRecovery.kt`, `AudioOutputRouteDetector.kt`.

- [ ] Instrument selected container/track/codec/channel metadata, current Android route/capabilities, playback speed, bitstream-eligible versus PCM-forced states and the reason for any fallback.
- [ ] Prefer Media3 device/HDMI direct passthrough for compatible TrueHD/Atmos, DTS-HD MA/DTS:X, DD+/Atmos and legacy surround formats with speed 1.0x, gain 0 dB, no downmix, no force-optical flag.
- [ ] Test existing error recovery for AudioTrack init/write failures and HDMI renegotiation; prefer bounded retries and explicit downgrade indicators before permanent session-wide PCM forcing.
- [ ] Verify with Nuvio diagnostics **and Q990C reported format**; an Atmos badge or filename by itself does not prove a lossless TrueHD path.
- [ ] Add tests for audio-routing decisions and fallback progression. Avoid disabling safe audio recovery entirely.

**Acceptance:** TrueHD/Atmos and DTS:X both reach the Q990C in known-supported cases; stable seek/resume; any PCM fallback is detectable.

## 2 — Video correctness and AFR

**Existing code:** `PlayerRuntimeControllerAfrPreflight.kt`, `FrameRateUtils.kt`, `DolbyVisionBaseLayerPolicy.kt`, `HardwareVideoDecoderSelector.kt`.

- [ ] Verify accurate 23.976/24/25/50/59.94 Hz matching, startup switch, restoration after stop, and A/V sync through seek.
- [ ] Measure the current automatic frame-rate preflight (18 s maximum). Optimize repeated/cached probes and eliminate avoidable stalls, but do not sacrifice correct mode switching.
- [ ] Prefer the hardware H.264/HEVC decoder and measure dropped frames; invoke software fallback only for unsupported content.
- [ ] Preserve 4K HDR10 correctly. For Dolby Vision Profile 7, test valid HDR10 base-layer fallback. The QN90A cannot display Dolby Vision, and Shield Pro 2019 does not create HDR10+ output. Do not promise a faithful fallback for DV-only profiles.

**Acceptance:** correct display mode, color and contrast, smooth 23.976 fps, accurate restoration and subtitles.

## 3 — Performance, memory, and responsiveness

**Existing code:** `NuvioExoPlayerPerformanceHelper.kt`, buffering settings, caching and artwork/trailer pathways.

- [ ] Baseline warm/cold launch, stream-search time, time-to-first-frame, RAM, garbage collection, dropped frames, rebuffer count/time and seek latency on the real Shield.
- [ ] Measure native allocator/back-buffer allocations and AIOStreams high-bitrate remuxes under the 3 GB RAM limit; the helper currently allows a 500 MB native target limit for this RAM tier. A smaller buffer may reduce memory pressure but can also harm high-bitrate playback—benchmark rather than blindly change it.
- [ ] Investigate background image/trailer loads and player re-instantiation only where profiling reveals contention.
- [ ] Gate any Shield-specific tuning by trustworthy device detection or an explicit user option, preserving normal Nuvio behavior elsewhere.

**Acceptance:** no increased rebuffering/failed playback and demonstrated improvement in at least one measured KPI.

## 4 — AIOStreams compatibility and Elite Badges

**Existing code:** `StreamRepositoryImpl.kt`, `MetaDetailsViewModel.kt`, `StreamBadgePresentation.kt`.

- [ ] Preserve the known working Cinemeta → metadata → AIOStreams search route; add tests for `tmdb:` and canonical IMDb stream IDs. Retry converted IDs only where a valid conversion is available and the original lookup genuinely lacked usable results; avoid duplicating streams or masking provider errors.
- [ ] Understand addon manifest refresh vs account addon-list sync, and provide controlled refresh/retry without erasing catalog ordering.
- [ ] Add **badge scope by stable addon URL/ID** so imported Elite Badges apply only to the selected AIOStreams install; do not identify it solely by the user-renamable label `English`.
- [ ] Preserve existing filename/description details and badge sizing and settings; verify output on the Shield.

**Acceptance:** metadata source choice no longer causes avoidable empty result pages; only selected addon streams receive badges.

## 5 — Tests, shipping and upstream maintenance

- [ ] Add unit tests for codec/passthrough/fallback logic, frame-rate policy, memory boundaries, addon ID resolution and addon-scoped badges.
- [ ] Device-test matrix: HDR10+TrueHD/Atmos; HDR10+DTS-HD MA/DTS:X; DD+ Atmos; HDR10 23.976fps; supported DV7 HDR10 base layer; 1080p; pause/seek/subtitle; long (60–120 minute) high-bitrate playback and HDMI input switching.
- [ ] Keep one signed ARM64 build artifact per PR/milestone with build SHA, change summary, known limitations and rollback instructions. Don't publish a release before a verified device test.
- [ ] Integrate upstream changes selectively in small PRs, with playback regression checks.

### Release gates
**Gate A** baseline debug build launches and installs side-by-side.  
**Gate B** bitstream diagnostics and correct Q990C output proven.  
**Gate C** AFR/HDR regression matrix passes.  
**Gate D** measured performance changes do not increase failure/rebuffer rates.  
**Gate E** addon/badge compatibility verified and signed build is upgradeable.

### First implementation PR (after this planning PR)
`build: isolate and stabilize Shield test builds` — package/signing/update source fixes + CI baseline. **Do not change any playback behavior in the first PR.**
