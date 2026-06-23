# TODO — Code Quality Issues

## Completed

### Critical
- [x] Fix unsafe cast `androidContext() as Application` in `AppModule.kt:63` — use `androidContext().applicationContext as Application`

### High
- [x] Fix `chunked(2)[1]` crash on odd-length coordinate arrays in `GeometryConverter.kt` and `SnappingEngine.kt` — filter incomplete chunks
- [x] Unify Earth radius constants to `6,378,137` (WGS-84) in both `GeometryUtils` and `GeometryConverter`
- [x] Fix `nearestPointOnSegment()` to use spherical geometry (haversine-based interpolation) in `SnappingEngine.kt`

### Medium
- [x] Replace hardcoded `"roads"`, `"houseEntrances"` strings with `Phases.*` constants in `PhaseNavigator.kt` and `FeatureStore.kt`
- [x] Deduplicate `snapToLineString()` and `snapToPolygon()` into shared `snapToCoordPath()` in `SnappingEngine.kt`
- [x] Remove dead `CoroutineScope` in `NarsGeoman.kt` companion factory
- [x] Align timeout constants — `Config.API_DEFAULT_TIMEOUT_MS` now consumed by `AppModule.kt`'s Ktor client
- [x] Reduce `MapScreenBody` / `MapScreenBoxContent` parameter counts (23→9) — introduced `MapScreenViewState` + `MapScreenCallbacks`
- [x] Add SSL pinning infrastructure via `BuildConfig.SSL_CERT_HASHES` (configurable in `local.properties`)
- [x] Fix `chunked(2)[1]` crash in `MapScreenHandlers.kt` — add `.filter { it.size == 2 }` in `handleMapLongClick` and `isPointNearFeature`
- [x] Remove `@Suppress("TooManyFunctions")` from `NarsGeoman.kt` — made `displayManager` and `snappingEngine` public, removed 8 delegation wrappers
- [x] Migrate `window.statusBarColor` (deprecated) to edge-to-edge via `WindowCompat.setDecorFitsSystemWindows(window, false)`
- [x] Introduce `PhaseNavigationResult` sealed class for typed phase navigation errors instead of raw strings
- [x] Sanitize `feature.id` for MapLibre layer/source IDs (replace non-alphanumeric chars with `_`)
- [x] Move `Double.formatDecimal()` extension from `ValidationFields.kt:206` to `utils/Formatters.kt`

### Low
- [x] Make tile URLs, glyphs URL, attribution in `Config.kt:31-44` configurable via `BuildConfig` — added `local.properties.example` documentation

## Remaining
- [ ] Remove unused imports (compile warnings)
- [ ] Add proper edge-to-edge status bar handling in Activity (currently handled in Theme)
