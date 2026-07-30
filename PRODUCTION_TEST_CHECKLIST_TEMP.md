# ValerinUtils — checklist temporal de pruebas en producción

> Archivo temporal. Eliminarlo cuando la versión sea aceptada.
>
> Versión objetivo: **ValerinUtils 1.0.0**, Paper **1.21.11+**, Java **21**.

## Cómo usar esta checklist

- Probar primero con el servidor en mantenimiento o whitelist.
- Utilizar un jugador normal y otro con permisos administrativos.
- Marcar cada caso con `[x]` y anotar cualquier incidencia con hora, jugador,
  mundo, comando y fragmento relevante del log.
- No probar `/clear`, `/sell`, `/condense`, vouchers, códigos ni recompensas con
  objetos o saldos que no se puedan restaurar.
- No aprobar la versión si aparece una pérdida o duplicación de objetos, dinero,
  recompensas o datos.

## 0. Preparación y rollback

- [ ] Confirmar que el servidor ejecuta Paper 1.21.11 o posterior y Java 21.
- [ ] Guardar copia del JAR anterior de ValerinUtils.
- [ ] Guardar copia completa de `plugins/ValerinUtils/`.
- [ ] Guardar copia de `ValerinUtils.db`, incluidos archivos `-wal` y `-shm` si existen.
- [ ] Confirmar espacio libre en disco y permisos de escritura.
- [ ] Tener preparado el rollback: apagar, restaurar JAR, configuraciones y base
      de datos, y volver a iniciar.
- [ ] Confirmar las integraciones instaladas que se utilizarán:
      PlaceholderAPI, LuckPerms, Vault, ExcellentEconomy, WorldEdit y WorldGuard.
- [ ] Confirmar que ExcellentEconomy está registrado como proveedor de Vault.
- [ ] Guardar una copia del log desde el inicio hasta el final de las pruebas.

## 1. Arranque, configuración y apagado

- [ ] El plugin carga como `ValerinUtils v1.0.0`.
- [ ] No aparecen `ERROR`, `SEVERE`, excepciones ni stack traces relacionados.
- [ ] El log muestra plataforma Paper 1.21.11+ y conexión SQLite.
- [ ] Todos los módulos configurados con `enabled: true` se activan una sola vez.
- [ ] Los módulos con `enabled: false` no registran comportamiento.
- [ ] Los archivos faltantes se generan sin sobrescribir personalizaciones existentes.
- [ ] Las claves nuevas se incorporan a configuraciones antiguas.
- [ ] `/vutilsadmin reload all` funciona sin duplicar listeners, tareas o mensajes.
- [ ] `/vutilsadmin reload <módulo>` recarga únicamente el módulo solicitado.
- [ ] Repetir el reload cinco veces no produce dobles recompensas ni dobles mensajes.
- [ ] Apagar el servidor cierra SQLite limpiamente y sin excepciones.
- [ ] Reiniciar conserva las configuraciones y datos modificados durante la prueba.

## 2. Comandos raíz, ayuda y permisos

- [ ] `/valerinutils`, `/vutils` y `/vu` muestran la ayuda pública.
- [ ] La ayuda muestra páginas, contador y botones `Anterior`/`Siguiente`.
- [ ] Los botones de navegación abren la página correcta.
- [ ] Cada comando tiene hover con uso y descripción.
- [ ] Click en una entrada sugiere el comando correcto sin ejecutarlo accidentalmente.
- [ ] La ayuda contiene todas las familias de comandos declaradas por ValerinUtils.
- [ ] Un administrador ve también reload, debug, vouchers y DeathSpawn.
- [ ] `/valerinutils help <página>` muestra la página solicitada.
- [ ] Una página inexistente muestra error rojo y el rango válido.
- [ ] `/valerinutils` solo autocompleta `help` y `about`.
- [ ] `/valerinutilsadmin` autocompleta únicamente opciones administrativas.
- [ ] Ninguna opción sugerida bajo `/valerinutils` termina silenciosamente en help.
- [ ] `/valerinutils about` muestra nombre, versión y enlace correcto.
- [ ] La ayuda tiene líneas en blanco, colores, emojis, hover y acciones click.
- [ ] `/valerinutilsadmin` muestra la ayuda administrativa.
- [ ] `/vutilsadmin` y `/vuadmin` funcionan como aliases.
- [ ] Un jugador sin `valerinutils.admin` no puede ejecutar comandos administrativos.
- [ ] Un administrador puede usar `reload`, `debug` y `deathspawn`.
- [ ] El autocompletado solo muestra opciones permitidas para el usuario.
- [ ] Los comandos incompletos muestran uso claro y no lanzan errores.
- [ ] Los comandos ejecutados desde consola responden correctamente cuando aplica.

