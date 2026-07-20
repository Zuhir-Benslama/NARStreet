# TODO

## App Module Issues

### High

- [x] `goToNextPhase()`/`goToPreviousPhase()` bypass ViewModel state — routed through `setCurrentPhase()` to persist via `appPreferences` and reset UI flags (`MapViewModel.kt:83-93`)
- [x] `@Stable` on `MapScreenViewState` with unstable `List<NarsFeature>` — removed annotation (`MapScreen.kt:159-169`)

### Medium

- [x] `currentPhase` in `NarsGeoman` — added `@Volatile` (`NarsGeoman.kt:33`)
- [x] `currentPhase` in `FeatureDisplayManager` — added `@Volatile` (`FeatureDisplayManager.kt:27`)
- [x] `vertexMarkerIds` in `LabelAndMarkerManager` — changed to `ConcurrentHashMap.newKeySet()` (`LabelAndMarkerManager.kt:30`)
- [x] `narsGeoman` in `MapScreenHandlers` — added `@Volatile` (`MapScreenHandlers.kt:39`)
- [ ] `MapViewModel` as parameter in `NarsMap` — unstable type causes unnecessary recompositions (`NarsMap.kt:41`)
- [x] `@Stable` on `MapScreenCallbacks` — removed annotation (`MapScreen.kt:171-178`)

### Low

- [x] `_uiState.value = _uiState.value.copy(...)` — replaced with atomic `_uiState.update {}` (`MapViewModel.kt:194-202`)
- [x] `clearErrorMessage()`/`clearSuccessMessage()` still use non-atomic `_uiState.value = _uiState.value.copy(...)` — should use `_uiState.update {}` for consistency (`MapViewModel.kt:204-209`)

---

## Geoman Module Issues

### High

- [x] `destroy()` race condition — `scope.launch { emit(Destroyed) }` is async, so `removeAllListeners()` and `scope.cancel()` run before emission; Destroyed event is never received; needs non-suspending `tryEmit` path (`Geoman.kt:448-457`, `GmEventBus.kt:24`)
- [x] Map/touch listeners registered in `addControl()` never unregistered in `removeControl()` — stored references and remove in `removeControl()` (`MapLibreAdapter.kt:60-81`)
- [x] `updateFeature()` read-modify-write not atomic — added `@Synchronized` (`Features.kt:107-115`)
- [x] `featuresMap` unsynchronized — only `updateFeature` is `@Synchronized`; `addFeature`, `removeFeature`, `clearSource`, `clearAll`, `getFeatures`, `getFeaturesInBounds` are not; concurrent access causes `ConcurrentModificationException` (`Features.kt:46-119`)
- [x] `generateFeatureId()` collision risk — `System.currentTimeMillis() + Math.random()` can collide on fast devices; use `UUID.randomUUID()` (`Features.kt:313`)

### Medium

- [x] `enabled` flag in `BaseAction` — added `@Volatile` (`BaseAction.kt:6`)
- [x] TOCTOU race in `MapLibreAdapter.off()` — capture list reference before check-then-remove (`MapLibreAdapter.kt:367-371`)
- [x] `activeModes` in `GmControl` — changed to `ConcurrentHashMap.newKeySet()` (`GmControl.kt:55`)
- [x] `destroy()` is idempotent — `if (_destroyed.value) return` guard prevents double cleanup (`Geoman.kt:427`)
- [x] `e.printStackTrace()` in `GmEventBus` — replaced with `android.util.Log.e()` (`GmEventBus.kt:31`)
- [ ] `getAllFeatures()` exposes mutable inner `MutableMap` references (`Features.kt:64`)
- [x] Empty catch blocks silently swallow layer errors in `MapLibreLayer` — added logging (`MapLibreLayer.kt:94-96,106-108`)
- [x] `waitForGeomanLoaded()` polling loop — replace with `_loaded.first { it }` with timeout (`Geoman.kt:394-421`)
- [x] `waitForBaseMap()` polling loop — replace with `addOnDidFinishLoadingStyleListener` callback (`Geoman.kt:114-137`)

### Low

- [x] `getEnabledModes()` crashes on malformed key format — added bounds check and try/catch (`Geoman.kt:270-273`)
- [x] `setLayoutProperty()` delegated to `setPaintProperty()` — now calls `setProperties()` correctly (`MapLibreLayer.kt:145-147`)
- [x] Empty cancellation handler in `waitForGeomanLoaded()` — merged into polling-loop fix above

---

## Test Issues

### High

- [ ] `MapViewModelTest` — all 4 dependencies `mockk(relaxed = true)`, 7 tests are pure mock-forwarding with no behavioral assertions (`MapViewModelTest.kt:40-43,185-311`)
- [ ] `MapViewModelTest` — missing edge cases for all public methods: null inputs, duplicate IDs, exception paths, toggle-off (`MapViewModelTest.kt`)

### Medium

- [ ] `FeatureDisplayManagerTest` — all 5 dependencies relaxed, interaction-only verification (`FeatureDisplayManagerTest.kt:54-58`)
- [ ] `GeomanEventHandlerTest` — missing tests for null geometry, partial circle props, Polygon type (`GeomanEventHandlerTest.kt`)
- [ ] `NarsGeomanTest` — all 7 component mocks relaxed, `updateFeatureOnMap` delegation test untracked (`NarsGeomanTest.kt:58-64,290`)
- [ ] `SessionManagerTest` — no concurrency tests for login/logout race (`SessionManagerTest.kt`)
- [ ] `ApiServiceTest` — no tests for HTTP 500, timeout, malformed JSON (`ApiServiceTest.kt`)
