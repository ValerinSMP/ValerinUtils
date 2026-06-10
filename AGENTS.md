# AGENTS.md — ValerinUtils

## Build & Run

```bash
mvn clean package     # output: target/ValerinUtils-2.0.16.jar
```

- Java 21 required (`maven-compiler-plugin` release=21).
- No tests, no linter, no formatter configured.

## plugin.yml: the real one

Root `/plugin.yml` is **outdated**. The canonical file is `src/main/resources/plugin.yml` — it's the one included in the JAR. Root copy is missing the `sign`/`itemsign` commands and the permissions block.

## Architecture

**Module-based plugin** for Paper 1.21.8 (`api-version: "1.21"`).

```
ValerinUtils (main class, ~1280 lines)
  └─ owns: ConfigManager, MessageService, CommandRegistry, DatabaseManager, ModuleManager
  └─ each module is a field, not accessed through a registry
  └─ implements Listener for player join/quit (PlayerData cache)
```

**Module hierarchy:** `Module` (interface) → `AbstractModule` (config/msg helpers) → `BaseModule` (final enable/disable + listener/command tracking cleanup).

- `BaseModule.enable()` and `.disable()` are **final** — modules override `onEnableModule()` and optionally `onDisableModule()`.
- Disable automatically unbinds all module-owned commands and unregisters all listeners tracked via `registerListener()`.
- Modules live under `me.mtynnn.valerinutils.modules.<name>/` (10 modules).

### Startup order (onEnable)

1. ConfigManager loads/merges/migrates all configs
2. MessageService, CommandRegistry, DatabaseManager initialized
3. Each module instantiated + registered via `moduleManager.registerModule(...)`
4. `moduleManager.enableAll()` — checks each module's own config `enabled` key
5. PlaceholderAPI expansion registered (if available)

### Disable order (onDisable)

1. Save all cached PlayerData to SQLite (`CompletableFuture.runAsync`)
2. `moduleManager.disableAll()`
3. DatabaseManager closes the SQLite connection

## Config system (ConfigManager, ~1233 lines)

- 13 config files registered: `settings.yml`, `debug.yml`, `sellprice.yml`, plus 10 modules under `modules/`.
- Config has **code-based versioning** — each module has an `update*Config()` method that adds missing keys with defaults. Adding a new YAML key also requires adding it in the corresponding update method.
- Legacy `config.yml` (pre-2.0) is auto-migrated to per-module files on first load.
- `&a` → MiniMessage conversion runs on first load. Kits `display_name` paths are **skipped** from this conversion.
- `settings.messages.aesthetic-theme-enabled` rewrites standard MiniMessage colors to branded hex palette in-place on every load.
- `sellprice.yml` has **duplicate entries** (both `iron_ingot` and `ironingot` stripped variants) — search for material by uppercase name, not by stripping underscores.

## Message system

- **Adventure API / MiniMessage** for all player-facing text.
- `MessageService` centralizes resolution: `msg(key)` → module's `messages.<key>`, or `settings(key)` → global `messages.<key>`.
- `%prefix%` is a **universal placeholder** replaced in every message, sourced from `settings.messages.prefix`.
- Legacy color codes (`&a`, `&#RRGGBB`, BungeeCord hex) are normalized to MiniMessage `<color:#XXX>` via static regex methods on the main plugin class.

## Database (SQLite)

- File: `plugins/ValerinUtils/ValerinUtils.db` (path hardcoded in DatabaseManager, not configurable).
- WAL journal mode, NORMAL synchronous.
- Tables: `player_data`, `player_votes`, `server_data`, `player_codes`.
- Schema migrations use `ALTER TABLE ADD COLUMN` wrapped in try-catch (catches "duplicate column" errors silently). Fragile — don't change column names or exception handling.
- Player data is cached in `ConcurrentHashMap<UUID, PlayerData>` in the main class. Loaded on `AsyncPlayerPreLoginEvent`, saved on `PlayerQuitEvent` via async CompletableFuture.

## PlayerData cache — memory leak hazard

- `ValerinUtils.playerData` is a `ConcurrentHashMap<UUID, PlayerData>`.
- Loaded asynchronously on `AsyncPlayerPreLoginEvent`. Removed on `PlayerQuitEvent`.
- Never iterates keys to prune stale entries. If a player crashes or the QuitEvent is missed, their entry stays forever. Any module that also caches Player objects should use `WeakReference` or clean up on QuitEvent.

## Command system

- `CommandRegistry` binds module-owned commands. On module disable/reload, all commands owned by that module are unbound (executor set to null, but command stays registered in Bukkit).
- `plugin.yml` declares **all** commands upfront (not dynamically registered).
- The main class has **~300 lines of reflection** (`purgeRegisteredCommands`, `reinstatePluginCommands`, `repairBrigadierDispatcher`) to support PlugManX-style hot-reload without server restart. This is fragile across Paper versions and should be treated as "don't touch unless broken."

## Module conventions

- Each module gets its own YAML config: `src/main/resources/modules/<id>.yml` with an `enabled: true/false` root key.
- Module messages live under `messages.*` in that YAML.
- Modules register commands via `registerCommand(name, executor)` and listeners via `registerListener(listener)`.
- Module constructors typically take only the plugin instance. Anything extra comes from config or database.

## Soft dependencies (all optional)

- PlaceholderAPI — expansion registered if `%valerinutils_*` placeholders needed
- LuckPerms — group checks in joinquit module
- Vault — economy integration
- RoyaleEconomy — alternative economy hook

## Code conventions

- **Language:** Spanish for log messages, comments, and default player-facing messages. Source code identifiers are English.
- Module classes that grow large (Kits module has 7 files, Utility has 10) are split into helper/handler classes within the same package, not sub-packages.
- No dependency injection framework — everything is wired manually in `ValerinUtils.onEnable()`.

## Utility scripts (not part of the plugin)

- `check_db.py` — inspects RoyaleEconomy SQLite DB structure. Dev tool only.
- `spigot-logging-config.yml` — reference config snippet to suppress command-log spam in `spigot.yml`. Drop-in config for server admins.
