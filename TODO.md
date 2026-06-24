# TODO — Code Quality Issues

All identified issues have been fixed.

## Fixes Applied

### Code Quality
- **API response status check before body parsing** — `ApiService.kt`
- **Regex extracted to companion constant** — `FeatureDisplayManager.kt`
- **Unused imports removed** — `NarsMap.kt`, `AppPreferences.kt`
- **Java Koin API replaced with Kotlin `get()`** — `NarsApplication.kt`
- **Fully qualified names replaced with imports** — `GeomanEventHandler.kt`, `MapScreenHandlers.kt`, `FeatureRenderer.kt`
- **Dead code `dismissContextMenu()` removed** — `ContextMenuManager.kt`
- **String-concatenated GeoJSON replaced with `kotlinx.serialization.json` builders** — `LabelAndMarkerManager.kt`
- **`loginFieldColors()` renamed to `LoginFieldColors()`** — `LoginScreen.kt`
- **Multi-statement lines formatted** — `MapScreen.kt`
- **Indentation fixed** — `FeatureRenderer.kt`
- **Duplicate phase-filtering extracted to helper** — `MapScreenHandlers.kt`
- **Tile URLs configurable via `local.properties`** — `Config.kt`, `build.gradle.kts`

### detekt Configuration
- Added `UnusedImport`, `empty-blocks`, `comments`, `coroutines` rulesets

### Test Coverage
- **FeatureDisplayManagerTest.kt** — 11 tests covering add, filter, update, remove, clear
- **LabelAndMarkerManagerTest.kt** — 5 tests covering label layer, endpoint markers, vertex markers
- **SnappingEngineTest.kt** — 9 tests covering point snapping, thresholds, geometry types
- **ApiErrorsTest.kt** — 10 tests covering retry logic, backoff, non-retryable errors
