# Changelog

## 1.0.1 (unreleased)

- The group ID is now `com.fewchurs`: use `com.fewchurs:fewchurs-android` and `com.fewchurs:fewchurs-core`. 1.0.0 stays available as `shop.fewchurs`.
- The default API server is now `https://www.fewchurs.com/api/v1`.

## 1.0.0 (unreleased)

First release, published to Maven Central as `shop.fewchurs:fewchurs-android` and `shop.fewchurs:fewchurs-core`, and on JitPack.

- `FewchursBoard`, `FewchursBoardSheet` and `Fewchurs.showBoard(context)`: list, detail, voting, comments, follow and submit.
- Setup through a manifest `meta-data` entry or `Fewchurs.configure { }`.
- Theming: inherits `MaterialTheme`; `theme {}` tokens, scoped `FewchursTheme(...)`, dark mode options, density.
- Behavior and copy: `board {}`, `strings {}`, and overridable `fewchurs_*` string resources.
- Slots: `header`, `emptyState`, `featureCard`, `voteButton`.
- Headless API: `Fewchurs.boardState`, `Fewchurs.featureDetailState` and `Fewchurs.client`.
- Optimistic, debounced voting with rollback; one shared cache across screens.
- `Fewchurs.events` for your own analytics; the SDK sends none.
- Java wrappers (`FewchursJava`) and consumer R8 rules.
