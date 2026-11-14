# GoalsPL - Documentación (ES)

GoalsPL añade metas comunitarias a servidores Paper y Purpur. La administración crea campañas desde menús y los
jugadores las completan entre todos: derrotar criaturas, romper bloques o entregar materiales. Al llegar a ciertos
porcentajes se anuncian hitos y se entregan recompensas, y al completar una campaña puede empezar sola la siguiente.

English: [GoalsPL - Documentation EN](../en/GoalsPL%20-%20Documentation%20EN.md)

## Compatibilidad

| Servidor | Java | Estado |
|---|---|---|
| Paper / Purpur 1.21.4 a 1.21.11 | 21 | Compila y pasa las pruebas contra la API de cada versión |
| Paper / Purpur 26.1.1, 26.1.2, 26.2 y 26.3 | 25 | Compila y pasa las pruebas contra la API de cada versión |
| Spigot, CraftBukkit, Folia | | No compatible |

Un solo jar para todas las versiones. No usa NMS ni PacketEvents.

## Instalación

1. Copia `GoalsPL-<versión>.jar` en `plugins/` (borra antes cualquier versión anterior) y arranca el servidor. El
   servidor descarga solo el driver de SQLite la primera vez.
2. Opcional: EventsPL, para las recompensas de tipo `EVENT`.
3. Entra con permisos y usa `/goals admin`.

## Primeros pasos

Vienen tres campañas de ejemplo en borrador: `zombie_hunt` (criaturas), `mining_week` (bloques, que al completarse
inicia `harvest_donation`) y `harvest_donation` (entrega de trigo y zanahorias). Ninguna cuenta hasta que se inicia:
ábrelas en `/goals admin`, revísalas y pulsa «Iniciar», o usa `/goals start <id>`.

Para crear una nueva: `/goals admin`, «Crear campaña», escribe el ID en el chat y rellena el editor. Nada se guarda
hasta pulsar «Revisar y guardar», que enseña un resumen con los errores (impiden guardar) y los avisos.

## Conceptos

### Campañas y ejecuciones

Una campaña es la definición (qué cuenta, cuánto hace falta, recompensas). Cada vez que se inicia empieza una
ejecución nueva con el progreso a cero. Estados:

| Estado | Significado |
|---|---|
| Borrador | Creada o terminada; no cuenta |
| Activa | En curso; cuenta el progreso |
| Pausada | No cuenta y el reloj está parado |
| Completada | Llegó a la meta |
| Cancelada | Terminada a mano sin completar |
| Vencida | Se acabó el tiempo sin completar |

El ID de una campaña no cambia nunca: el progreso va unido a él, así que editar una campaña en curso lo conserva.

### Tipos de objetivo

| Tipo | Qué cuenta |
|---|---|
| Derrotar criaturas | Criaturas de la lista que mata un jugador |
| Romper bloques | Bloques de la lista que rompe un jugador |
| Entregar materiales | Objetos que los jugadores entregan desde un menú |
| Progreso administrativo | Solo `/goals progress` y la API |

Si la lista de objetivos está vacía cuenta cualquiera (salvo en las entregas, que necesitan al menos uno). Cada
campaña puede limitarse a unos mundos.

### Tiempo

La duración (`7d`, `12h`, `1d12h`) y la fecha límite (`2026-12-31 23:59`, en la zona horaria de `config.yml`) son
opcionales; si hay las dos, se aplica la que llegue antes. En pausa el reloj se para, pero la fecha límite no se mueve.

### Hitos y recompensas

- **Hitos comunitarios:** porcentajes del total (25, 50, 75, 100 o los que quieras). Cada uno tiene sus recompensas
  y a quién se entregan:
  - **Comunidad:** a quien aportó algo y a los jugadores conectados en ese momento.
  - **Participantes:** solo a quien llegó a la aportación mínima de la campaña.
- **Hitos personales:** recompensas para cada jugador al llegar su aportación a una cantidad.
- **Clasificación:** recompensas por puesto al completar la campaña.
- **Mínimo y máximo por jugador:** el mínimo decide quién recibe las recompensas de participantes; lo que pase del
  máximo no cuenta, para que unos pocos no acaparen la campaña. Estas reglas se ven en el menú antes de participar.

Cada hito y cada recompensa se entregan una sola vez por ejecución, también tras reinicios.

### Tipos de recompensa

Se escriben como `TIPO: valor`.

| Tipo | Valor | A quién |
|---|---|---|
| `COMMAND` | Comando de consola sin barra: `eco give {player} 100` | Cada jugador |
| `MESSAGE` | Texto MiniMessage | Cada jugador |
| `ITEM` | `MATERIAL cantidad`: `DIAMOND 3` | Cada jugador |
| `PERMISSION` | `permiso duración`: `essentials.fly 1h` | Cada jugador |
| `BROADCAST` | Texto MiniMessage para todo el servidor | Una vez |
| `GLOBAL_COMMAND` | Comando de consola sin barra | Una vez |
| `EVENT` | ID de un evento de EventsPL | Una vez |

