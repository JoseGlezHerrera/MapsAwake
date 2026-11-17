#!/usr/bin/env bash
# Compila MapsAwake, la instala en el RELOJ y le da los permisos por adb.
# Uso: ./install.sh <serial_del_reloj>     (p. ej. 10.18.151.33:39849)
#      Mira el serial con: adb devices -l
set -euo pipefail
cd "$(dirname "$0")"

PKG=dev.mapsawake
SVC="$PKG/$PKG.MapsWatcherService"
APK=app/build/outputs/apk/release/app-release.apk
ORIG=.watch-original.env

S="${1:-${WATCH_SERIAL:-}}"
[ -n "$S" ] || { echo "Uso: $0 <serial_del_reloj>  (adb devices -l)"; exit 2; }

ok()   { printf '   \e[32m✓\e[0m %s\n' "$*"; }
fail() { printf '   \e[31m✗ %s\e[0m\n' "$*"; exit 1; }
step() { printf '\n\e[1m▶ %s\e[0m\n' "$*"; }
# Todos los comandos adb van con -s: nunca al dispositivo por defecto.
a()    { adb -s "$S" "$@"; }
sh_()  { a shell "$@" | tr -d '\r'; }

step "Compruebo que '$S' es un reloj (getprop: solo lectura)"
[ "$(adb -s "$S" get-state 2>/dev/null)" = device ] || fail "'$S' no está conectado (adb devices -l)"
chars=$(sh_ getprop ro.build.characteristics)
model=$(sh_ getprop ro.product.model)
sdk=$(sh_ getprop ro.build.version.sdk)
[[ "$chars" == *watch* ]] || fail "$model no es un reloj (ro.build.characteristics=$chars). Abortado."
ok "$model, API $sdk"

step "Compilo el APK release (Gradle con JDK 17)"
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew -q assembleRelease
[ -f "$APK" ] || fail "no se generó $APK"
ok "$APK ($(du -h "$APK" | cut -f1))"

step "Apunto los valores originales del reloj en $ORIG (settings get: solo lectura)"
if [ -f "$ORIG" ]; then
    ok "ya existía, no lo piso: $(tr '\n' ' ' < "$ORIG")"
else
    t=$(sh_ settings get system screen_off_timeout)
    [ "$t" = 2147483647 ] && fail "screen_off_timeout ya está al máximo (¿Maps delante con la app activa?). Cierra Maps y repite."
    {
        echo "SERIAL_MODEL=$model"
        echo "ORIG_SCREEN_OFF_TIMEOUT=$t"
        echo "ORIG_ACCESSIBILITY_ENABLED=$(sh_ settings get secure accessibility_enabled)"
    } > "$ORIG"
    ok "$(tr '\n' ' ' < "$ORIG")"
fi

step "Instalo el APK (adb install -r: instala o actualiza conservando datos)"
# La wifi del reloj da cortes: hasta 3 intentos.
for i in 1 2 3; do
    a install -r "$APK" >/dev/null && break
    [ "$i" = 3 ] && fail "adb install falló 3 veces"
    echo "   reintento $((i + 1))/3…"
done
sh_ pm path "$PKG" | grep base.apk >/dev/null || fail "no aparece instalada"
ok "instalada ($(sh_ dumpsys package $PKG | grep -m1 versionName | xargs))"

step "Concedo WRITE_SETTINGS (appops set ... WRITE_SETTINGS allow)"
a shell appops set "$PKG" WRITE_SETTINGS allow
sh_ appops get "$PKG" WRITE_SETTINGS | grep allow >/dev/null || fail "WRITE_SETTINGS no quedó en allow"
ok "WRITE_SETTINGS: allow"

step "Activo el servicio de accesibilidad (settings put secure, conservando los que ya tengas)"
cur=$(sh_ settings get secure enabled_accessibility_services)
[ "$cur" = null ] && cur=""
if [[ ":$cur:" == *":$SVC:"* ]]; then
    ok "ya estaba en la lista"
else
    new="${cur:+$cur:}$SVC"
    a shell settings put secure enabled_accessibility_services "$new"
fi
a shell settings put secure accessibility_enabled 1
sh_ settings get secure enabled_accessibility_services | grep "$SVC" >/dev/null || fail "no quedó en enabled_accessibility_services"
# El sistema tarda un instante en enlazar el servicio: esperamos hasta 10 s.
for _ in $(seq 20); do
    sh_ dumpsys accessibility | grep "$SVC" >/dev/null && break
    sleep 0.5
done
sh_ dumpsys accessibility | grep "$SVC" >/dev/null || fail "el servicio no aparece enlazado en dumpsys accessibility"
ok "servicio enlazado"

step "Saco la app de la optimización de batería (dumpsys deviceidle whitelist +$PKG)"
a shell dumpsys deviceidle whitelist "+$PKG" >/dev/null
sh_ dumpsys deviceidle whitelist | grep "$PKG" >/dev/null || fail "no aparece en la whitelist de deviceidle"
ok "en la whitelist"

# shellcheck source=/dev/null
. "./$ORIG"
step "Listo"
echo "   Abre Google Maps en el reloj y comprueba con:"
echo "   adb -s $S shell settings get system screen_off_timeout"
echo "   → 2147483647 con Maps delante, $ORIG_SCREEN_OFF_TIMEOUT sin Maps"