## 3. Activación individual de módulos

Repetir para `menuitem`, `killrewards`, `codes`, `deathspawn`, `itemsign`,
`utility`, `vouchers` y `grace`:

- [ ] Cambiar `enabled: false` y recargar el módulo.
- [ ] Confirmar que listeners, comandos y tareas del módulo dejan de actuar.
- [ ] Confirmar que otros módulos continúan funcionando.
- [ ] Cambiar `enabled: true` y recargar.
- [ ] Confirmar que el módulo vuelve una sola vez, sin comportamiento duplicado.
- [ ] Reiniciar y confirmar que conserva el estado elegido.

## 4. DeathSpawn y WorldGuard

### Configuración desde comandos

- [ ] WorldEdit y WorldGuard cargan antes que ValerinUtils.
- [ ] Crear cuatro regiones independientes para los cuatro KOTH.
- [ ] Pararse en el respawn del primer KOTH.
- [ ] Escribir `/vutilsadmin deathspawn set koth_1 ` y comprobar que el
      autocompletado muestra regiones del mundo actual.
- [ ] Ejecutar `/vutilsadmin deathspawn set koth_1 <región>`.
- [ ] Repetir el proceso para `koth_2`, `koth_3` y `koth_4`.
- [ ] `/vutilsadmin deathspawn list` muestra las cuatro reglas y su estado.
- [ ] Las coordenadas, mundo, yaw y pitch quedan guardados correctamente en
      `modules/deathspawn.yml`.
- [ ] `/vutilsadmin deathspawn disable <id>` desactiva la regla inmediatamente.
- [ ] `/vutilsadmin deathspawn enable <id>` vuelve a activarla.
- [ ] `/vutilsadmin deathspawn remove <id>` elimina una regla de prueba.
- [ ] Volver a crear la regla eliminada mediante `set`.
- [ ] Un ID inválido es rechazado sin modificar el YAML.
- [ ] Una región inexistente es rechazada sin modificar el YAML.
- [ ] Ejecutar `set` desde consola informa que debe realizarse dentro del juego.
- [ ] Reiniciar y confirmar que las reglas siguen configuradas.

### Respawn real

Repetir en cada KOTH:

- [ ] Morir en el centro de la región lleva al respawn configurado de ese KOTH.
- [ ] El mundo, coordenadas y orientación del respawn son correctos.
- [ ] No se envía al jugador al servidor global de spawn.
- [ ] No hace falta ejecutar `/back`.
- [ ] Morir cerca de cada borde, pero dentro de la región, usa la regla correcta.
- [ ] Morir un bloque fuera de la región no usa el respawn del KOTH.
- [ ] Morir fuera de todas las regiones conserva el comportamiento normal.
- [ ] Morir en otro mundo no usa una regla del mundo KOTH.
- [ ] Desactivar una regla hace que deje de aplicarse.
- [ ] Reactivarla hace que vuelva a aplicarse.
- [ ] Si existen regiones superpuestas, gana la primera regla configurada.
- [ ] Otro plugin de spawn/respawn no sobrescribe la ubicación elegida.
- [ ] Desconectarse durante la pantalla de muerte no deja datos residuales.
- [ ] Repetir muertes rápidas no cruza respawns entre jugadores.
- [ ] Probar dos jugadores muriendo simultáneamente en regiones distintas.
- [ ] No aparecen errores al recargar WorldGuard o ValerinUtils.

