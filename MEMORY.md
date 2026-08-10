# ValerinUtils — memoria de desarrollo

Lee también `AGENTS.md` antes de modificar el repositorio.

## Rol

Plugin histórico que concentra utilidades y sistemas survival. No debe reescribirse
de una sola vez: cada módulo se aislará y migrará con un contrato verificable.

## Base actual

- Versión actual 1.1.1; el baseline SemVer fue 1.0.0.
- Desde este baseline se aplica SemVer: PATCH para correcciones compatibles,
  MINOR para funcionalidades compatibles y MAJOR para cambios incompatibles.
- Java 21, Gradle Kotlin DSL 9.1.0 y Paper API 1.21.11.
- Clase principal y `ConfigManager` excesivamente grandes.
- SQLite local y caché de datos de jugadores.
- Adventure/MiniMessage con `MessageService`.
- Hay pruebas unitarias para las condiciones de PlaceholderAPI.
- El descriptor canónico es `src/main/resources/plugin.yml`; el `plugin.yml` de la
  raíz está obsoleto.

## Riesgos conocidos

- Guardados asíncronos y ciclo de vida de la caché de jugadores.
- Migraciones SQL basadas en excepciones y ruta de base de datos fija.
- Los comandos se declaran estáticamente; se retiró la reflexión de PlugMan/CommandMap.
- Dependencias opcionales accedidas desde un plugin monolítico.
- Defaults y migraciones de configuración repartidos en código.
- MenuItem solo identifica objetos mediante su etiqueta PDC y nunca sobrescribe
  un slot ocupado por un objeto normal.
- Geodes, kits, itemeditor, VUSpawn y la integración RoyaleEconomy fueron retirados.
- DeathSpawn guarda la ubicación exacta de muerte y puede seleccionar una regla
  por mundo y región de WorldGuard. Cada región KOTH apunta directamente a unas
  coordenadas de respawn del mismo servidor; no usa placeholders ni comandos warp.
- WorldGuard es opcional. Las reglas con `worldguard-region` no coinciden cuando
  WorldGuard no está instalado; las reglas sin esa clave conservan el modo anterior.
- `/valerinutilsadmin deathspawn set <id> <region>` guarda la ubicación actual
  como respawn, con autocompletado de regiones. También permite listar, activar,
  desactivar y eliminar reglas sin editar el YAML.
- `/valerinutils help [página]` usa la lista configurable de `settings.yml`,
  muestra todos los comandos en páginas con hover/click y separa estrictamente
  el autocompletado público del administrativo.
- Paleta visual: primario `#FFD166`, éxito `#00FB9A`, error `#FF3300` y
  advertencia `#FFC43B`.

## Estado visual y compilación

- Las ayudas de subcomandos de Utility, MenuItem, Grace, ItemSign, Vouchers y
  DeathSpawn usan bloques Adventure con líneas en blanco, hover y click.
- Los mensajes nunca deben convertirse a texto legacy antes de enviarse si
  contienen eventos Adventure. Las pruebas recorren todos los mensajes YAML y
  generan todas las entradas reales de `/valerinutils help` para detectar
  MiniMessage inválido antes de publicar.
- Las migraciones `message-style-version: 2` reemplazan automáticamente solo la
  sección `messages` de cada módulo afectado. Antes crean junto al YAML una copia
  `*.yml.messages-v1.bak`; la configuración funcional permanece intacta.
- `settings.yml` usa `messages.help-style-version: 3`. La migración debe ejecutarse
  antes de fusionar claves faltantes para reparar instalaciones cuya sección
  `messages.help` fue serializada accidentalmente a legacy o quedó vacía.
- Las filas, navegación, hover y click de `/valerinutils help` se construyen con
  componentes Adventure, nunca concatenando etiquetas MiniMessage configurables.
  El YAML conserva las entradas, descripciones, permisos y tamaño de página.
- Toda ayuda interactiva de subcomandos usa `CommandHelpRenderer`. Está prohibido
  guardar etiquetas `<hover>` o clicks de comandos dentro de YAML: una prueba
  recorre los recursos y falla si vuelven a introducirse. Esto cubre Utility,
  MenuItem, Grace, Vouchers, ItemSign, DeathSpawn y la ayuda general.
