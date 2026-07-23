# TODO

Audit date: 2026-07-22 — 83 new findings audited, 4 real bugs fixed, 70 already fixed/acceptable, 9 deferred

---

## Fixed This Session (4)

- [x] `SnappingEngine.kt:146` — `sin()` → `asin()` for cross-track distance calculation
- [x] `VerticalPhaseNav.kt:62,72` — Phase locking logic: `previousAllDone` initial value `false` → `true`, lock condition `index == 0` → `index > 0 && !previousAllDone[index]`
- [x] `ApiService.kt:loadFeatures` — Added `response.status.isSuccess()` check (matching save/update/delete pattern)
- [x] `MapLibreDomMarker.kt` — All markers shared a single GeoJSON source; each add/update/remove destroyed all others. Added companion `activeMarkers` registry and `rebuildSource()` to reconstruct full FeatureCollection on every change

---

## Verified Already Fixed (70)

### Geoman Module

- [x] `Features.kt:64` — `getAllFeatures()` returns `featuresMap.toMap()` (defensive copy)
- [x] `Features.kt:107-115` — `updateFeature()` is `@Synchronized` with lambda pattern
- [x] `Features.kt:313` — `generateFeatureId()` uses `UUID.randomUUID()`
- [x] `MapLibreAdapter.kt:60-81` — `removeControl()` properly removes click/long-click/touch listeners
- [x] `MapLibreAdapter.kt:367-371` — TOCTOU in `off()` benign with `CopyOnWriteArrayList` + `ConcurrentHashMap.remove(type, value)`
- [x] `Geoman.kt:448-457` — `destroy()` uses synchronous `tryEmit()` before `removeAllListeners()` + `scope.cancel()`
- [x] `Geoman.kt:394-421` — `waitForGeomanLoaded()` uses `_loaded.first { it }` with `withTimeoutOrNull`
- [x] `Geoman.kt:114-137` — `waitForBaseMap()` uses `suspendCancellableCoroutine` + `addOnDidFinishLoadingStyleListener`
- [x] `Geoman.kt:270-273` — `getEnabledModes()` has bounds check + try/catch
- [x] `Geoman.kt:427` — `destroy()` idempotency guard `if (_destroyed.value) return`
- [x] `MapLibreLayer.kt:94-96,106-108` — Catch blocks use `android.util.Log.w()`
- [x] `MapLibreLayer.kt:145-147` — `setLayoutProperty()` calls `setProperties()` correctly
- [x] `GmControl.kt:55` — `activeModes` uses `ConcurrentHashMap.newKeySet()`
- [x] `BaseAction.kt:6` — `enabled` has `@Volatile`
- [x] `GmEventBus.kt:31` — Uses `android.util.Log.e()`
- [x] `DragEditor.kt:63` — `startDrag()` checks `!enabled || selectedFeature == null || isDragging`
- [x] `ChangeEditor.kt:55` — `onMapClick()` checks `isEditing` before starting new edit
- [x] `UndoManager.kt` (app) — All operations wrapped in `synchronized(lock)`

### App Module

- [x] `MapViewModel.kt:194-202` — Uses `_uiState.update {}`
- [x] `MapViewModel.kt:204-209` — `clearErrorMessage()`/`clearSuccessMessage()` use `_uiState.update {}`
- [x] `MapScreen.kt:159-169` — `@Stable` removed from `MapScreenViewState`
- [x] `MapScreen.kt:171-178` — `@Stable` removed from `MapScreenCallbacks`
- [x] `MapViewModel.kt:83-93` — `goToNextPhase()`/`goToPreviousPhase()` route through `setCurrentPhase()`
- [x] `NarsGeoman.kt:33` — `currentPhase` has `@Volatile`
- [x] `FeatureDisplayManager.kt:27` — `currentPhase` has `@Volatile`
- [x] `LabelAndMarkerManager.kt:30` — `vertexMarkerIds` uses `ConcurrentHashMap.newKeySet()`
- [x] `MapScreenHandlers.kt:39` — `narsGeoman` has `@Volatile`

---

## Not Applicable / Files Don't Exist (9)

- [x] `OfflineCacheManager.kt` — File does not exist in codebase
- [x] `AppNavigation.kt` — File does not exist; navigation is in `NarsNavHost.kt` (no issues)
- [x] `GpsStateManager.kt` — File does not exist in codebase
- [x] `NarsScaffold.kt` — File does not exist in codebase
- [x] `MapControls.kt` — File does not exist in codebase
- [x] `ValidationUtils.kt:128` — `isSameWilaya` / `.equals()` — `Validation.kt` uses Kotlin idioms, no `.equals()` calls
- [x] `MapUtils.kt:distanceMeters` — File does not exist; `GeometryUtils.kt` correctly uses `Math.toRadians()` on lat/lng values
- [x] `Theme.kt:36-38` — `crossinline` not needed; `content` is `@Composable () -> Unit` matching `MaterialTheme` signature
- [x] `Theme.kt` colorScheme instability — Color scheme is a top-level `val`, not recreated per recomposition

---

## Deferred / Acceptable (9)

- [ ] `MapViewModel.kt` — `MapViewModel` as parameter in `NarsMap` is unstable type (Compose optimization, low impact)
- [ ] `FeatureStore.kt:14` — `undoManager` is public; exposes internal to tests; make `private` + refactor tests
- [ ] `SessionManager.kt` — Login/logout race with `AppPreferences` (SharedPreferences is main-thread only; race window negligible)
- [ ] `MapViewModelTest.kt` — All dependencies `mockk(relaxed = true)`, tests are pure mock-forwarding
- [ ] `FeatureDisplayManagerTest.kt` — Relaxed mocks, interaction-only verification
- [ ] `GeomanEventHandlerTest.kt` — Missing tests for null geometry, partial circle props, Polygon type
- [ ] `NarsGeomanTest.kt` — Relaxed mocks, `updateFeatureOnMap` delegation untracked
- [ ] `SessionManagerTest.kt` — No concurrency tests for login/logout race
- [ ] `ApiServiceTest.kt` — No tests for HTTP 500, timeout, malformed JSON
