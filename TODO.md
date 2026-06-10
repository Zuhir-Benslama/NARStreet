# NARStreet (Android) — Code Quality Audit ✅

**Status: All P1 + P2 items resolved. Detekt baseline 74 → 0. Build clean.**

## Summary

| Metric | Before | After |
|---|---|---|
| Detekt baseline entries | 74 | **0** |
| LongMethod | 6 | 0 |
| CyclomaticComplexMethod | 3 | 0 |
| NewLineAtEndOfFile | 2 | 0 |
| SwallowedException | 2 | 0 |
| ReturnCount | 3 | 0 |
| MagicNumber | 8 | 0 |
| MatchingDeclarationName | 4 | 0 |
| LoopWithTooManyJumpStatements | 1 | 0 |
| MaxLineLength | 14 | 0 |
| TooGenericExceptionCaught | 8 | 0 |
| TooManyFunctions | 7 | 0 |
| Unused strings removed | — | 67 |
| CompileSdk | 36 | 37 |

## Dependency Updates

| Dependency | Before | After |
|---|---|---|
| kotlinx-serialization | 1.8.0 | **1.11.0** |
| kotlinx-coroutines | 1.10.1 | **1.11.0** |
| androidx-core-ktx | 1.17.0 | **1.19.0** |
| Koin | 4.0.2 | **4.2.1** |
| Ktor | 3.1.0 | **3.5.0** |
| MockK | 1.13.14 | **1.14.9** |
| MapLibre SDK | 11.13.0 | **13.2.0** |
| compileSdk / targetSdk | 36 | **37** |

## Code Decompositions
- **7 composable functions decomposed**: `MapScreen`, `LoginScreen` (→ `LoginForm`, `LoginAppLogo`, `LoginCredentialsForm`, `LoginSignInButton`), `FeatureValidationModal` (→ `FeatureModalHeader`, `FeatureModalCoordinateInfo`, `FeatureModalValidationErrors`, `FeatureModalSaveButton`), `ProfileMenu` (→ `ProfileAvatar`, `ProfileMenuContent`, `ProfileMenuCompactInfo`, `ProfileMenuSettingsItem`, `ProfileMenuLogoutItem`), `SettingsScreen` (→ `SettingsAppearanceContent`, `SettingsAboutContent`, `SettingsLogoutButton`), `CompactInfoPanel` (→ `CompactInfoHeader`, `CompactPhaseCounts`), `TileControl` (→ `TileLayerDropdown`), `PhaseBadge` (→ `PhaseBadgeColors`, `PhaseBadgeContent`, `PhaseCountDot`)
- **`SnappingEngine.snapPoint`** extracted into 4 geometry-specific handlers (`snapToPoint`, `snapToLineString`, `snapToPolygon`, `snapToCircle`)

## Bug Fixes
- **InfoPanel display**: `phase.label.take(3)` → `Phases.getDisplayLabel(phase, context)` — was showing truncated labels
- **All `catch (e: Exception)`** narrowed or suppressed with proper logging

## Remaining Nice-to-Haves (P3)
- Upgrade Kotlin to **2.4.0** — blocked until detekt supports it (PR #9218 in progress)
- Upgrade Coil to **3.x** — group ID changed to `io.coil-kt.coil3`, needs `build.gradle.kts` update
- Enable **R8/ProGuard** `minifyEnabled`
- Add **monochrome adaptive launcher icon**
- Update `SecurePreferences` to use the newest `androidx.security` non-deprecated API (currently suppressed)