- Todos los comandos de módulos deben declararse en `BaseModule#getCommandNames`
  y registrarse únicamente desde `onEnableModule`. `CommandRegistry` instala un
  executor inactivo sin tab completion cuando el módulo o comando está apagado.
  Nunca registrar comandos de módulos directamente desde la clase principal.
- `/valerinutils help` oculta módulos apagados y comandos Utility desactivados.
  Los nombres reales de switches agrupados son `smithing`, `cartography`,
  `gamemode`, `broadcast` y `top`, aunque sus comandos tengan otros nombres.
- En este equipo, Gradle puede sufrir bloqueos ZipFS si caché o salida están dentro
  de OneDrive. Para builds fiables usar `GRADLE_USER_HOME` y `VALERIN_BUILD_DIR`
  apuntando a carpetas temporales fuera de OneDrive.
- Producción usa UniverseSpigot, que puede invocar comandos desde
  `universe-command-thread`. `CommandRegistry` debe trasladar ejecución y
  autocompletado al hilo principal antes de acceder a Bukkit, configuraciones o
  ciclo de módulos. El estado compartido de módulos/configs usa colecciones
  concurrentes para garantizar visibilidad antes y después de un reload.
- Crimson Protection registra `valerin-crimson-protection` exclusivamente en
  `onLoad()`. WorldGuard y Nexo son opcionales; el listener se registra una vez y
  los reloads solo sustituyen un snapshot validado de `settings.yml`.
- Dentro de un mundo configurado y una región con la flag en `ALLOW`, solo se
  permite romper IDs Nexo configurados. Un lookup ausente o fallido se cierra
  de forma segura sin afectar mundos o regiones fuera de ese alcance.
- La única API Nexo compilada y verificada es 1.26.0. Falta el smoke de servidor
  con los JARs runtime reales de Nexo y WorldGuard; no asumir compatibilidad con
  otras versiones hasta ejecutar esa prueba.
- Los placeholders públicos `earnings_money` y `earnings_shards` son acumulados,
  no saldos. ExcellentEconomy 2.8.0 emite `ChangeBalanceEvent`; solo sus deltas
  positivos para IDs `money`/`shards` se persisten. La escritura SQLite ocurre
  antes de publicar en caché, y eventos async/offline convergen al hilo principal.
- ExcellentEconomy 2.8.0 está compilado para Java 25, mientras ValerinUtils
  conserva bytecode Java 21. La frontera registra el evento oficial de forma
  aislada por nombre y no expone tipos ExcellentEconomy fuera de ese adaptador;
  la dependencia Gradle sigue siendo `compileOnly` y nunca se empaqueta.

## Auditoría de inicio (2026-07-29)

- Rama `main`, commit base `d814c3a`; el worktree conserva una reforma extensa con
  40 cambios rastreados y numerosos archivos nuevos. No se descartó ni sobrescribió
  ningún cambio existente.
- La búsqueda en `src/main/resources` no encontró etiquetas MiniMessage de
  `hover`/`click`. `CommandHelpRenderer` cubre la ayuda general y los bloques
  interactivos de Utility, MenuItem, Grace, Vouchers, ItemSign y DeathSpawn.
- Hay 15 métodos `@Test` en el árbol actual, pero el build no llegó a ejecutarlos:
  `compileJava` falla de forma reproducible con `AccessDeniedException` sobre
  `antlr4-runtime-4.13.2.jar` en la caché temporal de Gradle. Una caché temporal
  nueva necesitó descargas y no completó dentro del límite aproximado de 60 segundos.
- No se generó ni copió un JAR nuevo. El artefacto existente no debe considerarse
  validado contra este estado del worktree.
- No apareció una tarea titulada exactamente `ValerinSMP — Principal`; por eso no
  se afirmó ni se realizó el reporte directo.

### HANDOFF PARA EL PRINCIPAL

