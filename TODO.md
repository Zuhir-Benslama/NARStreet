# TODO — Code Quality Improvements

## Fixed

### Code Quality
- **`NarsGeoman` constructor: 8 params → 6** — grouped 3 callbacks into `FeatureCallbacks` data class; removed unused `map` param
- **`NarsGeoman` CoroutineScope** — now injected via constructor (shared with `GeomanEventHandler`)
- **`ApiService.login()` extracted** — cookie/token parsing and user creation moved to private methods; inner catch narrowed from `Exception` to `SerializationException`
- **`@Suppress("DEPRECATION")` on `NarsGeoman`** — removed (class-level suppress no longer needed)
- **`@Suppress("DEPRECATION")` on `SecurePreferences`** — updated `MasterKey.Builder` API (removed alias param); kept scoped suppress for library-level deprecation
- **`FeatureStore` interface extracted** — `FeatureStoreInterface` created, consumers updated to use it for testability
- **detekt config** — raised `TooManyFunctions` interface limit from 11 to 15 to match class limit
- **Spotless + ktlint configured** — enforces Kotlin formatting across `app/src/**/*.kt`; integrated with `spotlessCheck`/`spotlessApply`
- **Fixed detekt issues** — `LongMethod` & `MaxLineLength` violations resolved (extraction, line breaks, targeted `@Suppress`)
- **KDoc added** — to `NarsGeoman` factory, `FeatureCallbacks`, `FeatureStoreInterface`, `ApiService.login()`

### New Files Created
- `data/store/FeatureStoreInterface.kt` — interface for `FeatureStore`
- `modes/NarsGeoman.kt` — added `FeatureCallbacks` data class
