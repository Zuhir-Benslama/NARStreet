# TODO

## Code Quality Issues (app)

### Major

- [x] Release builds log HTTP response bodies (`LogLevel.BODY`) via Timber, leaking tokens/PII — changed to `LogLevel.NONE` in release (`AppModule.kt:63`)
- [x] Auth token sent redundantly as both `Cookie` and `Authorization: Bearer` header — removed `Cookie`, kept `Bearer` only (`ApiService.kt:69-70`)
- [x] `isLenient = true` in JSON parsing accepts malformed input — removed from both `AppModule.kt` and `ApiModels.kt`
- [x] Race condition: `GeomanEventHandler.setEditingFeature()` writes `editingFeatureId` + `editingFeature` non-atomically — synchronized via `editingLock` (`GeomanEventHandler.kt:33-46`)
- [x] Race condition: `MapViewModel.updateFeature()` reads old feature, updates, then records undo — uses `let` to capture old feature before update (`MapViewModel.kt:137-148`)
- [x] `NarsGeoman` creates `CoroutineScope` not tied to any lifecycle — now accepts external scope from caller (`NarsGeoman.kt:48-96`)
- [ ] `MapScreenHandlers` is a 264-line god class combining UI events, API calls, drawing, and domain logic (`MapScreenHandlers.kt`)
- [x] `MapViewModel` directly accesses `featureStore.undoManager.canUndo` internal state — exposed `canUndo`/`executeUndo`/`addUndoAction` on interface, removed `undoManager` from interface (`MapViewModel.kt`, `FeatureStoreInterface.kt`)

### Moderate

- [x] `NarsApplication` catches broad `RuntimeException` silently — narrowed to `IllegalStateException` (Koin not ready) (`NarsApplication.kt:45,61,72`)
- [x] `withRetry` catches `Exception` generically — added `@file:Suppress("TooGenericExceptionCaught")` with documented rationale (`ApiErrors.kt:93`)
- [x] `FeatureStore.setCurrentPhase` not protected by lock unlike all other mutations — now uses `lock.withLock` (`FeatureStore.kt:42-44`)
- [x] `MapScreen.kt` uses `KoinJavaComponent.get()` instead of `koinInject()` — changed to `koinInject()` (`MapScreen.kt:78-79`)
- [x] `ApiPropertiesRequest` / `ApiPropertiesResponse` nearly identical with duplicated mapping (`ApiModels.kt`)
- [x] Duplicated feature-hitting logic in `isPointNearFeature()` and `handleMapLongClick()` — unified to single method with threshold parameter (`MapScreenHandlers.kt`)
- [ ] `FeatureDisplayManager.updateDisplayedFeatures` does O(n*m) comparison on main thread (`FeatureDisplayManager.kt:51-73`)
- [ ] `NarsMap` creates `MapView` in `remember` without key — survives recomposition with stale context (`NarsMap.kt:60-62`)
- [ ] `MapScreenHandlers` captured in `remember` without keys — holds stale references after recreation (`MapScreen.kt:89-93`)
- [x] Phase key magic strings used inconsistently — changed `"roads"` to `Phases.ROADS_KEY` in `LabelAndMarkerManager.kt` and `ApiModels.kt`
- [ ] `NarsFeature.parsedColor` uses Compose `Color` in a data model, coupling data layer to UI framework (`NarsFeature.kt:111-118`)
- [ ] `MapViewModel` exposes 16+ public mutation methods — should group into sealed action classes (`MapViewModel.kt`)
- [x] `AppPreferences` uses `runCatching` which swallows `CancellationException` — changed to try/catch with specific `IllegalArgumentException` (`AppPreferences.kt:36-38,43-48`)
- [x] `GeometryConverter.geometryToJsonElement` falls back to `(0,0)` for unknown types — now throws `IllegalArgumentException` (`GeometryConverter.kt:174-180`)

### Minor

