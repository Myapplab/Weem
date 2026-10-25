#!/bin/bash
# WEEM — Claude Code bulut oturumu başlangıcı.
# Çıktı her oturumun bağlamına girer: ~1 KB'ı geçmez (satırlar kısaltılır). Hiçbir adım oturumu durdurmaz.
set -uo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

# Kabuğun yerel ayarı POSIX: Türkçe adlı test işlevlerinin sınıf dosyaları derleyicide kırılır (InvalidPathException).
# Oturumun kendisi için aynı değer .claude/settings.json → env'de.
export LC_ALL=C.UTF-8

# Satırları en çok $1 bayta kısalt; yarım kalan UTF-8 harfini at.
short() {
  if command -v iconv >/dev/null 2>&1; then cut -b "1-$1" | iconv -c -f UTF-8 -t UTF-8; else cut -b "1-$1"; fi
}

echo "== WEEM oturum başlangıcı (AGENTS.md) =="

# 1) "Şu an": son yapılan, devam eden, sıradaki, kullanıcıdan beklenen
if [ -f docs/WEEM_ISLER.md ]; then
  awk '/^## Şu an/{f=1; next} /^## /{if (f) exit} f && NF' docs/WEEM_ISLER.md | head -5 | short 420
fi

# 2) Son devir notunun başı
if [ -f AI_HANDOFF.md ]; then
  echo "-- Son devir notu --"
  awk '/^## /{n++} n==1' AI_HANDOFF.md | head -4 | short 300
fi

# 3) Ortam (tek satır)
env_line="JDK $(java -version 2>&1 | grep -oE 'version "[0-9]+' | grep -oE '[0-9]+$' || echo YOK)"

code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 \
  https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml || true)
if [ "$code" = "200" ]; then
  if [ -x tools/setup-android-sdk.sh ] && timeout 600 tools/setup-android-sdk.sh >/dev/null 2>&1; then
    env_line+=" · Android SDK hazır"
  else
    env_line+=" · Android SDK kurulamadı (tools/setup-android-sdk.sh)"
  fi
else
  env_line+=" · dl.google.com KAPALI: Android modülleri CI'da derlenir (AGENTS.md → Ortam)"
fi

# 4) Saf Kotlin mantığın bağımlılıklarını indir ve derle (Android'siz build: logic/)
if [ -x ./gradlew ] && [ -f logic/settings.gradle.kts ]; then
  # Maven Central proxy üstünden ara sıra 429 verir: tek işçiyle, bir kez yeniden dene.
  warm() { timeout 600 ./gradlew -p logic --no-daemon -q --max-workers=1 testClasses >/dev/null 2>&1; }
  if warm || { sleep 10; warm; }; then
    env_line+=" · logic/ derlendi"
  else
    env_line+=" · logic/ derlenemedi: tools/test.sh ile bak"
  fi
fi

echo "-- Ortam: $env_line"
echo "Test: tools/test.sh <hedef> · kod yeri: docs/KOD_HARITASI.md"

exit 0