## 5. MenuItem — prioridad alta

### Entrega y protección del slot

- [ ] `/menuitem on`, `/menuitem off` y `/menuitem toggle` funcionan.
- [ ] El objeto aparece únicamente en el slot configurado.
- [ ] `/menu` funciona como alias.
- [ ] El objeto contiene la etiqueta PDC de ValerinUtils.
- [ ] Un objeto normal colocado en ese slot nunca se reemplaza ni se elimina.
- [ ] Activar MenuItem con el slot ocupado muestra el mensaje correspondiente.
- [ ] Desactivar MenuItem elimina solamente el objeto etiquetado por el plugin.
- [ ] Colocar un objeto normal en el slot después de desactivar MenuItem no hace
      que desaparezca unos segundos más tarde.
- [ ] Repetir `on/off/toggle` rápidamente no elimina objetos normales.
- [ ] Entrar y salir rápidamente no ejecuta tareas retrasadas sobre una sesión vieja.
- [ ] Reconectar con el slot ocupado no reemplaza el objeto normal.
- [ ] Cambiar entre mundos permitidos y deshabilitados conserva los objetos normales.
- [ ] Morir y reaparecer no crea copias del MenuItem ni borra otros objetos.
- [ ] Inventario lleno no provoca pérdida, duplicación o drops inesperados.

### Uso y comandos condicionales

- [ ] Click derecho ejecuta el comando base configurado.
- [ ] El cooldown bloquea usos repetidos y muestra el tiempo correcto.
- [ ] Cuando el placeholder cumple la condición se ejecuta el comando alternativo.
- [ ] Cuando no la cumple se ejecuta el comando base.
- [ ] Probar `equals`, `not_equals`, `contains`, `not_contains`, `regex`,
      `truthy`, `falsy`, `resolved` y `unresolved` si se usan en producción.
- [ ] PlaceholderAPI ausente o placeholder sin resolver no rompe el click.
- [ ] Solo se ejecuta la primera regla coincidente.
- [ ] `%player%` se reemplaza por el jugador correcto.
- [ ] Nombre, lore, CustomModelData y sonido son correctos.

## 6. KillRewards

- [ ] Matar a otro jugador entrega exactamente la recompensa configurada.
- [ ] La recompensa de dinero llega mediante Vault/ExcellentEconomy.
- [ ] XP, comandos u objetos configurados se entregan una sola vez.
- [ ] Una muerte ambiental no entrega recompensa PvP.
- [ ] Suicidio o muerte propia no entrega recompensa.
- [ ] Team kill es rechazado cuando corresponde.
- [ ] Mundos incluidos y `disabled-worlds` se respetan.
- [ ] Jugadores sin permisos o condiciones necesarias no reciben recompensa.
- [ ] Inventario lleno no causa duplicación ni pérdida silenciosa.
- [ ] Dos muertes rápidas no repiten una recompensa anterior.
- [ ] Reload y reinicio no duplican listeners de muerte.

## 7. Codes

- [ ] Código inexistente muestra error sin modificar datos.
- [ ] Código válido entrega exactamente las recompensas configuradas.
- [ ] El mismo jugador no puede reclamar dos veces un código de un solo uso.
- [ ] Mayúsculas, minúsculas y espacios se comportan como se haya definido.
- [ ] Dos intentos rápidos del mismo jugador no duplican recompensas.
- [ ] Dos jugadores pueden usar un código compartido cuando está permitido.
- [ ] Reiniciar conserva los códigos reclamados.
- [ ] Reload conserva el estado de reclamación.
- [ ] Fallo en una recompensa no marca silenciosamente una entrega incompleta como correcta.
- [ ] Objetos, dinero y comandos de recompensa corresponden al código usado.

## 8. Grace

