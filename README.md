# Bunker for Android

The Bunker Rust server admin console, in your pocket. Native Android (Kotlin, Jetpack Compose, Material 3) that talks to
your server over **WebRCON** and nowhere else: no accounts, no analytics.

It shares its look (colours, logo, wordmark) and its behaviour (RCON commands, parsing, reconnect timings, chat handling,
console filters) with the [Windows app](../Bunker-Application). The widgets system is not part of the phone app yet.

## Features

| | |
| --- | --- |
| **Servers** | Save several servers, test a connection before saving, passwords encrypted with a key in the Android Keystore, optional `wss://`. |
| **Dashboard** | Players, FPS, entities, memory, uptime, map; player and FPS charts; save/announce/tools buttons; your shortcuts; who is online; recent joins and leaves. |
| **Console** | Live, colour-coded output with command history, search, Chat/Warning/Error filters, hide a whole plugin or any words, tap a line for actions, share the log. A Command/Chat switch, and "say this in chat?" when you type a sentence instead of a command. |
| **Players** | Search, sort (name, ping, time, health), pull to refresh. Tap anyone for: copy name or Steam ID, Steam profile, **kick, ban, mute, unmute, teleport to/from another player, give item (searchable, with pictures), private note, admin permissions** (moderator/owner). |
| **Chat** | Player chat with game name colours, `[TEAM]` and plugin tags such as `[VIP]`, watch-word highlighting, and a box to talk back as yourself (BunkerChat plugin) or as SERVER. |
| **More** | Quick actions (save world, time of day, restart countdown), Announce (saved messages), Server health charts (5 min to 2 h), Scheduler (intervals or daily, `wait N` steps), Shortcuts, Player database (notes, CSV export, join/leave feed), Ban list (unban, ban by Steam ID), Give items (to a player or everyone). |
| **Phone extras** | Stays connected with the screen off (quiet foreground notification), auto-reconnect, alerts for lost connection / chat watch-words / joins while the app is in the background, polling slows down in the background, app lock (fingerprint/face/screen lock), hide from recents & block screenshots, dark/light/system theme, console text size, keep screen on. |

## Build and run

You need Android Studio (or the Android SDK) and a JDK 17+. Open this folder in Android Studio and press Run, or:

```bash
./gradlew :app:installDebug          # build and install on a connected device or emulator
./gradlew :app:testDebugUnitTest     # unit tests (parsers, commands, schedules)
./gradlew :app:assembleRelease       # release APK (signed with the debug key unless keystore.properties exists)
./gradlew :app:bundleRelease         # the .aab for Google Play
```

> **Gradle crashing with `EXCEPTION_ACCESS_VIOLATION` in `jvm.dll`?** Some JDK builds crash the Gradle daemon in a GC thread on
> certain CPUs. `gradle.properties` already pins `-XX:+UseSerialGC` and a lower worker count, which fixed it here.

## Try it without a Rust server

```bash
node tools/mock-rcon-server.js            # port 28016, password "test"
node tools/mock-rcon-server.js --noplugin # pretend the BunkerChat plugin is missing
node tools/mock-rcon-server.js --fast     # busier console
```

It answers `serverinfo`, `playerlist`, `banlistex` and the common admin commands, sends console noise and chat, and makes
players come and go. From the Android emulator the host PC is **`10.0.2.2`**, so add a server with address `10.0.2.2`,
port `28016`, password `test`. A wrong password gets the socket closed immediately, like Rust does.

## How it is put together

```
app/src/main/java/app/getbunker/rcon/
├── rcon/   RconClient (OkHttp WebSocket + request/reply), Parsers (server info, players, chat, bans, filters, command guess)
├── core/   ServerSession (connect, reconnect, polling, scheduler, chat), PlayerTracker, ConsoleLog, ItemCatalog,
│           Notifier, ConnectionService (foreground service), AppContainer/SessionManager
├── data/   Models, SettingsStore (JSON in app storage), SecretBox (Keystore AES-GCM)
└── ui/     Compose screens: servers, session tabs, player actions, tools, lists, settings, theme
```

* One `ServerSession` lives while you are on a server. Screens only observe its `StateFlow`s, so rotating the phone or
  leaving the app never drops the connection.
* RCON replies to your commands are matched by `Identifier`; everything else is console output.
* The item list and pictures come from [bobosneefdev/rust-items-json](https://github.com/bobosneefdev/rust-items-json)
  (cached, refreshed at most daily, with a built-in fallback list).

## Known gaps / ideas

* No Multi Server overview yet (the desktop app has one) and no community widgets.
* Offline players can be banned/noted but not kicked, muted or teleported (same as desktop).
* Temporary bans are not offered (the desktop app does not send them either).
* Not yet tested against a live Rust server: the protocol code follows the desktop app and the mock server. Please try it on
  yours, and check `Carbon`/`Oxide` plugin-list tables (not ported yet).

See [PLAY_STORE.md](PLAY_STORE.md) for the release checklist.