Resultado: auditoría estática de mensajes correcta; build bloqueado antes de tests
por el bloqueo de caché Gradle descrito arriba. Archivos relevantes:
`CommandHelpRenderer.java`, sus siete consumidores y
`HelpConfigurationTest.java`. Decisión: no tocar la reforma activa ni intentar una
corrección funcional sin una regresión reproducible. Riesgo: falta validar las 15
pruebas y producir el JAR de la versión entonces vigente (2.0.16). Siguiente paso:
liberar/recrear la caché temporal
de Gradle y repetir `clean test build`; después ejecutar la prueba de producción.

## Bloqueo de colisión `/heal` (2026-07-30)

- ValerinUtils declara `heal` estáticamente en `src/main/resources/plugin.yml` y
  `UtilityModule` lo enlaza mediante `CommandRegistry`.
- El servidor de prueba local solo contiene plugins ValerinSMP y LuckPerms; ningún
  otro descriptor del workspace declara o usa `heal` como alias.
- No hay JAR, configuración ni log de producción local que identifique qué plugin
  posee actualmente la etiqueta corta `/heal`.
- No se añadió un `loadbefore` especulativo: solo resolvería la colisión si el rival
  se identifica por su nombre exacto y registra el comando durante la carga normal.
  No garantiza prioridad frente a un registro dinámico o una reasignación posterior.
- Para desbloquear la corrección se necesita confirmar que
  `/valerinutils:heal` ejecuta este plugin e identificar el dueño real de `/heal`,
  idealmente inspeccionando `knownCommands["heal"]` y el `PluginCommand#getPlugin()`
  asociado. Como evidencia mínima sirven la salida/captura de `/heal`, la lista
  completa de plugins y luego `/version <plugin-rival>`.
- Producción confirmó que `/valerinutils:heal` funciona y que PlugManX atribuye
  `/heal` a FastAsyncWorldEdit 2.15.3+1704422; la orden responde `Healed!`.
- El hash abreviado corresponde al commit oficial
  `17044229e4f3bd31c1033da2269ab237d69f4f4c` (release 2.15.3). Ese árbol no
  contiene un comando/alias `heal`, el mensaje `Healed!` ni configuración para
  aliases. FAWE carga en fase `STARTUP`, no declara comandos en `plugin.yml` y
  los registra dinámicamente mediante Bukkit `CommandMap`.
- El siguiente dato necesario es el JAR exacto de producción o su nombre y
  SHA-256. Puede tratarse de un JAR modificado o de una extensión que inyecta el
  comando en el dispatcher de FAWE. No modificar ValerinUtils ni añadir
  `loadbefore` hasta identificar ese origen.
- No se puede elevar de forma segura la prioridad con
  `loadbefore: [FastAsyncWorldEdit]`: FAWE usa `load: STARTUP` y
  `loadbefore: [WorldGuard]`, mientras ValerinUtils usa `softdepend: [WorldGuard]`.
  Esas tres relaciones formarían el ciclo
  `ValerinUtils -> FastAsyncWorldEdit -> WorldGuard -> ValerinUtils` y pueden
  impedir el arranque. Mover todo ValerinUtils a `STARTUP` también haría que sus
  integraciones opcionales todavía no estuvieran habilitadas.
- La forma nativa y determinista de reservar solo la etiqueta corta `/heal` es
  un alias global de `commands.yml` hacia `valerinutils:heal $1-`; requiere
  reinicio y no ofrece tab completion propio.
- Producción aplicó ese alias global y confirmó que `/heal` ya ejecuta
  ValerinUtils correctamente. La incidencia quedó resuelta sin modificar ni
  recompilar el plugin.

## Reforma

1. Añadir characterization tests antes de mover comportamiento.
2. Reducir la clase principal a composición y ciclo de vida.
3. Extraer un módulo de bajo riesgo cada vez.
4. Separar persistencia, dominio, entradas y presentación.
5. Añadir pruebas de caracterización para cada módulo antes de cambios funcionales.
6. Adoptar Gradle Kotlin DSL y Wrapper.
7. Elevar la API mínima a Paper 1.21.11 conservando bytecode Java 21.
8. Validar cada release en 1.21.11 y en la última versión estable de Paper.
