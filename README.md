# Elite Bosses (Forge 1.20.1)

Contiene cuatro jefes: **Chocolate** (Odio el Chocolate), **Sabueso** (Houndmaster), **Stalker** (Krasue) y **El Juicio**,
mas los objetos Angel Guardian (antes Vito's Blessing), Manual de Supervivencia y Totem BlackFate, y los efectos Obsesion, Salmuera y Velo Diurno.
Requiere Minecraft 1.20.1 + Forge 47.x + **GeckoLib 4.8** (`geckolib-forge-1.20.1-4.8.x.jar` en la carpeta `mods`).

## Compilar con GitHub

1. Crea un repositorio nuevo y sube TODO el contenido de esta carpeta (no el zip ni la carpeta envoltorio).
2. Debe existir `.github/workflows/build.yml`. Si no se subio (carpeta oculta), usa
   "Add file > Create new file", escribe `.github/workflows/build.yml` y pega el contenido de `COPIAR_build.yml.txt`.
3. Pestana **Actions** > ejecucion "Compilar mod" > espera el tick verde.
4. Abajo, en **Artifacts**, descarga `elitebosses-jar` (dentro esta `elitebosses-5.0.0.jar`).
5. Borra el jar viejo `elitebosses-4.5.0.jar` (y cualquier `odioelchocolate-*.jar`) de tu carpeta `mods` y pon el nuevo.

## Comandos

- `/houndmaster` : el Sabueso aparece a 300 bloques del jugador que ejecuta el comando (op nivel 2).
- `/houndmaster <x> <y> <z>` : lo invoca en esas coordenadas.
- `/summon elitebosses:stalker` : el Stalker (tambien con su huevo). Elige al jugador survival mas cercano.
- `/summon elitebosses:houndmaster` o `/summon elitebosses:chocolate` : aparecen donde indiques / donde estas.
- `/juicio estado | contador <n> | marcar | cazar | invocar | aparecer | reset [jugador]` (op nivel 2, para probar al Juicio):
  `contador 29` y matas 1 mob = aparece el contador; `marcar` = llegas a 150; `cazar` = ya dormiste; `invocar` = rayo de luz ya;
  `aparecer` = el Juicio aparece a 8 bloques; `reset` = borra la condena.
- `/summon elitebosses:judge` o su huevo: un Juicio "de prueba" que persigue al jugador survival mas cercano (no toca tu condena).
- `/chocosound chocochase1 | chocochase2 | chocospawn | stop [jugadores]`
- Huevos: pestana "Elite Bosses" del creativo (y pestana de huevos). Si no los ves:
  `/give @s elitebosses:houndmaster_spawn_egg` y `/give @s elitebosses:chocolate_spawn_egg`, `/give @s elitebosses:stalker_spawn_egg`,
  `/give @s elitebosses:judge_spawn_egg`, `/give @s elitebosses:vitos_blessing` (es el Angel Guardian; el id interno no cambio), `/give @s elitebosses:survival_manual`, `/give @s elitebosses:black_fate_totem`

## Donde ajustar

- Sabueso: constantes al inicio de `src/main/java/com/elitebosses/entity/HoundmasterEntity.java`.
- Stalker: constantes al inicio de `.../entity/StalkerEntity.java`.
- Chocolate: constantes al inicio de `.../entity/ChocolateEntity.java`.
- El Juicio: constantes al inicio de `.../entity/JudgeEntity.java` (combate) y `.../events/JudgmentEvents.java` (30/50/100/150, 250 bloques, tiempos).
- Barra de vida: `client/HoundBossBar.java` (SCALE para el tamano).

## Novedades v5.0.0: El Juicio

Serafin juez de ~7 bloques: patas digitigradas de 3.4 bloques (un jugador le llega a media pata), alas de ceniza
cubiertas de ojos dorados, mascara de porcelana con una sola rendija de luz, anillos giroscopicos que giran
sin parar, baculo solar y tunica hecha jirones. Modelo, textura y animaciones GeckoLib propias:
idle, walk, smash, lunge, hold, impale + impale_hold, exile, solar, backstep, push, fury, intro y death.

**Como se invoca (por jugador, se guarda en el jugador):**
1. Cada entidad viva que matas suma, sea del mod que sea (incluye golpes tuyos seguidos de caida/lava/trampa).
   Se reinicia si duermes (al acostarte) o mueres.
2. 30 muertes: contador rojo 15 s. 50: suena JudgeWarn1 y el contador 30 s. 100: JudgeWarn2 y 2 min.
   Mientras se ve, se actualiza con cada muerte.
3. 150: el numero se fija al centro en rojo oscuro 5 s con "Tus dias estan contados".
4. La proxima vez que te acuestes: JudgeStalk y no puedes levantarte en 10 s (el boton y ESC no sirven).
5. Despues, la primera vez que estes a 250 bloques o mas (en X/Z) de tu punto de reaparicion (tu cama; si no tienes,
   el spawn del mundo): Lentitud V 1 min, suena JudgeSpawn y un rayo de luz se planta frente a ti y te sigue.
6. Al acabar el minuto, el Juicio desciende por ese rayo y suena JudgeChase en bucle hasta que muera o desaparezca.
- Matarlo borra la condena. Si te mata, la sentencia se cumple y tambien vuelve a 0.
- Morir antes de que aparezca NO borra la condena (solo el contador de la etapa 1). Si sales del mundo, el Juicio se va
  y la caceria se repite la proxima vez que te alejes 250 bloques.

**Combate:** 300 corazones, inmune al fuego, misma velocidad que un jugador; te rodea pero no se aleja; cada 10 s
retrocede aleteando; atraviesa hojas y si se atasca 2 s reaparece junto a ti.
- Llegada: un rayo te quita 1 corazon y despues te empuja ~10 bloques.
- Ataque basico (baculo): 6 PS con vida llena, sube hasta 8 PS cuanto menos vida tenga (la armadura reduce).
- Valentia/Furia: si estas a mas de 20 bloques, "COBARDE" en pantalla y 15 rayos de 0.5 corazones; cada 15 s.
- Empalar: arremete y te agarra; pulsa la tecla que sale (R, G, C o P; la P sale 10%). Aciertas = te sueltas
  (enfriamiento 15 s, 20 s si era P). Fallas o no pulsas en 2 s = empalado: clavado al suelo, 7 veces ESPACIO
  y cada una cuesta 1 PS (enfriamiento 30 s). Seguro: te suelta a los 30 s.
- Exilio (15%): te levanta 5 bloques y te manda a 40-100 bloques sobre la superficie; ves a cuantos bloques esta
  hasta que vuelvas a 20 o menos (y mientras tanto Valentia te castiga).
- Invocacion Solar: marca un area 2x2 bajo tus pies, 1 s despues cae la luz: 3 corazones y quemadura que no se
  apaga sola (solo agua o lluvia; Resistencia al fuego evita el dano pero no la apaga).
- Botin: estrella del Nether, bloques de oro, diamantes, manzanas de Notch y totems.
- El Angel Guardian / Velo Diurno NO destierra al Juicio.

## Novedades v4.5.0 (overhaul de modelos y animaciones con GeckoLib)

- Sabueso y Krasue tienen modelo, textura y animaciones nuevas (GeckoLib). El Chocolate no cambia.
- Archivos: `assets/elitebosses/geo/*.geo.json`, `animations/*.animation.json`, `textures/entity/*.png` (+ `*_glowmask.png` = partes que brillan).
- Sabueso: postura encorvada y sarnosa, costillas en relieve, hocico largo con colmillos, hombros/codos/rodillas/corvejones articulados, cola de 4 segmentos.
  Animaciones: idle (respiracion pesada), walk, run (galope con flexion de columna), pounce, bite (dentellada al golpear), growl, grab, howl, charge, stun, dig.
- Krasue: rostro desfigurado asimetrico, cabello de 9 mechones articulados (4 segmentos), 8 cadenas de intestinos + columna colgante, corazon visible que late.
  Animaciones: idle (cabeza flotante, visceras pendulares asincronas, corazon latiendo), move, whip, ascend, rapto, purge, exec, drain, finish (muerte).
- Nombres de animacion: `animation.houndmaster.<nombre>` y `animation.stalker.<nombre>`.
- Los ids y el comportamiento de los jefes no cambian. La Krasue ahora sincroniza su fase (`Act`) al cliente solo para elegir animacion.

## Novedades v4.0.0

- Krasue: sin Resistencia; Rapto visceral con latigos (3 PS/s); la vision de la Obsesion ya no usa imagen, alterna blanco/negro translucido.
- Sabueso: sin Resistencia; Abalanzar hace 7 PS; 8 bloques/s en BUSQUEDA (jugador a mas de 70 bloques); lobos duran 15 s.
- Efectos en espanol (Obsesion, Salmuera, Velo Diurno) y con nivel en digitos (1, 2 ... 200).
- Angel Guardian (antes Vito's Blessing) ahora da el efecto Velo Diurno (20 min).
- Manual de Supervivencia rediseñado (indice, capitulos, colores).
