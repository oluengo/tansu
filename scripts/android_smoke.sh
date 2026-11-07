#!/usr/bin/env bash
# Prueba de arranque en un emulador (CI): instala el APK, abre la app, espera y busca cierres.
# Deja el resultado como anotaciones de GitHub Actions (cada una, varias líneas unidas con %0A).
set -u
join() { head -${1:-30} | sed 's/%/%25/g' | sed ':a;N;$!ba;s/\n/%0A/g'; }
apk=$(ls ${APK_DIR:-dist}/*.apk | head -1)
bt=$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)
pkg=$("$bt/aapt2" dump packagename "$apk" 2>/dev/null | head -1)
act=$("$bt/aapt2" dump badging "$apk" 2>/dev/null | sed -n "s/^launchable-activity: name='\([^']*\)'.*/\1/p" | head -1)
echo "::notice::Android: APK $(basename "$apk") · paquete=$pkg · actividad=$act"
adb install -r -g "$apk" || { echo "::error::Android: no se ha podido instalar el APK"; exit 1; }
adb logcat -c
adb shell am start -W -n "$pkg/$act" | join 10 | sed 's/^/::notice::am start%0A/'
sleep 20
adb logcat -d > /tmp/logcat.txt
grep -A30 "FATAL EXCEPTION" /tmp/logcat.txt > /tmp/crash.txt || true
grep -E "Fatal signal|DEBUG   :|backtrace" /tmp/logcat.txt | head -30 >> /tmp/crash.txt || true
if [ -s /tmp/crash.txt ]; then
  echo "::error::Android: la app se cierra al arrancar%0A$(sed 's/^.*\(AndroidRuntime\|DEBUG\|libc\) *: //' /tmp/crash.txt | join 40)"
fi
echo "::notice::Android: log de la app%0A$(grep -iE "$pkg|ActivityManager.*(Start proc|died|crash|kill)|ActivityTaskManager" /tmp/logcat.txt | grep -v resolv | tail -30 | join 30)"
if adb shell pidof "$pkg" > /dev/null; then
  echo "::notice::Android: la app arranca y sigue abierta a los 20 s"
  [ -s /tmp/crash.txt ] && exit 1 || exit 0
else
  echo "::error::Android: la app no sigue abierta a los 20 s"
  exit 1
fi
