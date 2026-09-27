# WearJelly

Standalone Jellyfin music player for Wear OS, including TicWatch Pro X devices without GMS. This project contains no Google Play services, Firebase, Play Billing, Wearable Play API or Google account integration.

Development and installation instructions will be completed alongside verified release artifacts.

## Navigation flow

Navigation is a custom back stack in `AppViewModel` (`AppScreen` sealed interface) — no navigation library, so IDE nav-graph tooling does not apply to this project.

```mermaid
graph TD
    Login -->|login success| Home
    Home -->|artists / albums / all songs| Library
    Home -->|my cache| Downloads
    Home -->|play history| History
    Home -->|settings| Settings
    Library -->|open album / artist songs| Library
    Library -->|song detail (long-press)| Track
    Library -->|tap song plays directly| Player
    Library -->|scope multi-select| ScopeActions
    Track -->|play| Player
    Player -->|queue| Queue
    Player -->|lyrics| Lyrics
    Downloads -->|tap plays| Player
    Downloads -->|long-press| Track
    History -->|tap plays| Player
    History -->|long-press| Track
    Settings -->|logout| Login
```

`Queue`, `Lyrics` and `ScopeActions` are leaf screens (back only). A generic `Confirm` screen handles destructive-action confirmations.

