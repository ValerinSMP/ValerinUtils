<div align="center">

# ValerinUtils

### Utilidades y sistemas survival para ValerinSMP

[![Paper](https://img.shields.io/badge/Paper-1.21.11%2B-222222?style=for-the-badge)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-E76F00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Version](https://img.shields.io/badge/version-1.0.0-7B5CFA?style=for-the-badge)](https://github.com/ValerinSMP/ValerinUtils)

</div>

**ValerinUtils** reúne las utilidades, comandos de calidad de vida y sistemas
personalizados del survival de ValerinSMP. Cada módulo se configura y se puede
desactivar por separado desde su propio archivo YAML.

## ⭐ Módulos

- **Menu Item:** acceso rápido al menú, con comandos condicionales por PlaceholderAPI.
- **Kill Rewards:** recompensas configurables por combate.
- **Codes:** códigos reclamables con recompensas.
- **Death Spawn:** respawn por mundo, condiciones o región de WorldGuard.
- **Item Sign:** firma de objetos con autor y dedicatoria.
- **Utility:** comandos generales para jugadores y administración.
- **Grace:** protección PvP temporal para jugadores nuevos.
- **Vouchers:** objetos canjeables configurables.

Los sistemas antiguos Geodes, Kits, Item Editor y VUSpawn ya no forman parte del
plugin.

## 🧰 Utilidades destacadas

- Mesas de trabajo virtuales: crafting, yunque, herrería, telar y más.
- Comandos de vuelo, velocidad, curación, alimentación y gamemode.
- Venta y condensación de inventarios.
- Nicknames, cabezas, ping, `/seen`, clima y tiempo personal.
- Broadcasts, helpop y herramientas administrativas.
- Mensajes con Adventure/MiniMessage y PlaceholderAPI.

## 🎮 Administración

| Comando | Descripción |
| --- | --- |
| `/valerinutils` | Ayuda pública e información del plugin. |
| `/valerinutilsadmin reload [all\|módulo]` | Recarga configuración o módulos. |
| `/valerinutilsadmin debug <módulo>` | Controla el debug de un módulo. |
| `/valerinutilsadmin deathspawn` | Configura respawns por región desde el juego. |
| `/grace` | Administra la protección PvP inicial. |
| `/voucher` | Entrega o recarga vouchers. |

El listado completo de comandos y permisos está en
[`src/main/resources/plugin.yml`](src/main/resources/plugin.yml).

## ⚙️ Configuración

- `settings.yml`: opciones y mensajes globales.
- `debug.yml`: debug por módulo.
- `sellprice.yml`: precios usados por `/sell`.
- `modules/*.yml`: estado, comportamiento y mensajes de cada módulo.

## 🧩 Requisitos

| Paper | Java requerida | Folia |
| :---: | :---: | :---: |
| 1.21.11 en adelante | 21 | ❌ |

Integraciones opcionales:

- PlaceholderAPI
- LuckPerms
- Vault
- ExcellentEconomy, mediante Vault
- MythicMobs
- WorldGuard, para respawns locales por región

## 🛠️ Compilación

```bash
./gradlew clean test build
```

El artefacto se genera dentro de `build/libs/`.

## Versionado

La versión **1.0.0** establece el baseline público de ValerinUtils. A partir de
este estado se aplica [Versionado Semántico](https://semver.org/lang/es/):

- **PATCH** para correcciones compatibles.
- **MINOR** para funcionalidades nuevas compatibles.
- **MAJOR** para cambios incompatibles.
