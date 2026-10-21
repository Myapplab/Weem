#!/usr/bin/env bash
# APK kimlik denetimi (A1.2): paket, sürüm, SDK, imza SHA-256 ve korunan bileşen adları.
# Kullanım: ci/check_identity.sh <apk> <beklenen paket>   örn. app-debug.apk com.kuryelog.app.dev
# ANDROID_HOME gerekir (aapt2, apksigner); keytool JDK'dan.
set -euo pipefail

APK=$1
WANT_PKG=$2
ROOT=$(cd "$(dirname "$0")/.." && pwd)
# Rawstats'ın imza sertifikası (kurye_nav rawstats ci/kuryelog-test.keystore, alias kuryelogtest).
# Anahtar dosyası değişirse burası da değişmeli: o zaman WEEM Rawstats'ın üstüne kurulamaz (kullanıcı kararı).
PIN=f679b77eaedd8cc7363aa79e5e9149a7cf91d413f0f13dfa472e990551f11883

BT=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)
prop() { sed -n "s/^$1=//p" "$ROOT/gradle.properties"; }
fail() { echo "HATA: $*" >&2; exit 1; }

BADGING=$("$BT/aapt2" dump badging "$APK")
line=$(grep '^package:' <<<"$BADGING")
pkg=$(sed -n "s/.* name='\([^']*\)'.*/\1/p" <<<"$line")
code=$(sed -n "s/.* versionCode='\([^']*\)'.*/\1/p" <<<"$line")
name=$(sed -n "s/.* versionName='\([^']*\)'.*/\1/p" <<<"$line")
min=$(sed -n "s/^minSdkVersion:'\(.*\)'/\1/p" <<<"$BADGING")
target=$(sed -n "s/^targetSdkVersion:'\(.*\)'/\1/p" <<<"$BADGING")

[ "$pkg" = "$WANT_PKG" ] || fail "paket $pkg, beklenen $WANT_PKG"
[ "$code" = "$(prop weem.versionCode)" ] || fail "versionCode $code, gradle.properties $(prop weem.versionCode)"
[ "$code" -gt 33 ] || fail "versionCode $code ≤ 33 (Rawstats'ın üstüne kurulamaz)"
case "$name" in "$(prop weem.versionName)"|"$(prop weem.versionName)-dev") ;; *) fail "versionName $name";; esac
[ "$min" = 29 ] || fail "minSdk $min"
[ "$target" = 36 ] || fail "targetSdk $target"

KEY=$(keytool -list -v -keystore "$ROOT/ci/kuryelog-test.keystore" -storepass android -alias kuryelogtest \
  | sed -n 's/.*SHA256: //p' | tr -d ': ' | tr 'A-F' 'a-f')
[ "$KEY" = "$PIN" ] || fail "ci/kuryelog-test.keystore sertifikası Rawstats'ınki değil: $KEY"
GOT=$("$BT/apksigner" verify --print-certs "$APK" | sed -n 's/.*certificate SHA-256 digest: //p' | sort -u)
[ "$GOT" = "$PIN" ] || fail "APK imzası $GOT, beklenen $PIN"

MANIFEST=$("$BT/aapt2" dump xmltree --file AndroidManifest.xml "$APK")
for svc in com.kuryelog.app.WatchService com.kuryelog.app.NotifService; do
  grep -q "android:name(0x01010003)=\"$svc\"" <<<"$MANIFEST" || fail "manifestte $svc yok"
done
# R8 (release, S30): manifestteki servis sınıfları adlarıyla dex'te kalmalı (Rawstats izinleri bu adlara bağlı).
DEX=$(unzip -p "$APK" 'classes*.dex' | LC_ALL=C grep -ao 'Lcom/kuryelog/app/\(WatchService\|NotifService\);' | sort -u | wc -l)
[ "$DEX" -ge 2 ] || fail "dex'te WatchService/NotifService sınıfı yok (R8 kaldırmış ya da adını değiştirmiş olabilir)"
# Yalnız arm64-v8a (S30): başka ABI klasörü APK'yı ≈62 MiB büyütür.
ABIS=$(unzip -Z1 "$APK" 'lib/*' 2>/dev/null | cut -d/ -f2 | sort -u | tr '\n' ' ')
[ "$ABIS" = "arm64-v8a " ] || fail "yerel kütüphane ABI'leri: '$ABIS', beklenen yalnız arm64-v8a"
grep -q 'android:allowBackup(0x01010280)=false' <<<"$MANIFEST" || fail "allowBackup false değil"
grep -q 'android:dataExtractionRules' <<<"$MANIFEST" || fail "dataExtractionRules yok"

echo "Kimlik doğru: $pkg $name ($code), min $min / target $target, imza SHA-256 $GOT, ABI $ABIS, $(stat -c %s "$APK") bayt"
