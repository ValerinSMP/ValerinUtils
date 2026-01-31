# ValerinUtils

ValerinUtils es el plugin de utilidad central diseñado para **ValerinSMP**, proporcionando herramientas esenciales de gestión del servidor, mecánicas personalizadas y expansiones internas de API.

## Módulos

El plugin opera con una arquitectura modular definida en `config.yml`. Cada módulo puede ser habilitado o deshabilitado independientemente.

### KillRewards

Maneja la distribución de recompensas de PvP con mecanismos anti-abuso configurables.

**Configuración:**

- `cooldown-per-victim`: Segundos antes de que un jugador pueda recibir recompensa nuevamente por matar a la misma víctima.
- `same-ip-check`: Previene recompensas si el asesino y la víctima comparten IP.
- `daily-limit`: Máximo de recompensas por jugador por día.
- `min-playtime-minutes`: Tiempo de juego requerido para que la víctima otorgue recompensa.
- `min-kdr`: KDR mínimo requerido para la víctima.

### JoinQuit

Gestiona mensajes personalizados de entrada y salida, con soporte para permisos y modos silenciosos para staff.

**Permisos:**

- `valerinutils.join.vip`: Muestra el mensaje de entrada VIP definido en la configuración.
- `valerinutils.join.silent`: Suprime mensajes de entrada/salida (útil para staff).

### Vote40

Se integra con Votifier/NuVotifier para manejar recompensas de votos específicamente para la lista de servidores 40servidoresMC.

**Características:**

- Delay configurable para la ejecución de comandos.
- Validación específica del servicio.

### TikTok

Módulo promocional que permite a los jugadores reclamar una recompensa única por apoyar al servidor en TikTok.

**Comandos:**
| Comando | Descripción | Permiso |
|---------|-------------|---------|
| `/tiktok` | Reclama la recompensa definida. | `valerinutils.tiktok` |

### MenuItem

Utilidad para ejecutar comandos a través de items en el inventario, típicamente usado en plugins de menús.

**Comandos:**
| Comando | Descripción | Alias |
|---------|-------------|-------|
| `/menuitem` | Alterna el estado del item de menú. | `/menu` |

**Placeholders:**

- `%valerinutils_menuitem_enabled%`: Devuelve `true` o `false` basado en el estado del jugador.

### ExternalPlaceholders

Expansión interna para PlaceholderAPI que expone datos del plugin y ganchos (hooks) a otros plugins.

**Placeholders Disponibles:**
| Placeholder | Descripción |
|-------------|-------------|
| `%valerinutils_player_number%` | Total de entradas únicas (basado en rastreo interno). |
| `%valerinutils_total_players%` | Igual que el anterior. |
| `%valerinutils_first_join_date%` | Fecha del primer ingreso del jugador (DD/MM/YYYY HH:mm). |

## Comandos Generales

| Comando         | Uso                    | Permiso              | Descripción                          |
| --------------- | ---------------------- | -------------------- | ------------------------------------ |
| `/valerinutils` | `/valerinutils reload` | `valerinutils.admin` | Recarga la configuración del plugin. |

## Instalación

1. Asegúrate de tener **Java 21** o superior instalado.
2. Compila el proyecto usando Maven:
   ```bash
   mvn clean package
   ```
3. Localiza el jar en el directorio `target/`.
4. Coloca `ValerinUtils-1.0-SNAPSHOT.jar` en la carpeta `plugins/` del servidor.
5. Reinicia el servidor.

## Dependencias

- **Dependencias Fuertes**: Ninguna (Standalone).
- **Dependencias Suaves**:
  - PlaceholderAPI
  - Votifier / VotifierPlus
  - RoyaleEconomy (para hooks específicos de placeholders)

## Estado de Compilación

Este proyecto usa Maven para la gestión de dependencias y compilación.

- **Group ID**: `me.mtynnn`
- **Artifact ID**: `ValerinUtils`
- **Version**: `1.0-SNAPSHOT`
