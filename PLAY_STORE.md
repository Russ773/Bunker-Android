# Google Play release checklist

## 1. Signing

1. Create an upload key (once, keep it and its passwords safe, never commit it):
   `keytool -genkeypair -v -keystore bunker-upload.jks -alias bunker -keyalg RSA -keysize 2048 -validity 10000`
2. Create `keystore.properties` next to `settings.gradle.kts` (it is git-ignored):
   ```
   storeFile=../bunker-upload.jks
   storePassword=...
   keyAlias=bunker
   keyPassword=...
   ```
3. `./gradlew :app:bundleRelease` -> `app/build/outputs/bundle/release/app-release.aab`. Enrol in Play App Signing.

## 2. Things Play will ask about

| Topic | Answer |
| --- | --- |
| **Package name** | `app.getbunker.rcon` (change in `app/build.gradle.kts` before the first upload if you prefer another; it cannot change later). |
| **Target / min SDK** | target 36, min 26 (Android 8). |
| **Foreground service** | Declared as `specialUse` (`ConnectionService`). Justification text is already in the manifest: it keeps the admin's own WebSocket (RCON) connection open while the screen is off. You must complete the *Foreground service permissions* declaration form and may be asked for a short demo video. A user-visible notification is always shown and it stops when you leave the server. |
| **Cleartext traffic** | Rust's WebRCON is plain `ws://`, so `network_security_config.xml` allows cleartext. Explain in review: connections go only to the server address the user typed in. |
| **Permissions** | `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE(_SPECIAL_USE)`. |
| **Data safety** | Collects: nothing. Shares: nothing. The app sends data only to the user's own game server and downloads the public Rust item list/pictures from GitHub. Passwords are stored encrypted on the device. No analytics, no ads, no accounts. Data deletion: uninstall the app (all data is local). |
| **Content rating** | Utility/tools. The app administers a game server but contains no game content. |
| **Privacy policy URL** | `https://getbunker.app/privacy.html`. The page now covers Android (Keystore, notifications, background connection, app lock), but the updated `privacy.html` in `Bunker-Website` must be **uploaded to the site** before you submit. |
| **App category** | Tools (or Productivity). |
| **Trademark** | Make sure the listing says it is not affiliated with Facepunch. The wordmark says "Rust server admin", which is descriptive, but avoid the Rust logo/art in screenshots. |

## 3. Store listing (draft)

**Name:** Bunker RCON (matches the Microsoft Store listing)

**Short description (80):** Admin your Rust server from your phone: console, players, chat and moderation.

**Full description:**
> Bunker is the admin console for your Rust server, now on your phone. Watch the live console, see who is online, read chat,
> and kick, ban, mute, teleport or give items to players with a tap.
>
> • Live console with search, filters and command history
> • Player list with ping, time online and health; tap a player for kick, ban, mute, teleport, give item and notes
> • Chat that looks like the game, with watch-word alerts, and talk back as yourself
> • Server health charts, quick actions, scheduler, shortcuts, ban list and player database
> • Several servers, auto-reconnect, background connection, app lock
> • Passwords encrypted on your phone. No accounts, no analytics.
>
> Needs WebRCON turned on for your server (+rcon.web 1). Bunker is an independent community tool and is not affiliated with or
> endorsed by Facepunch Studios. Rust is a trademark of Facepunch Studios.

**Assets already in the repo:** `store/play-store-icon-512.png` (the desktop logo, 512x512). Still needed: a 1024x500 feature
graphic and 4+ phone screenshots (take them on the emulator against `tools/mock-rcon-server.js`).

## 4. Before the first upload

* Test on a real device against a real Rust server (Carbon and Oxide), including the `bunker.say` chat plugin.
* Try Android 8, 13 (notification permission) and 14/15 (foreground service types) if possible.
* Add `versionCode`/`versionName` bumps for every upload (`app/build.gradle.kts`).
* Internal testing track first, then closed testing (new personal developer accounts need 12 testers for 14 days before production).

## 5. iOS later

The protocol layer (`rcon/`, `core/ServerSession`, parsers and their unit tests) is plain logic with no Android UI, which makes it
the part to port (Swift + `URLSessionWebSocketTask`, Keychain for passwords). The mock server in `tools/` works for iOS too
(simulator reaches the Mac/PC through its LAN address).
