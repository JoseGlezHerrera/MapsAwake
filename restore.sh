#!/usr/bin/env bash
# Desinstala MapsAwake del RELOJ y deja el timeout en el valor original.
# Uso: ./restore.sh <serial_del_reloj> [timeout_ms]
#      timeout_ms: si no se da, se usa el que apuntó install.sh en .watch-original.env
set -euo pipefail
cd "$(dirname "$0")"

PKG=dev.mapsawake
SVC="$PKG/$PKG.MapsWatcherService"
ORIG=.watch-original.env

S="${1:-${WATCH_SERIAL:-}}"
[ -n "$S" ] || { echo "Uso: $0 <serial_del_reloj> [timeout_ms]"; exit 2; }

ok()   { printf '   \e[32m✓\e[0m %s\n' "$*"; }
warn() { printf '   \e[33m! %s\e[0m\n' "$*"; }
fail() { printf '   \e[31m✗ %s\e[0m\n' "$*"; exit 1; }
step() { printf '\n\e[1m▶ %s\e[0m\n' "$*"; }
a()    { adb -s "$S" "$@"; }
sh_()  { a shell "$@" | tr -d '\r'; }

ORIG_SCREEN_OFF_TIMEOUT=""; ORIG_ACCESSIBILITY_ENABLED=""
# shellcheck source=/dev/null
[ -f "$ORIG" ] && . "./$ORIG"
T="${2:-$ORIG_SCREEN_OFF_TIMEOUT}"
[[ "$T" =~ ^[0-9]+$ ]] || fail "No sé el timeout original: pásalo como 2º argumento (p. ej. 15000)"

step "Compruebo que '$S' es un reloj (getprop: solo lectura)"
[ "$(adb -s "$S" get-state 2>/dev/null)" = device ] || fail "'$S' no está conectado"
chars=$(sh_ getprop ro.build.characteristics)
[[ "$chars" == *watch* ]] || fail "$(sh_ getprop ro.product.model) no es un reloj. Abortado."
ok "$(sh_ getprop ro.product.model)"

step "Quito el servicio de accesibilidad (al desenlazarse, la app restaura lo que cambió)"
cur=$(sh_ settings get secure enabled_accessibility_services)
[ "$cur" = null ] && cur=""
new=$(tr ':' '\n' <<< "$cur" | grep -vxF "$SVC" | paste -sd: - || true)
if [ "$new" != "$cur" ]; then
    if [ -n "$new" ]; then
        a shell settings put secure enabled_accessibility_services "$new"
    else
        a shell settings delete secure enabled_accessibility_services >/dev/null
        a shell settings put secure accessibility_enabled "${ORIG_ACCESSIBILITY_ENABLED:-0}"
    fi
fi
sh_ settings get secure enabled_accessibility_services | grep "$SVC" >/dev/null && fail "sigue en la lista"
ok "servicio quitado (quedan: ${new:-ninguno})"

step "Lo saco de la whitelist de batería (dumpsys deviceidle whitelist -$PKG)"
a shell dumpsys deviceidle whitelist "-$PKG" >/dev/null || true
sh_ dumpsys deviceidle whitelist | grep "$PKG" >/dev/null && warn "sigue en la whitelist" || ok "fuera"

step "Desinstalo la app (adb uninstall $PKG)"
if sh_ pm path "$PKG" | grep base.apk >/dev/null; then
    a uninstall "$PKG" >/dev/null
fi
sh_ pm path "$PKG" | grep base.apk >/dev/null && fail "sigue instalada"
ok "desinstalada"

step "Dejo screen_off_timeout en $T ms (settings put system screen_off_timeout $T)"
a shell settings put system screen_off_timeout "$T"
[ "$(sh_ settings get system screen_off_timeout)" = "$T" ] || fail "no se aplicó"
ok "screen_off_timeout = $T"

bm=$(sh_ settings get system screen_brightness_mode)
[ "$bm" = 0 ] && warn "El brillo está en manual (screen_brightness_mode=0). Si lo tenías en automático, actívalo en Ajustes → Pantalla."
step "Listo"
