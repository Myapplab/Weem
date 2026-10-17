#!/usr/bin/env bash
# Scooter şifrelemesi ve el sıkışma, sabitlenmiş Python referansına (nb_crypto.py, Apache-2.0) karşı: kurye_nav'ın
# ninebot-logger/tests/reference_parity.py betiği Java yerine WEEM'in Kotlin uygulamasını (logic/scooter, ReferenceProbe) çalıştırır.
# CI dışı, elle (SCOOTER.md 13.1): kripto ya da protokol kodu değişince.
#
#   git clone --depth 1 -b rawstats https://github.com/Myapplab/kurye_nav /tmp/ref-rawstats
#   pip install --target /tmp/nb-pylib pycryptodome==3.23.0
#   bash tools/scooter-reference-parity.sh /tmp/ref-rawstats /tmp/nb-pylib
#
# Beklenen son satır: "kotlin parity OK: 320 vectors" (64 tam el sıkışma dahil).
set -euo pipefail
ref=${1:?kurye_nav (rawstats) klonunun yolu}
pylib=${2:-}
root=$(cd "$(dirname "$0")/.." && pwd)
"$root/gradlew" -q -p "$root/logic" :scooter:testClasses
stdlib=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2" -name 'kotlin-stdlib-[0-9]*.jar' ! -name '*-sources.jar' | sort | tail -1)
classes="$root/logic/scooter/build/classes/kotlin"
python3 -I - "$pylib" "$ref/ninebot-logger/tests" "$classes/main:$classes/test:$stdlib" <<'PY'
import subprocess, sys
pylib, tests, cp = sys.argv[1], sys.argv[2], sys.argv[3]
if pylib:
    sys.path.insert(0, pylib)
sys.path.insert(0, tests)
import reference_parity as rp

real_run = subprocess.run
def run_kotlin(cmd, *args, **kwargs):
    if cmd[0] == 'javac':
        return subprocess.CompletedProcess(cmd, 0)
    if cmd[0] == 'java':
        return real_run(['java', '-cp', cp, 'com.kuryelog.logic.scooter.ReferenceProbe'], *args, **kwargs)
    return real_run(cmd, *args, **kwargs)
rp.subprocess.run = run_kotlin
print('kotlin parity OK:', rp.run()['comparisons'], 'vectors')
PY
