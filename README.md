# ValerinUtils

**ValerinUtils** es el plugin de utilidades núcleo diseñado específicamente para **ValerinSMP**. Proporciona una suite de herramientas esenciales, mejoras de calidad de vida y mecánicas personalizadas para potenciar la experiencia del servidor.

---

## 🚀 Características Principales

El plugin funciona mediante un sistema modular eficiente. Cada característica puede ser activada o desactivada independientemente desde `config.yml`.

### ⚔️ KillRewards

Sistema avanzado de recompensas por PvP con protecciones anti-abuso robustas.

- **Recompensas Configurables**: Ejecuta comandos o da dinero al matar jugadores.
- **Anti-Farm**: Evita el abuso mediante cooldowns por víctima y límites diarios.
- **Checks de Seguridad**:
  - Detección de misma IP.
  - Requisito de tiempo de juego mínimo para la víctima.
  - Requisito de KDR mínimo.

### 🎮 JoinQuit

Gestión de mensajes de entrada y salida personalizados.

- Mensajes vip y default separados.
- Integración completa con **PlaceholderAPI**.
- Ocultación de mensajes de entrada/salida silenciosa para staff.

### 🗳️ Vote40

Integración ligera para recompensas de votación.

- Listener para Votifier/NuVotifier.
- Ejecución de comandos con delay configurable.
- Soporte para servicios específicos (ej. 40servidoresMC).

### 📱 TikTok

Comando promocional `/tiktok`.

- Transmite mensajes clickeables y efectos visuales a todos los jugadores.
- Ideal para fomentar la creación de contenido en la comunidad.

### 📋 MenuItem

Utilidad para ejecutar comandos a través de items en menús.

- Facilita la creación de guís interactivos.

### 🧩 ExternalPlaceholders

Expansión interna de placeholders.

- Provee variables personalizadas para ser usadas en otros plugins (tablist, chat, scoreboards).

---

## 🛠️ Instalación y Compilación

### Requisitos

- **Java**: JDK 21 o superior.
- **Maven**: 3.8.0 o superior.
- **Servidor**: PaperMC, Purpur o derivado (1.20.4+).

### Compilar desde el Código Fuente

1. Clona el repositorio:

   ```bash
   git clone https://github.com/ValerinSMP/ValerinUtils.git
   cd ValerinUtils
   ```

2. Compila con Maven:

   ```bash
   mvn clean package
   ```

3. El plugin compilado estará en la carpeta `target/`:
   - `ValerinUtils-1.0-SNAPSHOT.jar`

---

## ⚙️ Configuración

El archivo `config.yml` se generará automáticamente en el primer inicio.

```yaml
debug: false # Activar para ver logs detallados en consola

modules:
  killrewards:
    enabled: true
    anti-abuse:
      same-ip-check: true
      cooldown-per-victim: 3600
  joinquit:
    enabled: true
  # ... otros módulos
```

---

## 🤝 Contribución

Este es un proyecto privado para **ValerinSMP**.

- **Reportar Bugs**: Usar el issue tracker del repositorio.
- **Pull Requests**: Bienvenidos para mejoras pequeñas o correcciones.

---

Desarrollado con ❤️ para **ValerinSMP**.
