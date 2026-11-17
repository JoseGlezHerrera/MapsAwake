# MapsAwake

App mínima para **Wear OS** que mantiene encendida la pantalla del reloj
**solo mientras Google Maps está en primer plano** y lo deja todo como estaba
al salir. Pensada para navegar en moto mirando el reloj sin tocarlo. Sin root.

Probada en un **Galaxy Watch7 (SM-L310), One UI Watch, Wear OS 6 / Android 16
(API 36)**.

## Qué hace

Cuando Maps (`com.google.android.apps.maps`) pasa a primer plano:

1. Guarda los valores actuales de `screen_off_timeout` (y del brillo, si la
   opción está activa).
2. Pone `screen_off_timeout` al máximo (`2147483647` ms ≈ 24,8 días).
3. Añade una ventana de 1×1 px, transparente y que no recibe toques, con
   `FLAG_KEEP_SCREEN_ON`.
4. Opcional (*Brillo al máximo*, apagado por defecto): pone el brillo en manual
   al máximo, para verlo a pleno sol.

Cuando Maps sale de primer plano, cuando apagas la app (interruptor o tile) o
cuando se desactiva el servicio de accesibilidad: quita la ventana y
**restaura los valores guardados**. Si el reloj se apaga de golpe con Maps
delante, al arrancar (`BOOT_COMPLETED`) se restauran también.

### Por qué hace falta la ventana `FLAG_KEEP_SCREEN_ON`

Subir solo `screen_off_timeout` **no basta en One UI Watch**. Con el brazo
quieto la pantalla aguanta, pero al **bajar la muñeca**, o al despertarla con
un **golpe de muñeca**, One UI aplica por su cuenta un timeout de **~7 s**.
`dumpsys power` lo muestra como `Screen off timeout: 7000 ms` aunque el ajuste
esté al máximo, y el historial registra `OFF timeout`. No sale de ningún ajuste
accesible: desactivar `global ungaze_sleep_enabled` no lo cambia. Una ventana
visible con `FLAG_KEEP_SCREEN_ON` hace que el sistema no apague la pantalla
por timeout, venga de donde venga. En `dumpsys window` aparece como
`mHoldScreenWindow=Window{… dev.mapsawake}`.

### Por qué un AccessibilityService

- Se basa en **eventos** (`TYPE_WINDOW_STATE_CHANGED` y `TYPE_WINDOWS_CHANGED`),
  sin sondeo: no gasta batería mientras no cambian las ventanas.
- Para saber qué app está delante, mira la ventana de tipo aplicación de más
  arriba (`getWindows()`). Así ignora el panel rápido, las notificaciones y los
  toasts que se ponen encima de Maps.
- Es un servicio **enlazado por el sistema**: no necesita servicio en primer
  plano (ni sus tipos obligatorios desde Android 14), y el congelador de
  procesos de Samsung (Freecess) no lo congela mientras está enlazado.
- Es lo único que permite poner una ventana encima de otra app
  (`TYPE_ACCESSIBILITY_OVERLAY`) sin el permiso de superposición.
- Alternativas descartadas: `UsageStatsManager` obliga a sondear;
  `ActivityManager.getRunningTasks` ya no informa de otras apps.

No lee texto, contenido ni pulsaciones: solo a qué paquete pertenece cada
ventana.

### Interfaz

- **App**: interruptor *Activo*, interruptor *Brillo al máximo* y estado
  (Maps delante o no, timeout actual y original, y si están los tres permisos).
- **Tile** *MapsAwake*: tócala para activar o desactivar la app.

## Requisitos

- Linux con `adb` (android-tools), **JDK 17**
  (`/usr/lib/jvm/java-17-openjdk`) y Android SDK con `platforms;android-37` en
  `~/Android/Sdk`. El Gradle wrapper (9.9.1) se descarga solo.
