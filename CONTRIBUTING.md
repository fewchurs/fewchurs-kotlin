# Contributing / maintaining the SDK

This guide is for people working **on** the SDK. If you're integrating Fewchurs into an app, see [README.md](README.md).

## Project layout

| Path | What it is |
|---|---|
| `fewchurs-core/` | API client (OkHttp + kotlinx.serialization), models, errors, device ID, shared cache (`FewchursRepository`), `BoardState` / `FeatureDetailState`. No Compose. |
| `fewchurs-android/` | Compose UI, theming, config DSL (`theme {}`, `board {}`, `strings {}`), `FewchursActivity`, App Startup initializer, string resources. |
| `sample/` | Demo app covering every integration style. |
| `tools/mock-server/` | Dependency-free Node copy of `/api/v1` with in-memory data. |
| `KOTLIN_SDK_PLAN.md` (in `feature-request-web`) | The design plan this SDK implements. |

Rules of the codebase:
- `explicitApi()` is on. Every public symbol is intentional, and anything shared between modules but not meant for apps gets `@FewchursInternalApi`.
- Nothing may crash the host app. Public calls throw `FewchursException` or render an error state.
- No network or disk work at startup. The initializer only stores configuration.
- Every UI resource name starts with `fewchurs_`, which Gradle enforces with `resourcePrefix`.

## Setup

Use Android Studio's bundled JDK (or any JDK 17+):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Open the folder in Android Studio, or use Gradle from the terminal.

## Tests

```bash
./gradlew test
```

The core module has unit tests that run against OkHttp MockWebServer. They cover request headers, every error code, retry rules, optimistic votes and rollback, rapid-tap collapsing, paging, hidden declined requests, and comment pages that come back empty. Reports are in `fewchurs-core/build/reports/tests/`.

## Running the sample against the mock server (no account needed)

Terminal 1:

```bash
node tools/mock-server/server.mjs
```

Terminal 2, with an emulator running:

```bash
./gradlew :sample:installDebug
```

The emulator reaches your computer's mock server at `http://10.0.2.2:4010`.

| Mock server switch | Effect |
|---|---|
| `LATENCY=1500` | Slow network (default 300 ms) |
| `HIDE_VOTES=1` | The owner has hidden vote counts |
| `PRO=1` | No "Powered by Fewchurs" badge |
| `PORT=5000` | Different port |
| API key `fr_bad` | 401 `invalid_key` |
| Submit title `fail` / `limit` | 500 / 402 `limit_reached` |

## Running the sample against a real backend

In `local.properties`, which isn't committed:

```
fewchurs.apiKey=fr_live_xxx
fewchurs.baseUrl=https://www.fewchurs.com/api/v1
```

For a local Next.js dev server, use `http://10.0.2.2:3000/api/v1`. Reinstall the sample after changing these values.

## Manual test checklist

| Try | Expect |
|---|---|
| Default board | Cards, Top/New tabs, status chips, "Powered by Fewchurs" at the end (not with `PRO=1`) |
| Tap a vote arrow fast several times | Updates instantly; only the final state reaches the server (check the mock-server log) |
| Open a request, then Follow | Email dialog, then "Following" plus a snackbar |
| Post a comment | Appears with a "You" badge; the comment count on the board goes up |
| Suggest a feature, then Done | Lands at the top of **New** with a "You" tag |
| Submit the title `fail` or `limit` | Error snackbar, no crash |
| Stop the mock server, then pull to refresh | "You're offline" snackbar. A cold start shows the offline screen with a retry button. |
| Rotate on any screen, including half-typed forms | Tab, scroll position and drafts are kept |
| Dark mode (`adb shell cmd uimode night yes`) | The board follows the app theme |
| `HIDE_VOTES=1` | Only arrows, no counts |

## Release checks

```bash
./gradlew :sample:assembleRelease
```

This builds a minified APK to confirm the consumer R8 rules still work. Each AAR in `*/build/outputs/aar/` should stay under 300 KB.

## Publishing a release

The group ID is `shop.fewchurs`. The Maven Central namespace is verified through a DNS TXT record on `fewchurs.shop`.

### One-time setup

1. Sign in at [central.sonatype.com](https://central.sonatype.com) and add the namespace `shop.fewchurs`. Add the TXT record it shows to the `fewchurs.shop` DNS, then press **Verify**.
2. Under **Account → Generate User Token**, create a token.
3. Run `./tools/setup-publishing.sh`. It asks for the token, then does steps 4 and 5 for you. To do them by hand instead:
4. Create a signing key and upload its public half:
   ```bash
   gpg --full-generate-key
   gpg --list-secret-keys --keyid-format SHORT
   gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <KEY_ID>
   ```
   Get the secret key as one line for the next step:
   ```bash
   gpg --export-secret-keys --armor <KEY_ID> | grep -v '\-\-' | grep -v '^=.' | tr -d '\n'
   ```
5. Put the credentials in `~/.gradle/gradle.properties`. Never put them in this repo.
   ```properties
   mavenCentralUsername=<token username>
   mavenCentralPassword=<token password>
   signingInMemoryKey=<the one-line secret key>
   signingInMemoryKeyId=<last 8 characters of the key ID>
   signingInMemoryKeyPassword=<key passphrase>
   ```
   On CI, use the env vars `ORG_GRADLE_PROJECT_mavenCentralUsername`, `ORG_GRADLE_PROJECT_signingInMemoryKey` and so on.

### Each release

1. Set `VERSION_NAME` in `gradle.properties`, and update `CHANGELOG.md` and the version in `README.md`.
2. Check that it builds and installs locally:
   ```bash
   ./gradlew test :sample:assembleRelease
   ./gradlew publishToMavenLocal
   ```
   The artifacts land in `~/.m2/repository/shop/fewchurs/`.
3. Upload to Maven Central:
   ```bash
   ./gradlew publishToMavenCentral
   ```
   The deployment waits at [central.sonatype.com/publishing](https://central.sonatype.com/publishing). Check it, then press **Publish**. It takes 10–30 minutes to appear on Maven Central.
4. Tag the release and push the tag. JitPack builds it the first time someone requests it.
   ```bash
   git tag 1.0.0
   git push origin 1.0.0
   ```
   The tag must match `VERSION_NAME`, without a `v` prefix.

## Known server dependencies

The SDK works with today's `/api/v1` and picks up these server changes automatically once they ship:
- `hideVoteCounts` in `/config`, and vote counts zeroed in `/features`.
- `isMine` on features and comments. Until then, the SDK falls back to `authorDeviceId` and `deviceId`.
- `GET /features/{id}`, used for deep links and restoring the detail screen after process death.
- A status filter, which will enable the Roadmap tab.
