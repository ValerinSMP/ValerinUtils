<div align="center">

# ValerinUtils

### Utilidades y sistemas survival configurables para ValerinSMP

[![Version](https://img.shields.io/badge/version-1.1.2-7B5CFA?style=for-the-badge)](https://github.com/ValerinSMP/ValerinUtils)
[![Paper](https://img.shields.io/badge/Paper-1.21.11%2B-222222?style=for-the-badge)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-E76F00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)

[Repositorio](https://github.com/ValerinSMP/ValerinUtils) · [Comandos y permisos](src/main/resources/plugin.yml) · [Reportar un problema](https://github.com/ValerinSMP/ValerinUtils/issues)

</div>

ValerinUtils reúne comandos de calidad de vida, administración y sistemas survival
en un único plugin modular para Paper.

## ⭐ Features

⭐ **Utilidades para jugadores y staff** — mesas virtuales, vuelo, velocidad,
curación, alimentación, venta, condensación, nicknames, clima y más.

⭐ **Sistemas survival configurables** — Menu Item, Kill Rewards, Codes, Grace,
Vouchers, Item Sign y Death Spawn.

⭐ **Crimson Protection** — minería selectiva por regiones de WorldGuard e IDs de
bloques Nexo, con bypass administrativo y cierre seguro si Nexo no está disponible.

⭐ **Respawns por región** — Death Spawn admite reglas por mundo y regiones de
WorldGuard configurables desde el juego.

⭐ **Estadísticas de economía** — placeholders acumulados para ingresos `money` y
`shards` recibidos mediante ExcellentEconomy.

⭐ **Mensajes modernos** — Adventure/MiniMessage, PlaceholderAPI y ayudas
interactivas con hover y click.

Los sistemas antiguos Geodes, Kits, Item Editor y VUSpawn ya no forman parte del
plugin.

## Compatibilidad

| Componente | Requisito |
| --- | --- |
| Servidor | Paper 1.21.11 o posterior |
| Java de ValerinUtils | Java 21 |
| Java con ExcellentEconomy 2.8.0 | Java 25 |
| Folia | No compatible |

Integraciones opcionales:

| Plugin | Uso |
| --- | --- |
| PlaceholderAPI | Condiciones, mensajes y placeholders públicos |
| LuckPerms | Grupos y prefijos de jugadores |
| Vault | Proveedor de economía para utilidades como `/sell` |
| ExcellentEconomy 2.8.0 | Economía y eventos de ingresos `money`/`shards` |
| MythicMobs | Recompensas configurables |
| WorldGuard | Death Spawn y regiones de Crimson Protection |
| Nexo 1.26.0 | Identificación de bloques de Crimson Protection |

## Setup

1. Instala Paper 1.21.11 o posterior con la versión de Java indicada arriba.
2. Copia `ValerinUtils-1.1.2.jar` en la carpeta `plugins/` del servidor.
3. Instala únicamente las integraciones opcionales que vayas a utilizar.
4. Inicia el servidor una vez para generar la configuración.
5. Ajusta `settings.yml`, `sellprice.yml` y los archivos de `modules/`.
6. Reinicia el servidor o ejecuta `/valerinutilsadmin reload` para cambios
   compatibles con recarga.

### Configuración

| Archivo | Contenido |
| --- | --- |
| `settings.yml` | Opciones, mensajes globales y Crimson Protection |
| `debug.yml` | Debug por módulo |
| `sellprice.yml` | Precios de `/sell` |
| `modules/*.yml` | Estado, comportamiento y mensajes de cada módulo |

Cada módulo tradicional dispone de su propio `enabled` en YAML.

### Crimson Protection

La sección `crimson-protection` de `settings.yml` actúa únicamente en mundos
configurados y donde la flag `valerin-crimson-protection` de WorldGuard resuelva
`ALLOW`. Dentro de esas regiones solo permite romper IDs incluidos en
`allowed-break-ids`; el bypass predeterminado es
`valerinutils.crimsonprotection.bypass`.

Si Nexo no está disponible, la rotura se deniega solo dentro de ese alcance. Fuera
de esos mundos y regiones, ValerinUtils no modifica el evento. La API verificada es
Nexo 1.26.0.

### Placeholders de economía

- `%valerinutils_earnings_money%`
- `%valerinutils_earnings_shards%`

Ambos muestran el acumulado histórico de aumentos positivos en `money` y `shards`,
no el saldo actual. Devuelven enteros sin formato y conservan los totales persistidos
aunque ExcellentEconomy no esté disponible. Cambios cancelados, retiros y otras
monedas no suman; las transferencias entre jugadores mediante `/pay` tampoco.

ExcellentEconomy es opcional, pero su versión 2.8.0 requiere que el servidor se
ejecute con Java 25. ValerinUtils conserva bytecode Java 21. La exclusión de
transferencias está verificada contra la ruta interna de ExcellentEconomy 2.8.0 y
debe reauditarse antes de admitir otra versión.

## Comandos

| Comando | Uso |
| --- | --- |
| `/valerinutils help [página]` | Ayuda pública interactiva |
| `/valerinutils about` | Información del plugin |
| `/valerinutilsadmin reload [all\|módulo]` | Recarga configuración o módulos |
| `/valerinutilsadmin debug <módulo>` | Controla el debug de un módulo |
| `/valerinutilsadmin deathspawn` | Administra respawns por región |
| `/grace` | Administra la protección PvP inicial |
| `/voucher` | Entrega o recarga vouchers |

El catálogo completo y sus permisos están en
[`src/main/resources/plugin.yml`](src/main/resources/plugin.yml).

## Desarrollo

Requiere JDK 21. El Wrapper incluido ejecuta las pruebas y genera el JAR:

```bash
./gradlew clean test build
```

En Windows también puede usarse `gradlew.bat`. El artefacto se genera en
`build/libs/`.

El proyecto sigue [Versionado Semántico](https://semver.org/lang/es/): PATCH para
correcciones compatibles, MINOR para nuevas funciones compatibles y MAJOR para
cambios incompatibles.

## Licencia y enlaces

Este repositorio no incluye actualmente un archivo de licencia.

- [Código fuente](https://github.com/ValerinSMP/ValerinUtils)
- [Issues](https://github.com/ValerinSMP/ValerinUtils/issues)
- [ValerinSMP](https://github.com/ValerinSMP)
