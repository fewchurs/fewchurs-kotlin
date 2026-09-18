# Fewchurs Android SDK

A native, customizable feature-request board for Android apps: list, vote, comment, follow and submit.
It looks like your app by default, and there's a headless client if you want to build your own UI.

| Module | What it is |
|---|---|
| `fewchurs-core` | API client, models, errors, device ID, cache, `BoardState` / `FeatureDetailState`. No Compose. |
| `fewchurs-android` | Compose UI (`FewchursBoard`, `FewchursBoardSheet`, `FewchursActivity`), theming, strings, manifest auto-init. |
| `sample` | Demo app: default, sheet, themed, headless, own submit form, View-based, event log. |
| `tools/mock-server` | Local copy of `/api/v1` with in-memory data. No account needed. |

## Quick start

```kotlin
implementation("com.fewchurs:fewchurs-android:1.0.0")
```

```xml
<!-- Zero code: AndroidManifest.xml -->
<meta-data android:name="com.fewchurs.sdk.API_KEY" android:value="fr_live_xxx" />
```

```kotlin
// Or in code (Application.onCreate):
Fewchurs.configure(this, apiKey = "fr_live_xxx") {
    isSubscriber = { billing.hasActiveSubscription() }
    userEmail = { auth.currentUser?.email }
}

FewchursBoard(onClose = { navController.popBackStack() })            // Compose
FewchursBoardSheet(visible = show, onDismiss = { show = false })      // bottom sheet
Fewchurs.showBoard(context)                                          // Views / anywhere
val feature = Fewchurs.submitFeature(title = "Dark mode")             // your own UI
```

## Customization

```kotlin
Fewchurs.configure(this, apiKey = "…") {
    theme {                                   // level 2: tokens
        colors { primary = Color(0xFFED5F18); statusColors[FeatureStatus.DONE] = Color(0xFF16A34A) }
        cornerRadius = 14.dp
        density = FewchursDensity.Compact
        darkMode = DarkMode.System
    }
    board {                                   // level 3: behavior
        defaultTab = BoardTab.New
        showDeclined = false
        emailField = EmailField.Required
        submitButtonStyle = SubmitButton.BottomBar
    }
    strings { boardTitle = "Ideas & roadmap" }
}

FewchursTheme(colors = …, shapes = …) { FewchursBoard() }           // scoped tokens
FewchursBoard(header = { … }, featureCard = { f, actions -> … }, voteButton = { state -> … })  // level 4: slots
val board = Fewchurs.boardState(sort = Sort.Top, scope = viewModelScope)                        // level 5: headless
```

Any string can also be overridden per locale in your own `strings.xml` (every key starts with `fewchurs_`).

---

## How to test

Everything below runs from this folder. Use Android Studio's bundled JDK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

### 1. Unit tests (no device needed)

```bash
./gradlew test
```

This runs 28 tests against OkHttp MockWebServer: headers, every error code, retry rules, optimistic votes and rollback, rapid-tap collapsing, paging, and the "comments page can be empty" rule. Reports are in `fewchurs-core/build/reports/tests/`.

### 2. Sample app + local mock server (no account needed)

Terminal 1, the fake API on port 4010:

```bash
node tools/mock-server/server.mjs
```

Terminal 2, build and install on a running emulator:

```bash
./gradlew :sample:installDebug
```

Or open the folder in Android Studio and run the `sample` configuration. The emulator reaches your Mac's mock server at `http://10.0.2.2:4010`.

Mock server switches: `LATENCY=1500` (slow network), `HIDE_VOTES=1` (owner hid counts), `PRO=1` (no branding badge).

### 3. Against the real Fewchurs backend

Add to `local.properties` (it isn't committed):

```
fewchurs.apiKey=fr_live_xxx
fewchurs.baseUrl=https://fewchurs.com/api/v1
```

For your local Next.js dev server, use `fewchurs.baseUrl=http://10.0.2.2:3000/api/v1` and run `npm run dev` in `feature-request-web`. Then run `./gradlew :sample:installDebug` again.

### 4. Manual checklist

| Try | Expect |
|---|---|
| Default board | Cards, Top/New tabs, status chips, "Powered by Fewchurs" at the end (not with `PRO=1`) |
| Tap a vote arrow fast several times | Updates instantly; only the final state reaches the server (watch the mock-server log) |
| Open a request, then Follow | Email dialog appears, then "Following" plus a snackbar |
| Post a comment | Appears with a "You" badge; the comment count on the board goes up |
| Suggest a feature, then Done | Lands at the top of **New** with a "You" tag |
| Submit the title `fail` or `limit` | Error snackbar; nothing crashes |
| Stop the mock server, then pull to refresh | Snackbar says "You're offline." A cold start shows the offline screen with a retry button. |
| Change the API key to `fr_bad` | "Couldn't load requests" plus a Logcat error about the key |
| Rotate on any screen, including half-typed forms | Tab, scroll position and drafts are kept |
| Dark mode (`adb shell cmd uimode night yes`) | The board follows the app theme |
| Heavily themed / Headless / View-based demos | Customization levels 2, 4 and 5, and `showBoard` |
| `HIDE_VOTES=1` | Only arrows, no counts |

Logcat filter `Fewchurs` shows every request when `logLevel = LogLevel.Debug`.

### 5. Release/R8 check

```bash
./gradlew :sample:assembleRelease
```

This builds a minified APK (signed with the debug key) so you can confirm the consumer ProGuard rules work.

## Server notes (from the plan, §10)

The SDK works with today's `/api/v1`, and it's written to pick up these server changes automatically:
- `hideVoteCounts` is read from `/config`, but `/v1/features` doesn't zero counts yet (§10.1).
- `isMine` is used when the server sends it. Until then, it falls back to `authorDeviceId`/`deviceId`, which the server should stop exposing (§10.4).
- `GET /v1/features/{id}` (§10.5) is only needed for deep links and restoring the detail screen after process death.
- The Roadmap tab is accepted in config but stays hidden until a status filter exists (§10.2).
