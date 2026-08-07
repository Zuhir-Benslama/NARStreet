# NARStreet

Android client for the NARS mapping system. Built with Jetpack Compose, MapLibre, and Geoman.

## Features

- 3-phase field mapping pipeline (roads, house entrances, naming panels)
- Drawing, editing, and validation of geographic features
- Session-based feature store with server sync (features live in memory)
- Phase-based workflow with validation gates
- mTLS support for secure API communication
- Encrypted credential storage via Android Keystore

## Prerequisites

- Android Studio Meerkat (2025.1.x) or later
- JDK 21+
- Android SDK 37
- Gradle 9.6+ (wrapped)
- NARS backend running and accessible

## Setup

1. **Clone the repo** and open in Android Studio.

2. **Configure API URL** — copy `local.properties.example` to `local.properties` and set your backend URL:

   ```properties
   NARS_API_BASE_URL=http://10.0.2.2:8080
   ```

   Default emulator gateway `10.0.2.2` maps to host `localhost`. For a physical device, use your machine's LAN IP.

3. **Build**:

   ```bash
   ./gradlew assembleDebug
   ```

4. **Install** on connected device:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Project Structure

```
app/src/main/java/com/nars/maplibre/
├── data/
│   ├── api/            # API client, auth, feature CRUD
│   ├── model/          # NarsFeature, User, Phases, Geometry types
│   ├── store/          # Offline feature store with undo
│   └── repository/     # ApiModels, FeatureRepository
├── modes/              # Geoman integration, rendering, snapping
├── security/           # EncryptedSharedPreferences wrapper
├── ui/
│   ├── components/     # NarsMap, CompactInfoPanel, FeatureValidationModal
│   ├── screens/        # LoginScreen, MapScreen, MapScreenHandlers
│   └── theme/          # Material3 theme, GlassBackground
├── utils/              # Config, NarsLogger, TlsUtils, Validation
├── AppPreferences.kt   # SharedPreferences + encrypted prefs
├── NarsApplication.kt  # App entry point, DI
└── NarsViewModel.kt    # Main ViewModel
```

## API Endpoints

The app communicates with the NARS backend via these endpoints:

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/api/signin` | No | Login |
| POST | `/api/refresh` | Cookie | Rotate JWT |
| POST | `/api/logout` | Bearer | Logout |
| GET | `/api/features` | Bearer | Load features (paginated) |
| POST | `/api/features` | Bearer | Save new feature |
| PUT | `/api/features/{id}` | Bearer | Update feature |
| DELETE | `/api/features/{id}` | Bearer | Delete feature |

## Security

- Auth tokens stored in `EncryptedSharedPreferences` backed by Android Keystore (AES-256 GCM)
- `android:allowBackup="false"` prevents credential extraction via ADB backup
- Release variant network security config disables cleartext HTTP
- Optional TLS certificate pinning via the `SSL_CERT_HASHES` build config (disabled when blank)
- JWT access tokens are transparently refreshed on HTTP 401 via `/api/refresh` (refresh is lazy, not performed at app startup)
- No API keys, secrets, or credentials committed to version control

## Build Variants

| Variant | Minify | Debuggable | Cleartext |
|---------|--------|------------|-----------|
| debug | No | Yes | Allowed |
| release | Yes (ProGuard) | No | Blocked |

## Dependencies

- **MapLibre Android SDK** — Map rendering
- **Geoman** — Drawing and editing gestures (local module)
- **Jetpack Compose** — UI framework
- **Kotlinx Serialization** — JSON parsing
- **AndroidX Security Crypto** — Encrypted prefs

## License

GNU General Public License v3.0 — See [LICENSE](../LICENSE) for details.