Variables: `{player}`, `{goal_id}`, `{goal_name}`, `{progress}`, `{goal}`, `{percent}`, `{contribution}` y
`{rank}`.

- Lo que no se puede entregar (jugador desconectado, inventario lleno, un comando que falla) queda **pendiente** y
  se reintenta al entrar, cada `rewards.retry-minutes` minutos y con `/goals claim`.
- Los permisos temporales no necesitan un plugin de permisos: se dan mientras el jugador está conectado y vuelven al
  entrar hasta que caducan. Si ya lo tenía, la duración se suma.
- En los comandos, los valores de las variables se limpian (solo letras, números, `_`, `.`, `-` y espacios) y los
  comandos de `console-commands.blocked` nunca se ejecutan.

### Entrega de materiales

Los objetos nunca se ponen en el menú. El jugador elige un material y cuántos entregar (clic izquierdo 1, derecho
16, con mayúsculas todos los que pueda) y lo confirma. Al confirmar se vuelven a contar y se quitan solo los
aceptados, nunca más de lo que falta ni de su máximo. Solo valen objetos simples (sin nombre, encantamientos ni
daño) y solo de las 36 casillas del inventario, nunca la armadura ni la segunda mano.

Si la campaña se cancela o vence con «se devuelven», cada jugador recibe lo que entregó como recompensa pendiente.

### Etapas

Una campaña puede indicar la siguiente (`next`). Al completarse, la siguiente se inicia sola y lo anuncia.

### Protecciones contra progreso falso

- Las criaturas solo cuentan si las mata un jugador, una vez cada una. Por defecto no cuentan las de generadores.
  Con la lista vacía nunca cuentan jugadores ni soportes de armadura.
- Los bloques colocados por jugadores no cuentan (se marcan en los datos del chunk). Los cultivos solo cuentan
  crecidos.
- Por defecto no cuenta el modo creativo, y el espectador nunca.
- Otros plugins pueden cancelar un aporte con `CampaignContributionEvent`.

## Menús

| Menú | Qué hace |
|---|---|
| Principal (`/goals`) | Metas activas, mis recompensas y, con permiso, administración |
| Metas activas | Progreso, tiempo y mi aportación de cada campaña (se actualiza solo) |
| Detalle | Hitos alcanzados, mi aportación y mis hitos personales, clasificación, reglas, botón de entrega |
| Entrega | Materiales pedidos, cuántos tengo y cuántos puedo entregar, con confirmación |
| Mis recompensas | Pendientes, con un botón para reclamarlas |
| Administración | Todas las campañas; crear, historial general, recargar |
| Panel de campaña | Iniciar, pausar, reanudar, completar, cancelar (con confirmación), editar, clonar, ajustar progreso, aportaciones, recompensas entregadas o pendientes, historial, borrar |
| Editor | Todos los campos, objetivos, mundos, hitos, hitos personales, clasificación, recompensas (escritas o desde plantilla), anuncios, mensajes y bossbar |

Los iconos no se pueden sacar ni mover, y cada botón comprueba su permiso al pulsarlo. Cerrar el editor sin guardar
descarta los cambios.

## Comandos

| Comando | Permiso | Qué hace |
|---|---|---|
| `/goals` | `goalspl.use` | Menú principal (en consola, la ayuda) |
| `/goals help` | | Ayuda |
| `/goals goals` | `goalspl.use` | Metas activas (en consola, lista) |
| `/goals info <id>` | `goalspl.use` | Detalle de una campaña |
| `/goals top <id>` | `goalspl.use` | Clasificación |
| `/goals contribute <id>` | `goalspl.contribute` | Entrega de materiales |
| `/goals claim` | `goalspl.use` | Recibir recompensas pendientes |
| `/goals admin` | `goalspl.view` | Panel de administración |
| `/goals start <id>` | `goalspl.manage` | Iniciar; si ya terminó, ejecución nueva desde cero |
| `/goals pause <id>`, `/goals resume <id>` | `goalspl.manage` | Pausar y reanudar |
| `/goals complete <id>` | `goalspl.manage` | Completar ya: hitos que falten, clasificación y etapa siguiente |
| `/goals cancel <id>` | `goalspl.cancel` | Cancelar |
| `/goals progress <id> <+n/-n/=n> [jugador]` | `goalspl.progress` | Sumar, restar o fijar el progreso; con jugador, también su aportación |
| `/goals history [id] [página]` | `goalspl.view` | Historial |
| `/goals reload` | `goalspl.reload` | Recargar los archivos |

