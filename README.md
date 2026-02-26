# ValerinUtils

Plugin modular para Paper 1.21+ con utilidades de administración, QoL y sistemas personalizados para servidores survival.

## Módulos actuales

- `menuitem` (`src/main/resources/modules/menuitem.yml`)
- `joinquit` (`src/main/resources/modules/joinquit.yml`)
- `killrewards` (`src/main/resources/modules/killrewards.yml`)
- `codes` (`src/main/resources/modules/codes.yml`)
- `deathmessages` (`src/main/resources/modules/deathmessages.yml`)
- `geodes` (`src/main/resources/modules/geodes.yml`)
- `kits` (`src/main/resources/modules/kits.yml`)
- `utility` (`src/main/resources/modules/utilities.yml`)
- `pvpmina` (`src/main/resources/modules/pvpmina.yml`)
- `itemeditor` (`src/main/resources/modules/itemeditor.yml`)

> Los módulos `vote40` y `votetracking` fueron retirados.

## Configuración

- Global: `src/main/resources/settings.yml`
- Debug por módulo: `src/main/resources/debug.yml`
- Precios de venta (`/sell`): `src/main/resources/sellprice.yml`
- Descriptor Bukkit: `src/main/resources/plugin.yml`

Los mensajes se manejan por módulo en `messages.*` dentro de cada archivo del módulo.  
`settings.yml` queda para mensajes globales (`prefix`, permisos globales y comandos admin del plugin).

## Arquitectura interna

- `ModuleManager`: registro/habilitado/recarga de módulos.
- `BaseModule`: base común para comandos, listeners y debug.
- `ConfigManager`: carga, migraciones y autocompletado de claves faltantes.
- `MessageService`: resolución central de mensajes y `%prefix%`.
- `CommandRegistry`: registro/bindeo central de comandos.

## Comandos principales

- Admin: `/valerinutils reload`, `/valerinutils debug <modulo> [on|off|toggle]`
- MenuItem: `/menuitem <on|off|toggle>` (alias `/menu`)
- Utility: `/gmc`, `/gms`, `/gmsp`, `/gma`, `/broadcast`, `/nick`, `/seen`, `/sell`, etc.
- Kits: `/kits` (alias `/kit`, `/vukits`)
- ItemEditor: `/itemedit ...` (alias `/itemeditor`, `/iedit`)

Comandos y aliases completos: `src/main/resources/plugin.yml`.

## Permisos y Placeholders (por módulo)

> Placeholder interno común en todos los módulos de mensajes: `%prefix%`.

### Core / Admin

- **Permisos**
  - `valerinutils.admin`
- **PlaceholderAPI (expansión `valerinutils`)**
  - `%valerinutils_menuitem_enabled%`
  - `%valerinutils_deathmessages_enabled%`
  - `%valerinutils_player_number%`
  - `%valerinutils_total_players%`
  - `%valerinutils_first_join_date%`
  - `%valerinutils_votes_total%`
  - `%valerinutils_votes_daily%`
  - `%valerinutils_votes_monthly%`
  - `%valerinutils_votes_month_<mes>_[año]%`
  - `%valerinutils_votes_year_<año>%`
  - `%valerinutils_votes_quarter_<1-4>_[año]%`

### menuitem

- **Permisos**
  - `valerinutils.bypass.cooldown`
- **Placeholders internos (config)**
  - `%player%`, `%time%`

### joinquit

- **Permisos**
  - `groups.<grupo>.permission` (configurable por grupo en `joinquit.yml`)
  - fallback automático por grupo: `group.<grupo>`
- **Placeholders internos (config)**
  - `%player%`, `%player_name%`, `%player_number%`, `%online%`, `%max%`
  - soporta placeholders externos via PlaceholderAPI (ej: `%luckperms_prefix%`, `%server_online%`, `%vault_eco_balance%`)

### killrewards

- **Permisos**
  - Sin permisos propios obligatorios.
- **Placeholders internos (config)**
  - `%killer%`, `%victim%`, `%amount%`, `%percentage%`

### codes

