# Shield Pro test builds

This fork keeps **Nuvio** branding and behavior. Its optional `shieldTestBuild` Gradle flag creates a separately installable, full-featured APK for a **2019 NVIDIA Shield TV Pro**, while preserving the existing official app and all its data.

## Quick build

```bash
./gradlew :app:testFullDebugUnitTest :app:assembleFullDebug -PshieldTestBuild=true
```

- APK: `app/build/outputs/apk/full/debug/app-full-universal-debug.apk`
- Package ID: `com.nuvio.shieldpro.test` (different from the upstream Nuvio package)
- Launcher name: **Nuvio Shield Test**
- Feature set: Nuvio `full` flavor (stream addons, local plugins and sources stay intact).
- In-app GitHub updater: **disabled for this test build** (it must never silently install an upstream-signed package).
- Audio, video, settings and UX: otherwise unchanged in this first milestone.

This test installation does **not** automatically inherit account settings, local AIOStreams config, addon ordering or watch-progress records from an official Nuvio installation; configure/test it independently. Existing Nuvio stays installed.

### GitHub Actions

See [PR Full Debug Build](../.github/workflows/pr-full-debug-build.yml). A PR into `dev` runs the same test/build command. GitHub Actions must be enabled on the fork to execute it.

When successful, the workflow uploads a `shield-pro-full-debug-<SHA>` artifact containing the universal APK. You can sideload the APK on your Shield with any installer supporting local APK files.

The first workflow run is **not guaranteed to succeed**: this fork inherits dependencies, optional services and native binaries from the upstream project. Resolve build errors before shipping any optimized player changes.

### Repeatable APK upgrades — signing key

Android requires **the same package name and signing certificate** for an APK to update an installed APK. The default Android debug signing key is generated per CI runner; it is suitable for one-time builds, but **not stable across separate GitHub Actions runs**.

To support future in-place Shield test APK updates, generate and safely retain a dedicated private Android signing keystore, then configure **four GitHub repository secrets** under `Settings → Secrets and variables → Actions`:

| Secret | Contents |
| --- | --- |
| `SHIELD_TEST_KEYSTORE_BASE64` | Base64 encoding of the complete private JKS/PKCS12 keystore file, as one line |
| `SHIELD_TEST_STORE_PASSWORD` | Keystore password |
| `SHIELD_TEST_KEY_ALIAS` | Existing alias inside the keystore |
| `SHIELD_TEST_KEY_PASSWORD` | Password for that key |

The build workflow decodes the keystore **only to the temporary GitHub runner filesystem**, validates the alias, and passes the secret values to Gradle. The key and passwords must **never** be committed to this public fork or pasted into issues, PRs or chat logs. Keep a secure independent backup. Losing the key means future builds cannot update the existing APK in place.

If the secrets are absent, CI prints a visible warning and produces an ephemeral debug-signed **smoke-test-only** APK. To install a later APK signed with another key, Android may require uninstalling that previous **test** app (and losing its test-only local data). **Do not uninstall official Nuvio.**

### Local signing

The same setup supports local signing by environment variables:

```text
SHIELD_SIGNING_STORE_FILE=/absolute/private/path/shield-test.jks
SHIELD_SIGNING_STORE_PASSWORD=(private)
SHIELD_SIGNING_KEY_ALIAS=(existing alias)
SHIELD_SIGNING_KEY_PASSWORD=(private)
```

These environment variables are read only when `-PshieldTestBuild=true` is passed. Normal upstream build modes are unchanged.

## Release gate

1. CI successfully runs `testFullDebugUnitTest` and `assembleFullDebug`.
2. APK's package ID is verified as `com.nuvio.shieldpro.test`.
3. APK installs and launches beside the original Nuvio on Shield TV Pro (Android 11).
4. No accidental upstream upgrade is offered.
5. A **second signed build using the same private key** upgrades the test app and preserves its test-local data.

Only after these checks should we modify the audio engine or memory buffers.

## Activating CI on a new fork

GitHub may initially disable Actions after forking. Enable workflows in the repository's **Actions** tab. Then push a change to this feature branch or synchronize its draft PR into `dev` to trigger **PR Full Debug Build**. Review the workflow run for Gradle test/build logs and its attached test APK artifact.

**Do not merge the draft PR merely to start CI**; keep `dev` unchanged until the APK installs cleanly on the real Shield. If GitHub doesn't create a run, inspect Actions permissions and workflow activation before assuming a build succeeded.