El comando principal es `/goalspl`; `/goals` es su alias. Todos funcionan desde la consola salvo `contribute`,
`claim` y `admin`.

### Permisos

| Permiso | Por defecto | Para qué |
|---|---|---|
| `goalspl.use` | Todos | Menú, metas, detalle, clasificación y reclamar |
| `goalspl.contribute` | Todos | Entregar materiales (criaturas y bloques cuentan sin permiso) |
| `goalspl.view` | OP | Panel de administración en modo consulta |
| `goalspl.manage` | OP | Crear, editar, clonar, borrar, iniciar, pausar, reanudar y completar |
| `goalspl.progress` | OP | Ajustar progreso |
| `goalspl.cancel` | OP | Cancelar |
| `goalspl.reload` | OP | Recargar |
| `goalspl.commands` | OP | Añadir o cambiar recompensas de comandos de consola |
| `goalspl.admin` | OP | Todos los anteriores |

## Configuración

### `config.yml`

| Opción | Por defecto | Qué hace |
|---|---|---|
| `language` | `es` | Idioma con el que se crea `messages.yml` (`es` o `en`) |
| `timezone` | sistema | Zona horaria de las fechas límite (`Europe/Madrid`) |
| `storage.file` | `data/goalspl.db` | Archivo SQLite (cambia al reiniciar) |
| `storage.save-interval-seconds` | `2` | Cada cuánto se guarda el progreso de criaturas y bloques; las entregas se guardan al momento |
| `history.retention-days` | `90` | Días que se guardan historial, entregas y recompensas entregadas |
| `counting.spawner-mobs` | `false` | Si cuentan las criaturas de generadores |
| `counting.creative` | `false` | Si cuenta el modo creativo |
| `counting.ignore-placed-blocks` | `true` | No contar bloques colocados por jugadores |
| `counting.only-grown-crops` | `true` | Los cultivos solo cuentan crecidos |
| `rewards.deliver-on-join` | `true` | Entregar lo pendiente al entrar |
| `rewards.retry-minutes` | `5` | Reintento de lo pendiente de los conectados (0 = solo al entrar y con claim) |
| `leaderboard.size` | `10` | Puestos que se muestran |
| `bossbar.enabled` | `true` | Interruptor general de las bossbars |
| `console-commands.blocked` | `op`, `deop`, `stop`... | Comandos que nunca ejecuta una recompensa |

La retención nunca borra recompensas pendientes ni datos de la ejecución en curso de una campaña.

### Otros archivos

- `goals.yml`: las campañas. Se pueden editar a mano y aplicar con `/goals reload`. Una campaña con un valor no
  válido no se carga, pero se conserva en el archivo y se avisa en el registro. Si el archivo entero no se puede
  leer, no se guarda nada desde el juego hasta corregirlo.
- `rewards.yml`: plantillas de recompensas que se añaden con un clic desde el editor.
- `messages.yml`: todos los textos en MiniMessage. Las claves nuevas de cada versión se añaden solas.

### Datos

`data/goalspl.db` guarda el estado y el progreso de cada campaña, las aportaciones por jugador, los hitos
alcanzados, las recompensas entregadas o pendientes, las entregas, los permisos temporales y el historial. Todo el
acceso al disco va en un hilo propio, nunca en el principal. Antes de cada cambio de esquema se guarda una copia en
`data/backups/`.

## API

```java
GoalsAPI api = Bukkit.getServicesManager().load(GoalsAPI.class);
api.campaigns();                 // todas las campañas (CampaignInfo)
api.running();                   // activas o pausadas
api.contribution("zombie_hunt", uuid);
api.addProgress("zombie_hunt", uuid, "Steve", 5, "MiPlugin");   // devuelve lo aceptado
```

Eventos de Bukkit en `com.plgroup.goalspl.api.event`:

| Evento | Cuándo |
|---|---|
| `CampaignStartedEvent` | Inicio, reanudación o inicio como etapa siguiente (`previous()`) |
| `CampaignMilestoneEvent` | Se alcanza un hito (`percent()`) |
| `CampaignEndedEvent` | Completada, cancelada, vencida o pausada (`outcome()`) |
| `CampaignContributionEvent` | Antes de sumar un aporte; cancelable |

Llamar a la API solo desde el hilo principal.

## Limitaciones conocidas

- Los bloques movidos con pistones pierden la marca de colocados por un jugador.
- Los permisos temporales no aparecen en el plugin de permisos del servidor.
- Las recompensas de comunidad no llegan a jugadores desconectados que no aportaron nada.
- En el editor de hitos, el anuncio se cambia con la tecla Q (el clic central solo funciona en creativo).