- [ ] Un jugador nuevo recibe el tiempo de gracia configurado.
- [ ] `/grace check [jugador]` muestra tiempo coherente.
- [ ] `/grace add <jugador> <horas>` suma el tiempo correcto.
- [ ] `/grace remove <jugador>` retira la protección.
- [ ] `/grace list` muestra únicamente jugadores correspondientes.
- [ ] Durante la gracia se bloquean las interacciones PvP previstas.
- [ ] Al expirar se permite PvP sin requerir reinicio.
- [ ] Reiniciar conserva el tiempo restante.
- [ ] El tiempo de juego o método configurado no avanza incorrectamente offline.
- [ ] Atacante y víctima reciben mensajes correctos, sin spam duplicado.
- [ ] Un usuario sin permisos no puede administrar gracia ajena.

## 9. ItemSign

- [ ] `/sign <texto>` requiere `valerinutils.itemsign.use`.
- [ ] Firmar agrega autor y dedicatoria sin destruir nombre, lore, encantamientos,
      atributos, CustomModelData ni PDC ajeno.
- [ ] No se puede firmar una mano vacía.
- [ ] Probar texto corto, largo y caracteres especiales.
- [ ] El texto no permite inyectar MiniMessage no autorizado.
- [ ] `/itemsign remove` elimina la firma esperada.
- [ ] Eliminar por número o nombre selecciona la firma correcta.
- [ ] `valerinutils.itemsign.admin` permite administrar firmas según lo previsto.
- [ ] Firmar y quitar una firma no cambia la cantidad del stack.
- [ ] Probar con un objeto Nexo/custom importante usando una copia desechable.

## 10. Vouchers

- [ ] `/voucher give <jugador> <tipo> [cantidad]` valida jugador, tipo y cantidad.
- [ ] `/voucher reload` actualiza tipos sin duplicar listeners.
- [ ] El voucher tiene nombre, lore, material y PDC correctos.
- [ ] Canjear entrega exactamente una recompensa.
- [ ] Doble click o spam no canjea el mismo voucher dos veces.
- [ ] Un objeto visualmente idéntico sin PDC no puede canjearse.
- [ ] Stack de vouchers reduce exactamente una unidad por canje.
- [ ] Inventario lleno y jugador desconectado se manejan sin pérdida.
- [ ] Reinicio y reload no invalidan vouchers ya emitidos.
- [ ] Tipos inválidos no crean objetos incompletos.

## 11. Comandos Utility

Comprobar función, permisos, objetivo opcional, consola cuando aplique y
autocompletado:

- [ ] `/craft`, `/workbench` y `/wv`.
- [ ] `/anvil`.
- [ ] `/smithingtable` y `/st`.
- [ ] `/cartographytable` y `/ct`.
- [ ] `/grindstone`.
- [ ] `/loom`.
- [ ] `/stonecutter`.
- [ ] `/disposal`, `/trash` y `/basurero`.
- [ ] `/gmc`, `/gms`, `/gmsp` y `/gma`.
- [ ] `/hat`.
- [ ] `/condense` con inventario desechable y cantidades exactas.
- [ ] `/seen` para jugador online, offline y desconocido.
- [ ] `/clear [jugador]` únicamente con inventarios de prueba.
- [ ] `/ping [jugador]`.
- [ ] `/fly [jugador]`.
- [ ] `/speed <velocidad> [jugador]`, incluidos límites inválidos.
- [ ] `/broadcast`, `/bc`, `/anuncio` y `/vubroadcast`.
- [ ] `/helpop` y `/ayudaop`, con y sin staff conectado.
- [ ] `/heal [jugador]`.
- [ ] `/feed [jugador]`.
- [ ] `/repair`, `/fix` y `/repair hand`.
- [ ] `/nick <apodo|off>`, con formato permitido y reinicio.
- [ ] `/skull [jugador]`.
- [ ] `/suicide`.
- [ ] `/near [radio]`, incluidos mundos diferentes.
- [ ] `/ptime <day|night|reset|ticks>`.
- [ ] `/pweather <clear|rain|reset>`.
- [ ] `/sell hand` con cantidad y saldo exactos.
- [ ] `/sell inventory` con objetos vendibles y no vendibles mezclados.
- [ ] `/vtop` en terreno normal, Nether, vacío y techo.