- [ ] `@Suppress("TooManyFunctions")` in `MapViewModel.kt:24` and `FeatureStore.kt:12` without addressing root cause
- [ ] `NarsGeoman` companion `invoke` operator is unconventional — use named factory (`NarsGeoman.kt:48-97`)
- [ ] `lateinit var labelAndMarkerManager` in `FeatureRenderer` set externally after construction (`FeatureRenderer.kt:21`)
- [ ] `Config.kt` reads `BuildConfig` at class load time in `val` properties (`Config.kt:30-45`)
- [ ] `ContextMenuManager` uses View-based `PopupMenu` in a Compose project (`ContextMenuManager.kt:16`)
- [ ] `SnappingEngine.nearestPointOnSegment` uses full spherical trig — simple Euclidean approximation sufficient (`SnappingEngine.kt:135-156`)
- [ ] `LoginRequest` defined in `User.kt` instead of `ApiModels.kt` — moved to `ApiModels.kt` (`User.kt:39-40`)
- [ ] `LoginScreen` uses `rememberCoroutineScope` for login — should use `viewModelScope` (`LoginScreen.kt:61`)
- [x] Hardcoded fallback color `"#8e44ad"` should be a named constant — extracted `DEFAULT_FALLBACK_COLOR` (`FeatureRenderer.kt:159`)
- [x] `UndoManager.executeUndo()` extracts `roadDbId` and logs it but never uses it — removed dead code (`UndoManager.kt:34-40`)
- [ ] `FeatureDisplayManager` and `FeatureRenderer` both track rendered feature IDs in parallel sets (`FeatureDisplayManager.kt:26`, `FeatureRenderer.kt:49`)
- [ ] `SettingsViewModel` extends `ViewModel` but never uses `viewModelScope` (`SettingsViewModel.kt:9`)
- [ ] `VerticalPhaseNav` computes `previousAllDone` imperatively inside Composable — should be pure function (`VerticalPhaseNav.kt:62-66`)
- [ ] Two different `GeometryUtils` classes in `com.nars.maplibre.utils` and `com.geoman.maplibre.geoman.utils`

---

## Code Quality Issues (maplibre-geoman-android)

### Major

- [x] `Destroyed` event emitted after `scope.cancel()` — moved emit before cancel (`Geoman.kt:444-449`)
- [x] Thread-unsafe `mutableMapOf`/`mutableSetOf` shared across coroutines — changed to `ConcurrentHashMap`/`ConcurrentHashMap.newKeySet()` (`Features.kt:45`, `GmEventBus.kt:16`, `Geoman.kt:77`, `SourceUpdateManager.kt:21`, `GmOptions.kt:196`)
- [x] `MapLibreDomMarker.remove()` wipes ALL markers — now clears source (existing limitation: source only supports one marker at a time) (`MapLibreDomMarker.kt:232-247`)
- [x] Feature ID collision risk: `System.currentTimeMillis()` called twice — computed once, reused (`PolygonDrawer.kt:94,97`, `LineDrawer.kt:58,61`, `RectangleDrawer.kt:67,70`, `CircleDrawer.kt:63,66`)
- [x] `GeomanControls` composable never calls `disableMode` on deactivation — now calls `disableMode` when toggling off (`GmControl.kt:251-380`)
- [x] `FeatureData` data class has `MutableMap` property — changed to immutable `Map` (`Features.kt:21`)
- [x] `!!` operator on nullable `visibleRegion` projection fields — changed to safe call with fallback (`MapLibreAdapter.kt:100-101`)
- [x] Unsafe cast of `Any` to `MapLibreMap` — changed to `as?` with descriptive exception (`MapLibreDomMarker.kt:31`)
- [x] `waitForGeomanLoaded` continuation leak — added `continuation.isActive` check before resume (`Geoman.kt:396-412`)
- [x] `ConcurrentModificationException` risk in `enableMode` — collect keys first, then remove in separate loop (`Geoman.kt:200-205`)

### Moderate

- [x] `!!` operator in edit modes — replaced with safe local captures (`RectangleDrawer.kt:34`, `CircleDrawer.kt:35-36`, `RotateEditor.kt:70,81,83`, `DragEditor.kt:86`)
- [x] Triple-duplicated `updateFeatureGeometry` method — extracted to `BaseEdit` (`DragEditor.kt`, `ChangeEditor.kt`, `RotateEditor.kt`)
- [ ] Triple-duplicated Haversine distance calculation (`GeometryUtils.kt:72-85`, `GeoJsonTypes.kt:148-160`, `BaseMapAdapter.kt:180-192`)
- [x] Triple-duplicated source name constants — now delegate to `GeomanCoreConstants` from `FeatureSources` and `GeomanConstants`
- [ ] Excessive production `Log.d/e/w()` calls in hot paths — performance + security (`multiple files`)
- [x] `SourceUpdateManager` creates new `Json` instance per call — moved to companion object val (`SourceUpdateManager.kt:79`)
- [ ] `isGeometryInBounds` returns `true` for all non-Point geometries regardless of actual bounds (`Features.kt:299-306`)
- [ ] `SnapHelper.pixelsToMeters` hardcoded stub returns pixels as meters (`SnapHelper.kt:157-161`)
- [ ] `GmControl.activeModes` mutated by both `GmControl` and `Geoman` — confusing dual ownership (`GmControl.kt:55`, `Geoman.kt:214-215`)
- [ ] `enableMode` bypasses `disableMode` event emission when switching modes (`Geoman.kt:200-205`)
- [x] `MarkerDrawer.lastClickTime` declared but never used — removed (`MarkerDrawer.kt:20`)
- [ ] `DeleteEditor.removeFromMap` is an empty stub with no-op branches (`DeleteEditor.kt:61-85`)
- [ ] `SnapHelper.showSnapGuides`/`hideSnapGuides` are empty stubs (`SnapHelper.kt:172-182`)
- [ ] `GmEventBus.once()` modifies listener list during iteration — mitigated by `CopyOnWriteArrayList` (`GmEventBus.kt:43-55`)
- [ ] Hardcoded layer colors in `Features.kt:203-296` ignore configurable `GmOptions.layerStyles`

