# TODO — Code Quality Issues

## Dependency Updates
- **Gradle** `9.5.1` → `9.6.1` (`gradle-wrapper.properties:3`)
- **maplibre-sdk** `13.3.0` → `13.3.1` (`libs.versions.toml:26`)
- **ktor** `3.5.0` → `3.5.1` (`libs.versions.toml:32`)
- **spotless** `8.7.0` → `8.8.0` (`libs.versions.toml:41`)

## Use Timber Instead of Log
- `NarsLogger.kt:32,41,50,58,65,72` — 6 calls using `android.util.Log` instead of `Timber`

## Composable Naming
- `LoginScreen.kt:262` — `LoginFieldColors()` returns a value; should start with lowercase

## Unused Resources
- `colors.xml` — `R.color.primary`
- `ic_launcher.xml`, `ic_launcher_round.xml` — mipmap launcher icons
- `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` — drawable launcher icons
