# MG AFK Android

MG AFK Android is a lightweight mobile app that lets you stay connected to
Magic Garden without launching the game. It keeps a session open to display
pet ability logs, shop inventory, weather and more while minimizing battery
usage. You can also interact directly from the app: plant seeds and grow
eggs, water and harvest your garden, buy items from shops, chat with other
players, feed and swap your pets, sell crops and pets, lock items, play
casino mini-games, and browse public rooms.

## How it works

MG AFK connects to the game's WebSocket endpoint and authenticates using
your Discord account. Incoming data is parsed and displayed across dedicated
sections (dashboard, pets, garden, shops, alerts, mini-games…).

## Login

Tap **Login with Discord** in the Dashboard. A browser window opens on
Discord's OAuth page — log in and the app captures your session token
automatically. The token is stored persistently so you only need to log in
once.

To log out, tap **Logout**. This clears the stored token.

## Navigation

Swipe from the left edge or tap the hamburger menu to open the navigation
drawer. Sections:

| Section     | Content                                                                         |
|-------------|---------------------------------------------------------------------------------|
| Dashboard   | Connection setup + live status                                                  |
| Room        | Chat with players in the room                                                   |
| Pets        | Active pets, pet teams, ability logs                                            |
| Storage     | Seed silo, inventory (crops, plants, pets, tools, decors), feeding trough, pet hutch |
| Garden      | Plant seeds, water, harvest, pot plants, grow/hatch eggs                        |
| Shops       | Buy seeds / tools / eggs / decors (single, bulk, hybrid modes) + autobuy        |
| Social      | Browse and join public rooms                                                    |
| Mini Games  | Coin Flip, Mines, Slots, Dice, Crash, Blackjack, Egg Hatcher (casino wallet, deposit / withdraw) |
| Alerts      | Notification config (shops, weather, pets, feeding trough)                      |
| Settings    | Background & battery, reconnection, purchase mode, developer options            |
| Debug       | WebSocket logs, service logs, alert testing                                     |

Sections that require an active connection are greyed out when offline.
The Debug section is hidden by default and can be enabled in Settings.

## Multiple accounts

MG AFK supports multiple sessions. Use the tabs bar to add a new account (+)
and switch between sessions. Each tab keeps its own login, room code, and
reconnect settings.

## Background & lock screen

The app runs in the background even when the phone is locked using a
foreground service. A Wi-Fi lock keeps the network active and an optional
CPU wake lock (off, smart, or always) prevents the system from sleeping
during long AFK sessions. The smart mode automatically enables the CPU lock
after the phone has been locked for a configurable delay and releases it on
unlock.

If the WebSocket disconnects, the app retries indefinitely with exponential
backoff and reconnects immediately when the network comes back. An optional
notification can alert you when a session loses connection.

## Pets

The Pets section shows your active pets with hunger bars, STR stats, and
ability badges with dynamic colors from the game data. Tap a pet to feed it
from your produce, swap it with another pet from inventory/hutch, or remove
it. Empty slots can be filled by equipping a pet directly.

**Pet Teams** let you save and switch between up to 30 pre-configured teams
of 3 pets. One tap activates a team, instantly swapping your active pets.

**Ability logs** show a detailed description for each proc (e.g. "Snail
found 25552 coins", "Turtle reduced 1 plant growth by 4m 54s") alongside a
colored ability badge matching the game UI.

Every pet and crop sprite across the app renders with its current mutations
baked in (Gold, Rainbow, Wet, Frozen, Ambershine, Dawnlit, etc.) — no more
plain icons; you see at a glance what each pet or produce looks like.

## Garden

The Garden section mirrors your in-game plot. You can:
- Plant seeds onto free tiles.
- Water growing plants to speed up growth.
- Harvest mature crops from single-slot and multi-slot plants.
- Pot a grown plant back into your inventory using Planter Pots.
- Grow and hatch eggs into pets.

Multi-slot plants (Moonbinder, trees, Camellia…) are rendered like in the
game: the full plant illustration with each crop placed at the correct slot
position, sized by its `targetScale` and composited with its own mutations.

## Storage

Inventory, feeding trough, seed silo, pet hutch, and decor shed — each
category shows its contents with rarity borders and quantity badges.

- **Sell pets**: sell individual pets with a price preview, or bulk-sell
  all unlocked pets.
- **Sell crops**: sell individual produce, or bulk-sell all unlocked crops.
  Prices include the friends-bonus (×1.0 to ×1.5 based on room size).
- **Lock / unlock items**: tap any item to open its detail popup, then
  toggle the lock to protect it from being sold.
- **Multi-slot plants**: the detail dialog shows each crop slot with its
  growth bar, mutations, and individual sell price, plus a Plant in Garden
  button.

## Shops

Three purchase modes are available (configurable in Settings):
- **Hybrid** (default): tap to buy x1, long-press to buy all remaining stock.
- **Single**: tap always buys x1.
- **Bulk**: tap buys all remaining stock at once.

**Autobuy** lets you queue specific items to be purchased automatically on
each shop restock.

## Social

Browse public rooms fetched from the Aries Mod API. Each entry shows the
host's avatar and the room's player count — tap **Join** to switch rooms
instantly.

## Mini Games

A full casino with a dedicated wallet:
- **Deposit**: transfer in-game breads into the casino (amount capped by
  game-side limits).
- **Withdraw**: the casino sends breads back to your in-game account via
  the `/doughnate` command. Since the game refuses a doughnate that would
  push your balance past **2,000,000**, the app refreshes your in-game
  balance on every withdraw action and caps the withdraw amount at
  `min(casinoBalance, 2,000,000 − gameBalance)` so you never lose coins to
  a failed transfer.

Games available: **Coin Flip, Mines, Slots, Dice, Crash, Blackjack, Egg
Hatcher**. The Egg Hatcher animates the hatch with per-pet mutation (Gold,
Rainbow) composed directly on the sprite.

## Alerts

MG AFK can notify you about shop restocks, weather changes, low pet hunger,
and feeding trough levels. Configure thresholds for pet hunger and feeding
trough in the Alerts section. Alerts work in the background and when the
phone is locked.

## Sprite cache

Sprites are loaded from the Magic Garden API and cached locally via Coil —
256 MB of persistent disk cache survives app restarts, so composed mutation
sprites aren't re-fetched between sessions.

## Build

Prerequisites:
- Android Studio (latest stable)
- JDK 17+
- Android SDK 35

Open the project in Android Studio and run on a device/emulator, or build
from the command line:

```bash
./gradlew assembleDebug
```

The debug APK will be in `app/build/outputs/apk/debug/`.

## Credits

- WebSocket message parsing and actions are based on [MG-Websocket-Helper](https://github.com/Ariedam64/MG-Websocket-Helper).
- Sprites, composed mutation sprites and game data are fetched from the unofficial game API: [Magic-Garden-API](https://github.com/Ariedam64/Magic-Garden-API).
- Public room browsing uses the [Aries Mod API](https://github.com/Ariedam64/mg-api-front-ariesMod).

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- OkHttp (WebSocket)
- Kotlinx Serialization
- Coil (sprite loading + persistent image cache)
- DataStore (persistent settings)