- **Permisos**
  - Sin permisos propios obligatorios.
- **Placeholders internos (config)**
  - `%player%` (comandos de recompensa)
  - `%code%` (mensajes de éxito/error)

### deathmessages

- **Permisos**
  - `valerinutils.admin` (comando `/vuspawn`)
- **Placeholders internos (config)**
  - `%victim%`, `%attacker%`

### geodes

- **Permisos**
  - `valerinutils.geodes.admin`
- **Placeholders internos (config)**
  - `%player%`

### kits

- **Permisos**
  - `valerinutils.kits.admin`
  - `kits.<nombre_kit>` (o el permiso definido en `kits.<id>.required-permission`)
- **Placeholders internos (config)**
  - `%kit%`, `%time%`, `%items%`, `%days%`, `%perm%`, `%status%`
  - `%page%`, `%max_page%`, `%selected_kit%`, `%preset_slot%`
  - `%player%`, `%permission%`

### utility

- **Permisos base**
  - `valerinutils.utility.craft`
  - `valerinutils.utility.enderchest`
  - `valerinutils.utility.anvil`
  - `valerinutils.utility.smithing`
  - `valerinutils.utility.cartography`
  - `valerinutils.utility.grindstone`
  - `valerinutils.utility.loom`
  - `valerinutils.utility.stonecutter`
  - `valerinutils.utility.disposal`
  - `valerinutils.utility.hat`
  - `valerinutils.utility.condense`
  - `valerinutils.utility.clear`
  - `valerinutils.utility.ping`
  - `valerinutils.utility.fly`
  - `valerinutils.utility.speed`
  - `valerinutils.utility.broadcast`
  - `valerinutils.utility.heal`
  - `valerinutils.utility.feed`
  - `valerinutils.utility.repair`
  - `valerinutils.utility.nick`
  - `valerinutils.utility.skull`
  - `valerinutils.utility.suicide`
  - `valerinutils.utility.near`
  - `valerinutils.utility.top`
  - `valerinutils.utility.ptime`
  - `valerinutils.utility.pweather`
  - `valerinutils.utility.sell`
  - `valerinutils.utility.gamemode`
- **Permisos extra**
  - `/seen` actualmente no valida permiso base; sólo protege IP con `valerinutils.utility.seen.ip`
  - `valerinutils.utility.clear.others`
  - `valerinutils.utility.ping.others`
  - `valerinutils.utility.fly.others`
  - `valerinutils.utility.speed.others`
  - `valerinutils.utility.heal.others`
  - `valerinutils.utility.feed.others`
  - `valerinutils.utility.gamemode.others`
  - `valerinutils.utility.nick.others`
  - `valerinutils.utility.nick.color.basic`
  - `valerinutils.utility.nick.color.format`
  - `valerinutils.utility.nick.color.hex`
  - `valerinutils.utility.seen.ip`
- **Placeholders internos (config)**
  - `%player%`, `%players%`, `%message%`, `%mode%`, `%nick%`, `%tier%`, `%time%`
  - `%ping%`, `%state%`, `%type%`, `%speed%`, `%value%`, `%count%`, `%amount%`
  - `%radius%`, `%status%`, `%uuid%`, `%ip%`, `%first_join%`, `%last_seen%`
  - `%world%`, `%x%`, `%y%`, `%z%`, `%health%`, `%hunger%`, `%xp%`, `%fly%`, `%gamemode%`, `%items%`

### pvpmina

- **Permisos**
  - `valerinutils.pvpmina.use`
  - `valerinutils.pvpmina.admin`
  - `valerinutils.pvpmina.bypass`
- **Placeholders internos (config)**
  - `%mode%`, `%time_left%`

### itemeditor

- **Permisos**
  - `valerinutils.itemeditor.use`
- **Placeholders internos (config)**
  - `%prefix%`

## Dependencias suaves

- PlaceholderAPI
- LuckPerms
- Vault
- RoyaleEconomy

## Build

Requiere Java 21.

```bash
mvn clean package
```

El jar generado queda en `target/`.
