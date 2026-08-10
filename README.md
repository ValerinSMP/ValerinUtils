<div align="center">

# ValerinUtils

### Utilidades y sistemas survival para ValerinSMP

[![Paper](https://img.shields.io/badge/Paper-1.21.11%2B-222222?style=for-the-badge)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-E76F00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Version](https://img.shields.io/badge/version-1.1.1-7B5CFA?style=for-the-badge)](https://github.com/ValerinSMP/ValerinUtils)

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
- **Crimson Protection:** minería selectiva por regiones de WorldGuard y bloques Nexo.

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
- ExcellentEconomy 2.8.0, mediante Vault y su evento de cambios de saldo
- MythicMobs
- WorldGuard, para respawns locales por región
- Nexo 1.26.0, para identificar bloques permitidos en Crimson Protection

## Crimson Protection

La sección `crimson-protection` de `settings.yml` limita rotura y colocación
únicamente en los mundos configurados y donde la flag de WorldGuard
`valerin-crimson-protection` esté en `ALLOW`. Dentro de esas regiones solo se
pueden romper los IDs Nexo incluidos en `allowed-break-ids`; el permiso de bypass
predeterminado es `valerinutils.crimsonprotection.bypass`.

Si Nexo no está disponible, la rotura se deniega de forma segura únicamente
dentro de ese mundo y región activa; fuera de ese alcance no cambia eventos. La
API verificada es Nexo 1.26.0. El smoke con JARs reales de servidor queda pendiente.

## Ingresos de economía

`%valerinutils_earnings_money%` y `%valerinutils_earnings_shards%` conservan el
acumulado histórico de aumentos positivos en las monedas `money` y `shards` de
ExcellentEconomy. Ambos devuelven enteros sin formato, truncando decimales como
en el contrato anterior. Los cambios cancelados, retiros y otras monedas no suman.

ExcellentEconomy es opcional: si falta, los totales ya persistidos siguen
disponibles, pero no se registran ingresos nuevos hasta que el proveedor vuelva.
ValerinUtils conserva bytecode Java 21; un servidor que cargue ExcellentEconomy
2.8.0 debe cumplir por separado el requisito Java 25 de ese proveedor.

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