- Reloj con **Opciones de desarrollador** y **Depuración inalámbrica** activadas.
- Reloj y portátil en la **misma red** y **viéndose entre sí** (ver
  *Conectar el reloj*).

## Conectar el reloj

1. En el reloj: **Ajustes → Opciones de desarrollador → Depuración
   inalámbrica → Emparejar nuevo dispositivo**.
2. `adb pair <IP>:<puerto_emparejar> <código>`. Solo la primera vez.
3. `adb connect <IP>:<puerto_conexión>`. El puerto de conexión es el que sale
   en la pantalla principal de Depuración inalámbrica y cambia cada vez que se
   reactiva. Con `adb mdns services` se ven los dos.
4. `adb devices -l` debe mostrar `model:SM_L310`. **Usa siempre `-s <serial>`**
   si hay más dispositivos (p. ej. el móvil por USB).

Si `adb connect` da `No route to host` y el reloj no responde a `ping`, puede
que la red no deje pasar tráfico entre equipos (repetidores wifi, aislamiento
de clientes). Lo más fácil: conectar reloj y portátil al **punto de acceso del
móvil**. En Arch con NetworkManager: `nmcli dev wifi connect <red> password …`.

## Instalación

```bash
./install.sh <serial_del_reloj>      # p. ej. ./install.sh 10.18.151.33:39849
```

El script se niega a seguir si el dispositivo no es un reloj
(`ro.build.characteristics` sin `watch`), y luego:

1. compila el APK *release* (R8, ~470 KB, firmado con la clave debug local);
2. apunta los valores originales del reloj en `.watch-original.env` (no versionado);
3. instala con `adb -s <serial> install -r`;
4. concede `WRITE_SETTINGS` con `appops set dev.mapsawake WRITE_SETTINGS allow`;
5. activa el servicio de accesibilidad (`settings put secure
   enabled_accessibility_services`, conservando los que ya hubiera) y
   `accessibility_enabled=1`;
6. saca la app de la optimización de batería
   (`dumpsys deviceidle whitelist +dev.mapsawake`).

Verifica cada paso y para en el primero que falle.

## Desinstalación

```bash
./restore.sh <serial_del_reloj> [timeout_ms]
```

Quita el servicio de accesibilidad (la app restaura lo que tenga cambiado), la
saca de la whitelist, la desinstala y deja `screen_off_timeout` en el valor de
`.watch-original.env` (o en el que pases como 2º argumento).

## Comprobar que funciona

```bash
S=<serial>
adb -s $S shell settings get system screen_off_timeout   # 2147483647 con Maps / original sin Maps
adb -s $S shell dumpsys window | grep mHoldScreenWindow   # Window{… dev.mapsawake} con Maps
adb -s $S logcat -s MapsAwake                             # Aplicado / Restaurado / Ventana …
```

## Limitaciones

- **Batería**: pantalla siempre encendida, y más aún con el brillo al máximo,
  gasta mucho. En rutas largas, vigila la batería del reloj.
- **Quemado AMOLED**: una imagen fija durante horas puede marcar la pantalla.
  El mapa se mueve, pero los elementos fijos de la interfaz de Maps no.
- **Tapar con la palma o pulsar el botón** sigue apagando la pantalla (es un
  apagado explícito, no por timeout). Al volver a encenderla con Maps delante,
  se mantiene encendida otra vez.
- Si cambias el tiempo de pantalla **a mano con Maps delante**, al salir de
  Maps se vuelve al valor guardado al entrar.
- El APK va firmado con la clave debug **de este portátil**
  (`~/.android/debug.keystore`). Para actualizarlo desde otro equipo, antes hay
  que desinstalarlo.
- Si Samsung o una actualización desactivan el servicio de accesibilidad, la
  app lo indica en su pantalla (✗ Accesibilidad). Vuelve a lanzar `install.sh`.
- Solo reconoce Google Maps (`com.google.android.apps.maps`). Para otra app,
  cambia `MAPS_PACKAGE` en `State.kt`.
