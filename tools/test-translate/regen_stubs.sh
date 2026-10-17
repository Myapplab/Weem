#!/bin/bash
# kullanım: regen_stubs.sh Dosya1 Dosya2 ... (Rawstats dosya adları, .kt'siz). Sonra fixup.py.
RS=${RS_MAIN:-/home/user/kurye_nav/app/src/main/java/com/kuryelog/app}; OUT=${OUT:-${WEEM_ROOT:-/home/user/Weem}/logic/orders/src/main/kotlin/com/kuryelog/logic/orders}
PKG=${PKG:-com.kuryelog.logic.orders}
cd "$(dirname "$0")"
for f in "$@"; do
 { printf "package $PKG\n\n"; grep -E '^import (java|kotlin|org\.json)' $RS/$f.kt; printf 'import com.kuryelog.logic.capture.*\nimport com.kuryelog.logic.core.*\n\n'; python3 stubber.py $RS/$f.kt --task ${TASK:-A3.5} --types types.json; } > $OUT/$f.kt
done