### Minor

- [ ] Magic numbers throughout: polling timeouts, hit tolerances, vertex sizes, random ID ranges (multiple files)
- [ ] Redundant `ModeName` sealed class hierarchy — `DrawModeName` enum and `DrawMode` sealed class represent same concepts (`ModeTypes.kt:47-101`)
- [ ] `GmOptions` duplicates mode tracking from `Geoman` — `enabledModes` set never synchronized (`GmOptions.kt:196-245`)
- [ ] `SnapHelper.snap()` ignores `project()` return value — call does nothing (`SnapHelper.kt:62`)
- [ ] No input validation on `enableMode`/`disableMode` string params — invalid names silently ignored (`Geoman.kt:193,230`)
- [ ] `PolygonDrawer.updatePolygonFeature` double-closes ring (`PolygonDrawer.kt:84-88` + `finishDrawing` line 56)
- [ ] `isPointInBounds` incorrect for non-axis-aligned bounds / antimeridian (`GeometryUtils.kt:121-129`)
- [ ] `centroidFromFlat`/`flatToLngLat` will crash on odd-length lists (`GeometryUtils.kt:62-66,310`)
- [ ] `GmControl.createButton` creates empty `BitmapDrawable` — ignores `icon` parameter (`GmControl.kt:169-171`)
- [x] `GeomanConstants` deprecation incomplete — constants now delegate to `GeomanCoreConstants` (`GeomanConstants.kt:5-21`)
- [x] `MapLibreAdapter.fire()` uses `printStackTrace()` instead of proper logging — changed to `Log.e()` (`MapLibreAdapter.kt:336-344`)
- [ ] `GeometryCollection.coordinates` returns `Any` losing all type safety (`GeoJsonTypes.kt:138`)
- [ ] Empty `else ->` branches in `when` expressions — silent no-ops (`DragEditor.kt:138-140`, `RotateEditor.kt:157-159`, `ChangeEditor.kt:251-253,282-284,315-317`)
- [ ] `MapLibreSource.setData` double-create retry can leave source in inconsistent state (`MapLibreSource.kt:38-52`)

---

## Code Quality Issues (tests)

### Major

- [x] `ApiErrorsTest` — 7 tests used try/catch without failure guard — added `fail()` after each operation to catch silent passes (`ApiErrorsTest.kt:46-158`)
- [x] `FeatureRendererTest` — 5 tests claimed to verify layer types but only verified `addLabelLayer` — now verify actual layer/source factory calls (`FeatureRendererTest.kt:167-188`)
- [x] `SnappingEngineTest` — 4 tests used `assertNotNull` — now assert exact expected snap coordinates (`SnappingEngineTest.kt:42-43,65-66,78-79,92-93`)
- [ ] `NarsGeomanTest` — 6 delegation tests only verify mock forwarding, not behavior (`NarsGeomanTest.kt:254-310`)

### Moderate

- [ ] `FeatureRendererTest` — all dependencies `mockk(relaxed = true)` hides broken code paths (`FeatureRendererTest.kt:42-71`)
- [ ] `FeatureDisplayManagerTest` — all relaxed mocks, interaction-only verification (`FeatureDisplayManagerTest.kt:54-58`)
- [ ] `GeomanEventHandlerTest` — tests mock the same types the code delegates to (`GeomanEventHandlerTest.kt:139-202`)
- [ ] Phase definition constants duplicated across 3 test files (`GeomanEventHandlerTest.kt:39-65`, `FeatureDisplayManagerTest.kt:33-51`, `NarsGeomanTest.kt:53-54`)
- [ ] `FeatureStoreTest` — no concurrency tests despite `ReentrantLock` usage (`FeatureStoreTest.kt`)
- [ ] `ValidationTest` — missing edge cases for partial values, success paths, non-standard types (`ValidationTest.kt`)
- [ ] `ApiServiceTest` — no verification of auth headers on authenticated requests (`ApiServiceTest.kt`)

### Minor

- [ ] `FeatureRendererTest` — tautological constant test that can never fail (`FeatureRendererTest.kt:191-195`)
- [ ] `LabelAndMarkerManagerTest` — 3 smoke tests with no assertions (`LabelAndMarkerManagerTest.kt:39-65`)
- [ ] `PhaseNavigatorTest` — no shared `@Before` setup, 306 lines with repeated boilerplate (`PhaseNavigatorTest.kt`)
- [ ] `ApiServiceTest` — MockEngine/HttpClient boilerplate repeated 6 times (`ApiServiceTest.kt`)
- [ ] `SnappingEngineTest` — `nearestPointOnSegment` only tests degenerate case (`SnappingEngineTest.kt:97-103`)