Pruebas destructivas adicionales:

- [ ] Disposal elimina únicamente objetos dejados dentro al cerrar.
- [ ] Cerrar/reabrir Disposal no devuelve ni duplica objetos eliminados.
- [ ] `/sell` nunca vende MenuItem, vouchers u objetos custom no configurados.
- [ ] `/condense` conserva el valor total y los sobrantes exactos.
- [ ] `/clear` no afecta a otro jugador por coincidencia parcial de nombre.

## 12. Base de datos y persistencia

- [ ] Entrar carga los datos del jugador sin bloquear el hilo principal.
- [ ] Salir guarda y retira al jugador de la caché.
- [ ] Reconectar inmediatamente conserva datos.
- [ ] Reiniciar conserva códigos, gracia y demás estado persistente.
- [ ] Apagado con jugadores conectados guarda todos los datos.
- [ ] No aparecen errores `database locked`.
- [ ] SQLite permanece en WAL y cierra correctamente.
- [ ] Probar nombres de jugador cambiados y UUID sin pérdida de datos.
- [ ] La base no crece de forma anormal durante una sesión breve.
- [ ] No se registran datos sensibles ni contenido completo de inventarios.

## 13. Sistemas eliminados

- [ ] No existen comandos, listeners, configuraciones activas ni tablas nuevas de Geodes.
- [ ] No existe el sistema de Kits antiguo.
- [ ] No existe ItemEditor.
- [ ] No existe VUSpawn.
- [ ] No hay hooks, clases cargadas ni mensajes de RoyaleEconomy.
- [ ] La economía utilizada es ExcellentEconomy mediante Vault.
- [ ] PlugManX no aparece como dependencia ni integración.
- [ ] No se recomienda ni prueba reload mediante PlugManX.

## 14. Rendimiento y estabilidad

- [ ] Observar MSPT/TPS con la cantidad normal de jugadores.
- [ ] Realizar varias muertes simultáneas durante un KOTH.
- [ ] DeathSpawn consulta WorldGuard únicamente durante muerte/respawn.
- [ ] No aparecen tareas repetitivas nuevas relacionadas con DeathSpawn.
- [ ] MenuItem no crea tareas acumuladas al entrar, salir o cambiar de mundo.
- [ ] No aumenta continuamente el número de listeners después de reloads.
- [ ] No aumenta continuamente el uso de memoria por jugadores desconectados.
- [ ] Revisar un perfil breve con spark durante KOTH y uso normal.
- [ ] Confirmar ausencia de spam de debug con debug desactivado.

## 15. Criterios de aprobación

La versión solo se aprueba si:

- [ ] No hubo pérdidas ni duplicaciones.
- [ ] No hubo saldos o recompensas incorrectas.
- [ ] No hubo excepciones de ValerinUtils.
- [ ] Los cuatro KOTH reaparecen localmente en su región correcta.
- [ ] MenuItem nunca borró ni reemplazó un objeto normal.
- [ ] Todos los módulos pueden desactivarse y reactivarse limpiamente.
- [ ] Permisos y comandos administrativos están restringidos correctamente.
- [ ] Reload y reinicio conservan estado sin duplicar comportamiento.
- [ ] El rendimiento permanece dentro del nivel anterior al despliegue.
- [ ] El rollback fue comprobado o permanece listo para aplicarse.

## Registro de incidencias

| Hora | Jugador | Módulo/comando | Resultado observado | Resultado esperado | Evidencia |
| --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |

## Resultado final

- Responsable:
- Fecha:
- Build/JAR probado:
- Paper:
- Java:
- Resultado: `APROBADO / RECHAZADO`
- Incidencias bloqueantes:
- Observaciones:
