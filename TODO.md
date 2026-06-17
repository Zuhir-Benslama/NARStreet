# TODO — Code Quality Issues

## Completed
- [x] Refactor `Geoman.kt` (577 → 484 lines) — extracted `BaseAction`, `GeomanConstants`, `ModeFactory`
- [x] Align dependency versions — removed stale `maplibre-geoman-android/gradle/libs.versions.toml` and unused version entries
- [x] Add missing detekt rules: `CyclomaticComplexMethod`, `LongParameterList`, `NestedBlockDepth`
- [x] Raised `TooManyFunctions` threshold to 15
- [x] Removed `@Suppress("TooManyFunctions")` from: `ApiService`, `FeatureStore`, `FeatureRenderer`, `GeomanEventHandler`, `SecurePreferences`
- [x] Extracted `UndoManager` from `FeatureStore` (18 → 15 functions)
- [x] Combined UI state helpers in `MapViewModel` (19 → 15 functions)
- [x] Removed dead field API methods from `ApiService` (14 → 11 functions)

## Remaining
- [ ] `NarsGeoman` (18 functions) — facade class, still needs `@Suppress("TooManyFunctions")`
- [ ] Fix naming convention mismatches in `BaseLayer.kt`, `NarsNavHost.kt`, `Theme.kt`, `PhaseBar.kt`
- [ ] Review `compileSdk 37` (Android 16) compatibility
