# Fewchurs Android SDK

Add a native feature-request board to your Android app. Your users can browse requests, vote, comment, follow updates and suggest new features, and it looks like your app out of the box.

```kotlin
Fewchurs.configure(this, apiKey = "fr_live_xxx")

FewchursBoard()
```

- **Plug and play**: one dependency, one line of setup, one composable.
- **Looks native**: inherits your `MaterialTheme` colors, shapes, fonts and dark mode.
- **Customizable at every level**: change one color, tweak copy and behavior, replace single pieces, or build your own UI on top of the headless API.
- **Small and safe**: no analytics, no crashes in your app, no network calls at startup, and R8 rules included.

---

## Contents

1. [Requirements](#requirements)
2. [Installation](#installation)
3. [Setup](#setup)
4. [Showing the board](#showing-the-board)
5. [Submitting from your own UI](#submitting-from-your-own-ui)
6. [Identifying users](#identifying-users)
7. [Customization](#customization)
8. [Strings and localization](#strings-and-localization)
9. [Headless API (build your own UI)](#headless-api-build-your-own-ui)
10. [Events and analytics](#events-and-analytics)
11. [Error handling](#error-handling)
12. [Limits and validation](#limits-and-validation)
13. [Privacy and Google Play Data Safety](#privacy-and-google-play-data-safety)
14. [Security](#security)
15. [R8 / ProGuard](#r8--proguard)
16. [Troubleshooting](#troubleshooting)
17. [API reference](#api-reference)

---

## Requirements

| | |
|---|---|
| Android | **minSdk 23** (Android 6.0) or higher |
| Kotlin | 2.0+ |
| UI | Jetpack Compose with Material 3. View-based apps can use `Fewchurs.showBoard(context)` instead. |
| Permissions | `INTERNET`, which is added for you |
| Account | A Fewchurs app and API key from your dashboard at [fewchurs.com](https://www.fewchurs.com) |

---

## Installation

The SDK comes in two artifacts:

| Artifact | Use it when |
|---|---|
| `com.fewchurs:fewchurs-android` | You want the ready-made board (most apps). It includes `fewchurs-core`. |
| `com.fewchurs:fewchurs-core` | You want only the API client and state holders to build a fully custom UI, without Compose. |

The SDK is on Maven Central, which every Android project already has in its repositories. Add one line:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.fewchurs:fewchurs-android:1.0.1")
}
```

Upgrading from 1.0.0? It was published as `shop.fewchurs`. Change the group in your dependency line to `com.fewchurs`; nothing else changes.

Using a version catalog:

```toml
# gradle/libs.versions.toml
[libraries]
fewchurs = { module = "com.fewchurs:fewchurs-android", version = "1.0.1" }
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(libs.fewchurs)
}
```

Kotlin Multiplatform projects: the SDK is Android-only, so add it to `androidMain.dependencies { }`, not `commonMain`. On iOS, use the [Swift package](https://github.com/fewchurs/fewchurs-swift).

<details>
<summary>Installing from JitPack instead</summary>

Every git tag is also built on JitPack. Add the repository:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then use the JitPack coordinates:

```kotlin
implementation("com.github.fewchurs.fewchurs-kotlin:fewchurs-android:1.0.1")
```

Don't mix the two: use either the `com.fewchurs` or the `com.github.fewchurs.fewchurs-kotlin` coordinates in one app, not both.
</details>

<details>
<summary>Building from source</summary>

```bash
git clone https://github.com/fewchurs/fewchurs-kotlin.git
```

```kotlin
// settings.gradle.kts of your app
includeBuild("../fewchurs-kotlin")
```

Keep the same `implementation("com.fewchurs:fewchurs-android:1.0.1")` line. Gradle swaps in the local source automatically.
</details>

---

## Setup

Get your API key from the Fewchurs dashboard (**Apps → API keys**). Keys start with `fr_live_`. Then choose one of the two options below.

### Option A: zero code (manifest)

```xml
<!-- AndroidManifest.xml -->
<application ...>
    <meta-data
        android:name="com.fewchurs.sdk.API_KEY"
        android:value="fr_live_xxx" />
</application>
```

The SDK configures itself at app start through AndroidX App Startup. It only stores the configuration and makes no network calls.

Optional: `com.fewchurs.sdk.BASE_URL` points the SDK at a different server, such as staging.

### Option B: code (recommended when customizing)

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Fewchurs.configure(this, apiKey = "fr_live_xxx") {
            isSubscriber = { billing.hasActiveSubscription() }
            userEmail = { auth.currentUser?.email }
        }
    }
}
```

If you use both options, the code configuration wins.

### Configuration options

| Option | Type | Default | Description |
|---|---|---|---|
| `baseUrl` | `String` | `https://www.fewchurs.com/api/v1` | API server. Change it for staging or a local server. |
| `logLevel` | `LogLevel` | `Error` | `None`, `Error` or `Debug`. `Debug` logs every request to Logcat under the tag `Fewchurs`. The API key is never logged. |
| `isSubscriber` | `() -> Boolean` | `false` | Marks requests from paying users in your dashboard. |
| `userEmail` | `() -> String?` | `null` | Prefills the email when a user follows or submits a request. |
| `deviceId` | `() -> String?` | random per install | Lets votes follow a signed-in user across devices. See [Identifying users](#identifying-users). |
| `theme { }` | | inherits your theme | Colors, shapes, fonts, density, dark mode. See [Customization](#customization). |
| `board { }` | | | Tabs, behavior, submit button style. |
| `strings { }` | | | Copy overrides. |

The callbacks are read when they're needed (for example, when the user submits), so they always return the current value.

---

## Showing the board

### Jetpack Compose

Put the board anywhere: a screen, a tab or a navigation destination.

```kotlin
import com.fewchurs.sdk.ui.FewchursBoard

FewchursBoard(onClose = { navController.popBackStack() })
```

`onClose` is optional. When you set it, a close button appears in the top bar.

The board includes list → detail → submit navigation, and system back works inside it.

### Bottom sheet

```kotlin
import com.fewchurs.sdk.ui.FewchursBoardSheet

var showBoard by remember { mutableStateOf(false) }

Button(onClick = { showBoard = true }) { Text("Feedback") }
FewchursBoardSheet(visible = showBoard, onDismiss = { showBoard = false })
```

### Navigation Compose

```kotlin
NavHost(navController, startDestination = "home") {
    composable("home") { HomeScreen(onFeedback = { navController.navigate("feedback") }) }
    composable("feedback") { FewchursBoard(onClose = { navController.popBackStack() }) }
}
```

### Views, XML, or anywhere with a `Context`

```kotlin
Fewchurs.showBoard(context)
```

This opens the board in its own Activity. The Activity picks up your app theme's `colorPrimary` and follows system dark mode. Java callers use the same call: `Fewchurs.showBoard(context);`.

### Edge-to-edge and insets

By default the board pads itself for the system bars. If it sits inside a layout that already handles insets (for example, a tab of your own `Scaffold`), turn that off:

```kotlin
FewchursBoard(windowInsets = WindowInsets(0))
```

---

## Submitting from your own UI

To add a "Suggest a feature" form of your own, or to send feedback from anywhere in your app:

```kotlin
lifecycleScope.launch {
    try {
        val feature = Fewchurs.submitFeature(
            title = "Add dark mode",
            description = "Would love a dark theme.",   // optional
            email = "user@example.com",                  // optional: they get an email when it ships
            isSubscriber = true,                          // optional: defaults to your isSubscriber callback
        )
        Log.d("App", "Submitted ${feature.id} (${feature.status})")
    } catch (e: FewchursException) {
        showError(e.userMessage)
    }
}
```

The input is checked on the device first, using the same rules as the server (see [Limits](#limits-and-validation)). A new request also appears at the top of the board's **New** tab.

### Java

```java
FewchursJava.submitFeature("Add dark mode", "Would love a dark theme.", new FewchursJava.Callback<Feature>() {
    @Override public void onSuccess(Feature feature) { /* main thread */ }
    @Override public void onError(FewchursException error) { showError(error.getUserMessage()); }
});
```

---

## Identifying users

### Anonymous by default

Fewchurs doesn't need a login. On first use the SDK generates a **random ID for each install** and stores it in your app's private storage. That ID ties together a user's votes, follows, "You" badges and daily limits.

- It is **not** `ANDROID_ID` and **not** the advertising ID.
- It stays the same until the app is uninstalled or its data is cleared. It's included in Android Auto Backup, so votes survive a device restore.

### Signed-in users (optional)

If your app has accounts, you can make votes follow the user across devices:

```kotlin
Fewchurs.configure(this, apiKey = "fr_live_xxx") {
    deviceId = { auth.currentUser?.uid?.let(::sha256) }   // null → falls back to the random ID
}
```

> Always pass a **hash** of your user ID. Never pass an email address or a raw database ID.

### Subscribers and email

```kotlin
isSubscriber = { billing.hasActiveSubscription() }   // highlights paying users' requests in your dashboard
userEmail = { auth.currentUser?.email }              // skips the "enter your email" step when following
```

---

## Customization

Pick the lowest level that does what you need.

### Level 1: automatic (no code)

The board reads your `MaterialTheme` (color scheme, typography, shapes) and follows your dark mode. In a Material 3 app it already looks native.

### Level 2: theme tokens

For the whole app:

```kotlin
import com.fewchurs.sdk.ui.config.*

Fewchurs.configure(this, apiKey = "fr_live_xxx") {
    theme {
        colors {
            primary = Color(0xFFED5F18)
            background = Color.White
            statusColors[FeatureStatus.DONE] = Color(0xFF16A34A)
        }
        darkColors {                          // optional; falls back to `colors`
            primary = Color(0xFFFF8A4C)
        }
        cornerRadius = 14.dp
        fontFamily = MyFonts.Inter
        density = FewchursDensity.Compact     // Comfortable | Compact
        darkMode = DarkMode.System            // FollowHost (default) | System | Light | Dark
    }
}
```

Or for one screen only, Compose-style:

```kotlin
import com.fewchurs.sdk.ui.theme.*

FewchursTheme(
    colors = FewchursColors(
        primary = Color(0xFFED5F18), onPrimary = Color.White,
        background = Color(0xFFFFFBF5), surface = Color.White, onSurface = Color(0xFF1C1917),
        muted = Color(0xFF78716C), border = Color(0xFFF1E7DA), error = Color(0xFFDC2626),
        statusColors = mapOf(FeatureStatus.DONE to Color(0xFF16A34A)),
    ),
    shapes = FewchursShapes(
        card = RoundedCornerShape(20.dp), button = RoundedCornerShape(14.dp),
        chip = RoundedCornerShape(6.dp), sheet = RoundedCornerShape(28.dp),
    ),
    spacing = FewchursSpacing.Compact,
) {
    FewchursBoard()
}
```

Precedence: `FewchursTheme(...)` beats `configure { theme { } }`, which beats your `MaterialTheme`.

| Token group | Tokens |
|---|---|
| `FewchursColors` | `primary`, `onPrimary`, `background`, `surface` (cards), `onSurface`, `muted` (secondary text), `border`, `error`, `statusColors` |
| `FewchursShapes` | `card`, `button`, `chip`, `sheet` |
| `FewchursTypography` | `title`, `body`, `label` |
| `FewchursSpacing` | `screen`, `cardPadding`, `itemGap` (presets: `Comfortable`, `Compact`) |

`DarkMode` options:

- `FollowHost` (default): uses your `MaterialTheme` exactly as it is.
- `System`: picks light or dark from the system setting.
- `Light` / `Dark`: forces one mode.

### Level 3: behavior and copy

```kotlin
Fewchurs.configure(this, apiKey = "fr_live_xxx") {
    board {
        tabs = listOf(BoardTab.Top, BoardTab.New)
        defaultTab = BoardTab.Top
        showDeclined = true                          // false hides declined requests
        allowSubmissions = true                      // false hides "Suggest a feature"
        allowComments = true                         // false hides the comment box
        emailField = EmailField.Optional             // Hidden | Optional | Required (submit form)
        submitButtonStyle = SubmitButton.ExtendedFab // ExtendedFab | BottomBar | TopBarAction
        pageSize = 20
    }
    strings {
        boardTitle = "Ideas & roadmap"
        submitButton = "Suggest an idea"
        emptyTitle = "No ideas yet"
    }
}
```

> `BoardTab.Roadmap` (Planned / In progress / Done) is accepted but stays hidden until the Fewchurs API supports filtering by status.

### Level 4: slots (replace pieces, keep the logic)

```kotlin
FewchursBoard(
    header = { MyBoardHeader() },
    emptyState = { MyEmptyIllustration() },
    featureCard = { feature, actions ->
        MyFeatureRow(feature, onVote = actions.toggleVote, onClick = actions.open)
    },
    voteButton = { state ->
        MyVoteChip(count = state.count, voted = state.voted, showCount = !state.hideCount, onClick = state.onToggle)
    },
)
```

| Slot | Receives | Replaces |
|---|---|---|
| `header` | — | Content above the list |
| `emptyState` | — | The "No requests yet" message |
| `featureCard` | `Feature`, `FeatureCardActions(toggleVote, open)` | Each row in the list |
| `voteButton` | `VoteButtonState(count, voted, hideCount, onToggle)` | The vote control on the list and detail screens |

Paging, optimistic voting and error handling keep working with your slots. Inside a slot you can read the resolved tokens with `FewchursTheme.colors`, `FewchursTheme.shapes`, `FewchursTheme.typography` and `FewchursTheme.spacing`.

You can also reuse the built-in pieces in your own layouts: `VoteButton`, `StatusChip`, `FeatureCard` and `PoweredByBadge`.

### Level 5: headless

Skip the Fewchurs UI entirely. See [Headless API](#headless-api-build-your-own-ui).

### The "Powered by Fewchurs" badge

The badge appears at the end of the list on the Free plan. It's controlled by your plan on the server, so there's no option to hide it in code. Upgrading to **Starter** or **Pro** removes it automatically, the same as on iOS.

### How many requests the board lists

Your plan decides this on the server: the board shows an app's first 5 requests on Free, its first 200 on Starter, and all of them on Pro. No code or SDK update is involved. Newer requests are still saved and appear in your dashboard. A request a user just submitted shows on their board straight away, but once the app is over its limit it leaves the list on the next refresh. They can still open it with `Fewchurs.featureDetailState`.

---

## Strings and localization

Every piece of text is an Android string resource starting with `fewchurs_`. To change copy, or to translate it, define the same name in your app's `strings.xml`. Per-locale folders work as usual:

```xml
<!-- res/values/strings.xml -->
<string name="fewchurs_board_title">Ideas &amp; roadmap</string>

<!-- res/values-de/strings.xml -->
<string name="fewchurs_board_title">Ideen &amp; Roadmap</string>
<string name="fewchurs_submit_button">Idee vorschlagen</string>
```

The full list is in [`fewchurs-android/src/main/res/values/strings.xml`](fewchurs-android/src/main/res/values/strings.xml). Common keys:

| Key | Default |
|---|---|
| `fewchurs_board_title` | Feature requests |
| `fewchurs_submit_button` | Suggest a feature |
| `fewchurs_tab_top` / `fewchurs_tab_new` | Top / New |
| `fewchurs_empty_title` / `fewchurs_empty_message` | No requests yet / Be the first to suggest something. |
| `fewchurs_follow_dialog_message` | We'll email you when this ships. |
| `fewchurs_submitted_title` | Thanks for the suggestion! |
| `fewchurs_status_open` … `fewchurs_status_declined` | Open, Planned, In progress, Done, Declined |

Values set in `strings { }` take priority over resources, but they aren't localized. Use resources if your app supports more than one language.

English ships with 1.0. More languages are planned for 1.1.

---

## Headless API (build your own UI)

With just `fewchurs-core` you get state holders and a client, and render everything yourself.

### Board state

```kotlin
class FeedbackViewModel : ViewModel() {
    val board = Fewchurs.boardState(sort = Sort.Top, scope = viewModelScope)
}

@Composable
fun MyBoard(vm: FeedbackViewModel = viewModel()) {
    val state by vm.board.state.collectAsState()
    when (val s = state) {
        BoardUiState.Loading -> Spinner()
        is BoardUiState.Error -> ErrorView(s.error.userMessage, onRetry = vm.board::retry)
        is BoardUiState.Content -> LazyColumn {
            items(s.features, key = { it.id }) { f ->
                MyRow(f, onVote = { vm.board.toggleVote(f.id) })
            }
            if (s.canLoadMore) item { LaunchedEffect(Unit) { vm.board.loadMore() } }
        }
    }
}
```

| `BoardState` member | Description |
|---|---|
| `state: StateFlow<BoardUiState>` | `Loading`, `Error(error)` or `Content(features, canLoadMore, isLoadingMore, isRefreshing, loadMoreError)` |
| `messages: SharedFlow<FewchursException>` | One-off errors worth a snackbar, such as a failed vote |
| `refresh()` / `retry()` | Reload the first page |
| `loadMore()` | Load the next page. Safe to call repeatedly. |
| `toggleVote(featureId)` | Optimistic: updates instantly, syncs in the background, rolls back on failure |
| `close()` | Stops work. Only needed if you created it without a `scope`. |

`Fewchurs.boardState(sort, scope, pageSize = 20, hideDeclined = false)`. Pass a `scope` such as `viewModelScope` to tie it to a lifecycle.

### Feature detail state

```kotlin
val detail = Fewchurs.featureDetailState(featureId, scope = viewModelScope)

detail.feature        // StateFlow<Feature?>, always in sync with the board
detail.comments       // StateFlow<CommentsUiState>: Loading | Error | Content(comments, canLoadMore, …)
detail.toggleVote()
detail.loadMoreComments()
detail.follow("user@example.com")        // suspend, throws FewchursException
detail.unfollow()                          // suspend
detail.addComment("Love it", authorName = "Sam")   // suspend, returns Comment
```

### Raw client

The raw client doesn't do optimistic updates. It's useful for scripts, widgets or anything outside Compose. Every call is `suspend` and throws `FewchursException`.

```kotlin
val client = Fewchurs.client

val page = client.listFeatures(sort = Sort.New, limit = 20, cursor = null)
val next = client.listFeatures(sort = Sort.New, cursor = page.nextCursor)
client.vote(featureId)                 // returns the new upvote count
client.unvote(featureId)
client.follow(featureId, email = "a@b.com")
client.unfollow(featureId)
val comments = client.listComments(featureId)
client.addComment(featureId, text = "+1", authorName = "Sam")
val settings = client.settings()       // BoardSettings(showBranding, hideVoteCounts, appName)
```

Everything shares one in-memory cache. A vote made through the client, a state holder or the built-in UI shows up everywhere immediately.

---

## Events and analytics

The SDK **sends no analytics anywhere**. If you want to track usage, forward its events to your own tools:

```kotlin
lifecycleScope.launch {
    Fewchurs.events.collect { event ->
        when (event) {
            FewchursEvent.BoardOpened -> analytics.log("fewchurs_open")
            is FewchursEvent.FeatureSubmitted -> analytics.log("fewchurs_submit", event.featureId)
            is FewchursEvent.Voted -> analytics.log("fewchurs_vote", event.featureId, event.voted)
            is FewchursEvent.Commented -> analytics.log("fewchurs_comment", event.featureId)
            is FewchursEvent.Followed -> analytics.log("fewchurs_follow", event.featureId, event.following)
            is FewchursEvent.Error -> crashReporter.log(event.error)
        }
    }
}
```

You can collect `Fewchurs.events` before calling `configure`.

---

## Error handling

Every failure is a `FewchursException`. Its `userMessage` is safe to show to end users. The built-in UI shows localized messages from string resources instead.

| Exception | When | Default `userMessage` |
|---|---|---|
| `NotConfigured` | `configure` was never called and there's no manifest key | Something went wrong. Try again. |
| `InvalidApiKey` | The key is wrong or was revoked. Also logged to Logcat. | Something went wrong. Try again. |
| `Validation(message)` | Input was rejected | The specific reason |
| `RateLimited` | The daily per-device limit was reached | You've reached today's limit. Try again tomorrow. |
| `LimitReached` | The server turned the request away because of the account. Plans don't block new requests, so this should be rare. | This board isn't accepting requests right now. |
| `NotFound` | The request was deleted | This request no longer exists. |
| `Network` | Offline, timeout or DNS failure | You're offline. |
| `Server(statusCode)` | Fewchurs returned 5xx | Something went wrong. Try again. |
| `Internal`, `Unknown` | Unexpected response or SDK bug | Something went wrong. Try again. |

**Retries:** reads and votes retry automatically, up to twice, on network and server errors. Creating requests and comments is never retried automatically, so there are no duplicates.

**Never crashes your app:** if the board is shown before configuration, it displays a "Fewchurs isn't set up" message instead of crashing.

---

## Limits and validation

| Rule | Limit |
|---|---|
| Title | 3–100 characters |
| Description | Up to 1000 characters (optional) |
| Comment | 1–1000 characters |
| Display name | Up to 60 characters (optional; defaults to "App User") |
| Submissions | 15 per device per day |
| Comments | 10 per device per day |

The same rules are available in code as `FeatureValidation` (`TITLE_MIN`, `TITLE_MAX`, `isValidEmail(…)`, …) for your own forms.

---

## Privacy and Google Play Data Safety

Copy this into your Play Console Data Safety form:

| Data type | Collected | Shared | Purpose | Notes |
|---|---|---|---|---|
| Email address | Optional | No | App functionality | Only when the user types it to follow or submit. Used to notify them when a request ships. |
| User-generated content | Yes | No | App functionality | Request titles, descriptions and comments |
| App-scoped identifier | Yes | No | App functionality | Random per-install ID. Not linked to hardware or ads. |
| Analytics, ads, location, contacts | No | — | — | |

All traffic uses HTTPS. The SDK never reads `ANDROID_ID`, the advertising ID, contacts or location.

---

## Security

- **The API key is publishable.** It ships inside your APK and can be extracted, just like a Firebase or Stripe publishable key. It can only read your board and create votes, comments and requests. It can't access your dashboard or change settings.
- Abuse protection runs on the server: per-device daily limits, with more hardening on the way.
- Don't use a secret or admin credential with this SDK.
- To rotate a key, create a new one in the dashboard, ship an app update, then revoke the old one.

---

## R8 / ProGuard

You don't need to do anything. The SDK ships consumer rules that are applied automatically to minified builds.

---

## Troubleshooting

**The board shows "Fewchurs isn't set up".**
`Fewchurs.configure(...)` wasn't called before the board was shown, and there's no `com.fewchurs.sdk.API_KEY` in your manifest. Call `configure` in `Application.onCreate`, and make sure that `Application` class is registered in the manifest (`android:name`).

**"Couldn't load requests" on every open.**
Set `logLevel = LogLevel.Debug` and filter Logcat by `Fewchurs`.
- An `InvalidApiKey` error means the key is wrong or revoked.
- A `Network` error means there's no connection or the `baseUrl` is wrong.

**My votes disappeared after reinstalling.**
The anonymous ID is tied to each install. Use the `deviceId` option to tie votes to your signed-in users. Auto Backup restores the ID on a restored device, but not after a manual uninstall and reinstall without backup.

**Vote counts show even though I hid them in the dashboard.**
Update to the latest server version. Hiding vote counts depends on the server's `/config` response.

**The board overlaps my bottom navigation, or has double padding.**
Pass `windowInsets = WindowInsets(0)` when your own `Scaffold` already handles insets.

**I use Material 2 or a custom design system.**
The board still works; Material 3 comes in as a dependency. Set colors with `theme { colors { … } }` or `FewchursTheme(...)` so it matches your brand.

**I'm testing against a local server over plain HTTP.**
Android blocks cleartext traffic by default. Allow your dev host in a debug-only `network_security_config.xml`. The SDK itself only needs HTTPS.

---

## API reference

| Package | Main types |
|---|---|
| `com.fewchurs.sdk` | `Fewchurs`, `FewchursConfig`, `FewchursClient`, `FewchursJava`, `LogLevel` |
| `com.fewchurs.sdk.model` | `Feature`, `Comment`, `FeatureStatus`, `Sort`, `Page`, `BoardSettings`, `FewchursEvent`, `FeatureValidation` |
| `com.fewchurs.sdk.error` | `FewchursException` and its subclasses |
| `com.fewchurs.sdk.state` | `BoardState`, `BoardUiState`, `FeatureDetailState`, `CommentsUiState` |
| `com.fewchurs.sdk.ui` | `FewchursBoard`, `FewchursBoardSheet`, `FewchursActivity` |
| `com.fewchurs.sdk.ui.config` | `theme {}`, `board {}`, `strings {}`, `BoardTab`, `EmailField`, `SubmitButton`, `DarkMode`, `FewchursDensity` |
| `com.fewchurs.sdk.ui.theme` | `FewchursTheme`, `FewchursColors`, `FewchursShapes`, `FewchursTypography`, `FewchursSpacing` |
| `com.fewchurs.sdk.ui.components` | `VoteButton`, `StatusChip`, `FeatureCard`, `PoweredByBadge`, `VoteButtonState`, `FeatureCardActions` |

### Models

```kotlin
data class Feature(
    val id: String,
    val title: String,
    val description: String,
    val status: FeatureStatus,        // OPEN, PLANNED, IN_PROGRESS, DONE, DECLINED, UNKNOWN
    val upvoteCount: Int,
    val commentCount: Int,
    val followerCount: Int,
    val hasVoted: Boolean,            // this device voted
    val isFollowing: Boolean,         // this device follows
    val isMine: Boolean,              // this device submitted it
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class Comment(
    val id: String,
    val text: String,
    val authorName: String,
    val isDeveloper: Boolean,         // written by you from the dashboard
    val isMine: Boolean,
    val createdAtMillis: Long,
)
```

A status the SDK doesn't recognize arrives as `FeatureStatus.UNKNOWN`, so new server statuses never crash a shipped app.

`FewchursRepository`, `Fewchurs.repository`, `Fewchurs.emit` and `FewchursConfig.extensions` are marked `@FewchursInternalApi`. They're shared between the SDK modules and may change without notice, so don't rely on them.

---

## Sample app

The [`sample`](sample) module shows every integration style: default board, bottom sheet, heavily themed, headless custom UI, submitting from your own form, a View-based screen, and a live event log. [CONTRIBUTING.md](CONTRIBUTING.md) explains how to run it against a local mock server, with no account needed.

## Versioning

The SDK follows [semantic versioning](https://semver.org). Within a major version, public API changes are additive only. Deprecated APIs keep working for at least one minor release. See [CHANGELOG.md](CHANGELOG.md).

## Support

Questions or bugs: open an issue at [github.com/fewchurs/fewchurs-kotlin/issues](https://github.com/fewchurs/fewchurs-kotlin/issues).
