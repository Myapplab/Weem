#!/bin/bash
# WEEM — Claude Code bulut oturumu başlangıcı.
# Çıktı oturumun bağlamına eklenir: kısa tutulur. Hiçbir adım oturumu durdurmaz.
set -uo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

echo "== WEEM oturum başlangıcı (kurallar: AGENTS.md → Birden çok Claude hesabı) =="

# 1) İş listesinden "Şu an": son yapılan, sıradaki iş, yarım iş
if [ -f docs/WEEM_ISLER.md ]; then
  awk '/^## Şu an/{f=1; next} /^## /{if (f) exit} f && NF' docs/WEEM_ISLER.md | head -8
fi

# 2) En son devir notunun başı
if [ -f AI_HANDOFF.md ]; then
  echo "-- Son devir notu --"
  awk '/^## /{n++} n==1' AI_HANDOFF.md | head -6
fi

# 3) Ortam
echo "-- Ortam --"
jv=$(java -version 2>&1 | grep -iE '(openjdk|java) version' | head -1)
echo "JDK: ${jv:-YOK}"

code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 \
  https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml || true)
if [ "$code" = "200" ]; then
  echo "dl.google.com: açık (Android modülleri burada derlenebilir)"
  if [ -x tools/setup-android-sdk.sh ]; then
    if timeout 600 tools/setup-android-sdk.sh >/dev/null 2>&1; then
      echo "Android SDK: hazır"
    else
      echo "Android SDK: kurulamadı (tools/setup-android-sdk.sh)"
    fi
  fi
else
  echo "dl.google.com: KAPALI → Android modülleri burada derlenmez; logic/ burada test edilir, Android katmanı CI'da derlenir. Kullanıcı ortam ağ ayarına dl.google.com ekleyebilir."
fi

# 4) Saf Kotlin mantığın bağımlılıklarını önceden indir (Android'siz build: logic/)
if [ -x ./gradlew ] && [ -f logic/settings.gradle.kts ]; then
  # Maven Central proxy üstünden ara sıra 429 verir: tek işçiyle, bir kez yeniden dene.
  warm() { timeout 600 ./gradlew -p logic --no-daemon -q --max-workers=1 testClasses >/dev/null 2>&1; }
  if warm || { sleep 10; warm; }; then
    echo "logic/: bağımlılıklar hazır, derleniyor"
  else
    echo "logic/: hazırlık başarısız; ilk iş './gradlew -p logic test' ile bak"
  fi
fi

exit 0
