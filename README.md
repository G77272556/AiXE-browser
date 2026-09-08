# AiXE browser

Prosta przeglądarka na Androida (Kotlin + Jetpack Compose + WebView).

## Struktura projektu

```
AiXE-browser/
├── settings.gradle.kts          # deklaracja modułów projektu
├── build.gradle.kts             # wersje pluginów (root)
├── gradle.properties            # ustawienia JVM/Gradle
├── .github/workflows/           # budowanie APK przez GitHub Actions
└── app/
    ├── build.gradle.kts         # zależności i konfiguracja modułu aplikacji
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/superbrowser/App.kt   # CAŁY kod aplikacji (jeden plik)
        ├── assets/adblock_list.txt        # reguły adblocka (format EasyList: ||domena^)
        └── res/                           # ikony, motyw, teksty, reguły backupu
```

## Budowanie

**Przez GitHub Actions (zalecane, bez instalowania czegokolwiek lokalnie):**
1. Wypchnij repo na GitHub.
2. Zakładka *Actions* → workflow "Android build" → *Run workflow* (albo poczekaj na automatyczne
   uruchomienie po pushu do brancha `main`).
3. Po zakończeniu, w wynikach uruchomienia (*Artifacts*) pobierz `aixe-browser-debug-apk`.
4. Rozpakuj `.zip`, zainstaluj `app-debug.apk` na telefonie.

**Lokalnie (jeśli masz Android Studio):**
Otwórz folder projektu w Android Studio — samo IDE wygeneruje brakujący Gradle Wrapper przy
pierwszej synchronizacji, więc `./gradlew assembleDebug` zadziała od razu potem. Ten projekt
świadomie NIE zawiera już gotowego wrappera (bo wymaga pobrania binarnego pliku `gradle-wrapper.jar`
z internetu) — workflow w GitHub Actions omija ten problem, instalując Gradle bezpośrednio przez
`gradle/actions/setup-gradle` i wołając `gradle assembleDebug` (bez `./gradlew`).

## Wymagania

- minSdk 26 (Android 8.0) — działa też na Androidzie 10 i nowszych.
- targetSdk / compileSdk 34.
- Kotlin 1.9.24, Compose Compiler 1.5.14 (muszą być ze sobą zgodne — patrz `app/build.gradle.kts`).

## Aktualizacja reguł adblocka

Podmień zawartość `app/src/main/assets/adblock_list.txt` — parser w kodzie (`AdBlockRuleParser`)
rozumie standardowy format EasyList (`||domena.com^`), więc możesz wkleić tam pełną, aktualną
listę np. z https://easylist.to/easylist/easylist.txt bez zmian w kodzie.
