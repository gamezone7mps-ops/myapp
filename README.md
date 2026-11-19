# LifeOS (Android)

An offline-first personal life-management application built with Kotlin, Jetpack Compose, Material 3, Room, and DataStore.

## Core Modules

1. **Dashboard** — Daily overview, localized Jalali/Gregorian date, timezone context, and local vault metrics.
2. **Tasks** — Offline-first task management *(Phase 2)*.
3. **Calendar** — Day/Week/Month views with Jalali & Gregorian display and timezone-safe event storage *(Phase 2)*.
4. **Smart Memory** — Notes, ideas, links, tags, and local search *(Phase 2)*.
5. **Documents** — Local document & image vault *(Phase 3)*.
6. **Insights** — Computed productivity & workload summaries *(Phase 2)*.
7. **AI Assistant** — Controlled Gemini integration with preview-before-write confirmation *(Phase 4)*.
8. **Settings & Profile** — Instant Persian (RTL) / English (LTR) switching, Light/Dark/System theme, Jalali/Gregorian display toggle, and privacy controls *(Active in Phase 1)*.

## Architecture

- **UI Layer (`features/`)**: Jetpack Compose + Material 3 + `StateFlow` ViewModels + Adaptive `NavigationBar` / `NavigationRail`.
- **Domain Layer (`domain/`)**: Platform-agnostic models (UUID IDs, UTC epoch millis, `syncStatus`, `isDeleted`) and repository interfaces.
- **Core Infrastructure (`core/`)**:
  - `core/calendar`: Pure Kotlin, API-24-compatible Jalali ↔ Gregorian conversion engine (`PersianCalendarEngine`).
  - `core/database`: Versioned Room SQLite (`AppDatabase`, schema v1 exported to `app/schemas/`, explicit `ALL_MIGRATIONS`).
  - `core/datastore`: DataStore Preferences (`UserPreferencesRepository`).
  - `core/localization`: Dynamic RTL/LTR `LayoutDirection` and localized `Configuration` context (`ProvideLifeOsLocale`).
  - `core/designsystem`: Material 3 color schemes and locally bundled `Vazirmatn` + `Plus Jakarta Sans` fonts (`res/font/`).
- **Data Layer (`data/local/`)**: Room-backed repository implementations.

## Setup & Build

1. Open the root project in Android Studio (AGP 9.1.1+, JDK 17/21 with Java 11 bytecode target).
2. Ensure `ANDROID_SDK_ROOT` is configured (do not commit `local.properties` or `.env`).
3. Build from CLI or Android Studio:
   ```bash
   gradle :app:assembleDebug
   ```

## Testing

Run all JVM unit tests, Room database tests, and Robolectric Compose UI tests:
```bash
gradle :app:testDebugUnitTest
```
