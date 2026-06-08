# NARStreet (Android) — TODO

## P1 — Static Analysis & Build
- [x] Run detekt — 23 issues fixed (MaxLineLength, TooGenericExceptionCaught, ReturnCount); now clean
- [x] Fix lint build — `espresso-core:3.6.1` inlined in `maplibre-geoman-android/app/build.gradle.kts`
- [x] Remove empty source dirs: `app/src/test/java/.../ui`, `mipmap-*`, `domain/`, `store/`, `components/`

## P1 — Test Coverage Gaps
- [x] Unit tests for `FeatureStore` — 16 tests added (executeUndo for Create/Update/Delete, setReferenceRoad, getCurrentPhaseFeatures, setCurrentPhaseByKey, getFeatureCounts, getFeatureById, selectFeature, removeFeature)
- [x] Unit tests for `PhaseNavigator` — 19 tests exist
- [x] Unit tests for `NarsGeoman` — 27 tests exist
- [x] Unit tests for `FeatureRenderer` — 13 tests exist
- [x] Unit tests for `GeomanEventHandler` — 24 tests exist

## P2 — Code Quality Hotspots
- [x] Decompose `NarsGeoman` — display methods extracted into `FeatureDisplayManager` (173 lines)
- [x] Convert `ApiService` JSON parsing — `saveFeature()`/`createEntranceFromInspection()` use `@Serializable SaveFeatureResponse`/`CreateEntranceResponse`
- [x] Specialize `FeatureProperties` — replaced flat data class with sealed class hierarchy (RoadProperties, HouseEntranceProperties, NamingPanelProperties), removed 11 dead fields
- [x] Fix inconsistent undo — `FeatureStore.executeUndo()` now handles Create (remove), Update (restore old), Delete (re-add)
- [x] Remove stale `colors.xml` values — only `primary` remains (referenced by launcher icons)
- [x] Remove unused `androidx-espresso` dependency from version catalog
- [x] Remove dead espresso resolution strategy from `build.gradle.kts`

## P3 — Nice-to-have
- [x] Instrumented (Compose UI) tests for map interactions — LoginScreen covered (5 tests)
- [x] Compose UI test infrastructure: `androidTest` directory, mockk dependency, Koin test setup
- [x] Add `AGENTS.md` with build/test/lint commands for AI-assisted development
- [x] Cover remaining ViewModel edge cases (sequential undo/redo, concurrent phase changes)
- [x] Fix duplicate operations in ViewModel.undo() — Create/Update paths no longer re-execute the operation already done by FeatureStore.executeUndo()
- [x] Enable HTTPS in nginx for meaningful HSTS — HSTS annotations added to frontend ingress (`max-age=31536000`, `includeSubdomains`); `upgrade-insecure-requests` added to CSP in nginx config

---

## Remaining Issues (Found June 2026)

### P1 — Fix Immediately
- [ ] **P1 — Remove 10 stale detekt baseline entries**: References to deleted/fixed code in `app/detekt-baseline.xml` (ApiUtils.kt, ApiUtilsTest.kt, FeatureRenderer.kt, NarsGeoman.kt)
- [ ] **P1 — Extract 22 hardcoded validation strings**: `Validation.kt` lines 68-242 embed user-visible error messages as string literals instead of `R.string.*` resources
- [ ] **P1 — Extract hardcoded strings in MapViewModel.kt**: Lines 98, 103, 106, 109 (`"Nothing to undo"`, `"Restored:..."`, `"Removed:..."`) should use string resources
- [ ] **P1 — Fix unsafe casts**: `Theme.kt:121` (`(view.context as Activity)` — crashes in non-Activity contexts); `GeomanEventHandler.kt:187` (`featureData.geometry as Polygon` — unchecked cast)
- [ ] **P1 — Fix display bug in InfoPanel.kt:165**: `phase.label.take(3)` renders resource key string (e.g. `"phase_roads_label"` → `"pha"`) instead of display name; use `Phases.getDisplayLabel(phase, context)`

### P2 — Address Soon
- [ ] **P2 — Use Config constants in NarsMap.kt**: Lines 87-90 re-hardcode `28.0`, `2.5`, `5.0` instead of using existing `Config.MAP_DEFAULT_LAT/LNG/ZOOM/BEARING/PITCH`
- [ ] **P2 — Extract duplicate source-name list**: `[SOURCE_MARKERS, SOURCE_LINES, SOURCE_POLYGONS, SOURCE_CIRCLES]` repeated across `NarsGeoman.kt` (x2), `FeatureDisplayManager.kt`, `GeomanEventHandler.kt` — extract to shared constant
- [ ] **P2 — Reduce bare `catch (e: Exception)` in LabelAndMarkerManager/FeatureDisplayManager**: 10 instances across `LabelAndMarkerManager.kt` (8) and `FeatureDisplayManager.kt` (2) — catch more specific exceptions where possible

### P3 — Nice-to-have
- [ ] **P3 — Remove unused `@Suppress("UNUSED_PARAMETER")` in NarsMap.kt:112**: Remove the unused `context` parameter instead of suppressing
- [ ] **P3 — Fix inefficient `getOrPut` in FeatureStore.kt:46**: Double map write (`getOrPut` then `currentMap[key] = ...`) — use `toMutableList()` instead
