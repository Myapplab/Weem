#!/usr/bin/env bash
# WEEM testleri kısa çıktıyla (ajan usage'ı için): Gradle sessiz koşar; ekrana yalnız özet, derleme hataları ve
# başarısız testlerin ilk satırları gelir. Tam günlük: build/test-sh.log.
#
#   tools/test.sh                      logic/ bütün testler (saf Kotlin; her ortamda)
#   tools/test.sh logic:map            tek logic modülü: app core capture orders economics scooter map link remote
#   tools/test.sh logic:map -t Router  yalnız adında Router geçen testler (tek modüllü hedeflerde)
#   tools/test.sh app                  Android birim testleri (Robolectric), :app:testDebugUnitTest
#   tools/test.sh android              bütün Android birim testleri
#   tools/test.sh lint:app | lint      Android lint (lintDebug)
#   tools/test.sh compile:map          yalnız derleme (compileDebugKotlin)
#   --rerun                            Gradle "güncel" saysa da test görevlerini yeniden koştur
#
# Birden çok hedef olur: tools/test.sh logic:map map lint:map. Android hedefleri Android SDK ve dl.google.com ister.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 2
export LC_ALL=C.UTF-8 # POSIX yerel ayarında Türkçe test adları derleyicide kırılır

mods="app core capture orders economics scooter map link remote"
targets=() logic_tasks=() root_tasks=() results=() filter="" rerun=""
while [ $# -gt 0 ]; do
  case "$1" in
    -t) filter="${2:?-t filtre ister}"; shift ;;
    --rerun) rerun=1 ;;
    *) targets+=("$1") ;;
  esac
  shift
done
[ ${#targets[@]} -eq 0 ] && targets=(logic)

add() { # $1 dizi adı, $2 görev; test görevine filtre ve --rerun eklenir
  local -n arr=$1
  arr+=("$2")
  if [[ $2 == *test || $2 == *UnitTest ]]; then
    [ -n "$filter" ] && arr+=(--tests "*$filter*")
    [ -n "$rerun" ] && arr+=(--rerun)
  fi
}
for t in "${targets[@]}"; do
  case "$t" in
    logic) [ -n "$filter" ] && { echo "-t yalnız tek modüllü hedefle (logic:<modül>)"; exit 2; }; add logic_tasks test
      for m in $mods; do results+=("logic/$m:test"); done ;;
    logic:*) add logic_tasks ":${t#logic:}:test"; results+=("logic/${t#logic:}:test") ;;
    android) add root_tasks testDebugUnitTest; for m in $mods; do results+=("$m:testDebugUnitTest"); done ;;
    lint) add root_tasks lintDebug ;;
    lint:*) add root_tasks ":${t#lint:}:lintDebug" ;;
    compile:*) add root_tasks ":${t#compile:}:compileDebugKotlin" ;;
    *) [[ " $mods " == *" $t "* ]] || { awk 'NR>1 && !/^#/{exit} NR>1' "$0"; exit 2; }
      add root_tasks ":$t:testDebugUnitTest"; results+=("$t:testDebugUnitTest") ;;
  esac
done

mkdir -p build
log=build/test-sh.log
marker=build/test-sh.start
: >"$log"
touch "$marker"
start=$SECONDS

gradle() { # $1 = logic | root, kalanlar görevler
  local where=$1; shift
  local args=(-q --console=plain "$@")
  [ "$where" = logic ] && args=(-p logic "${args[@]}")
  ./gradlew "${args[@]}" >>"$log" 2>&1 && return 0
  if grep -q 'InvalidPathException: Malformed input' "$log"; then
    # POSIX yerel ayarla açılmış eski daemon: durdur, bir kez daha dene.
    ./gradlew -q --stop >/dev/null 2>&1; pkill -f KotlinCompileDaemon 2>/dev/null; sleep 1
    echo "== yeniden: eski daemon durduruldu ==" >>"$log"
    ./gradlew "${args[@]}" >>"$log" 2>&1; return
  fi
  if grep -qiE 'status code 429|Too Many Requests|could not resolve|Could not GET' "$log"; then
    # Maven Central proxy üstünden ilk indirmede 429/çözümleme hatası verebilir: tek işçiyle bir kez daha.
    echo "== yeniden: indirme hatası, tek işçi ==" >>"$log"
    ./gradlew "${args[@]}" --max-workers=1 >>"$log" 2>&1; return
  fi
  return 1
}

status=0
[ ${#logic_tasks[@]} -gt 0 ] && { gradle logic "${logic_tasks[@]}" || status=1; }
[ ${#root_tasks[@]} -gt 0 ] && { gradle root "${root_tasks[@]}" || status=1; }

# Derleme hataları (yol kısaltılır)
grep -E '^e: ' "$log" | sed "s|file://$PWD/||" | sort -u | head -20

# Başarısız testler ve sayılar: bu koşuda yazılan JUnit XML'leri
find logic/*/build/test-results */build/test-results -name 'TEST-*.xml' -newer "$marker" 2>/dev/null |
  python3 -I -c '
import sys, xml.etree.ElementTree as ET
n = f = s = 0
for path in sys.stdin.read().split():
    try:
        r = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    n += int(r.get("tests", 0)); s += int(r.get("skipped", 0))
    for tc in r.iter("testcase"):
        e = tc.find("failure")
        if e is None:
            e = tc.find("error")
        if e is None:
            continue
        f += 1
        if f > 15:
            continue
        msg = ((e.get("message") or "").strip().splitlines() or [""])[0][:300]
        print("FAIL %s > %s: %s" % (tc.get("classname", "").rsplit(".", 1)[-1], tc.get("name", ""), msg))
        frames = [l.strip() for l in (e.text or "").splitlines() if l.strip().startswith("at com.kuryelog")]
        for l in frames[:3]:
            print("     " + l)
print("TESTS %d %d %d" % (n, f, s))
' >build/test-sh.sum
grep -v '^TESTS ' build/test-sh.sum
read -r _ n f s < <(grep '^TESTS ' build/test-sh.sum || echo "TESTS 0 0 0")

# Gradle'ın hata özeti (test dışı nedenler: lint, yapılandırma, bağımlılık)
if [ $status -ne 0 ] && [ "$f" = 0 ]; then
  awk '/^\* What went wrong:/{p=1; c=0; next} /^\* (Try|Get more help|Exception is):/{p=0} p && NF && c<12 {print; c++}' "$log" | head -24
fi

# Testi olup bu koşuda sonucu yazılmayan modüller: Gradle girdileri değişmedi saydı, testler koşmadı
stale=()
for r in "${results[@]}"; do
  dir=${r%%:*}
  [ -d "$dir/src/test" ] || continue
  [ -n "$(find "$dir/build/test-results/${r#*:}" -name 'TEST-*.xml' -newer "$marker" 2>/dev/null | head -1)" ] || stale+=("$dir")
done
note=""
[ ${#stale[@]} -gt 0 ] && note=" · güncel sayılıp koşmayan: ${stale[*]} (zorlamak için --rerun)"

dur=$((SECONDS - start))
if [ $status -eq 0 ] && [ ${#results[@]} -eq 0 ]; then
  echo "TAMAM · ${dur} sn"
elif [ $status -eq 0 ]; then
  echo "TAMAM · $n test, 0 hata, $s atlandı${note} · ${dur} sn"
else
  echo "HATA · $n test, $f başarısız · ${dur} sn · tam günlük: $log"
fi
exit $status
