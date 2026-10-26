# WeemIcons.kt üreticisi (A10.4d): python3 tools/icons/weem_icons.py <indirme klasörü> app/src/main/kotlin/com/kuryelog/app/ui/WeemIcons.kt
# Material Symbols Rounded SVG (fonts.gstatic.com, Apache-2.0) indirir; yeni simge için OUTLINED/FILLED listesine ekle.
# S42: dosyaya yalnız kodda kullanılan (`WeemIcons.<Ad>`) simgeler yazılır; liste fazlasını içerebilir (S39'da kullanılmayanlar elle silinmişti).
import re, sys, urllib.request, os, json
DL = sys.argv[1]
OUT = sys.argv[2]
OUTLINED = """add alt_route arrow_back backup bar_chart battery_5_bar battery_alert bluetooth bluetooth_searching chevron_left chevron_right check check_circle
close contrast dark_mode delete devices door_front edit electric_bolt electric_scooter error expand_less expand_more explore fiber_manual_record folder
folder_zip gps_off history home info inventory_2 language light_mode link location_on lock map menu my_location navigation notifications payments person
phone_android play_arrow power_settings_new punch_clock qr_code_scanner receipt_long refresh restaurant route schedule search settings share shield speed
splitscreen sports_score stop storefront thumb_down thumb_up timer touch_app trending_up tune two_wheeler visibility_off warning
partly_cloudy_day content_copy open_in_new remove thermostat water_drop air directions_bike fullscreen fullscreen_exit picture_in_picture_alt
bluetooth_connected bluetooth_disabled download upload filter_list cancel more_vert traffic volume_up vibration sensors analytics task_alt alarm key
flag add_location_alt wifi wifi_off screen_share stop_screen_share layers landscape block straighten battery_full
screen_rotation stay_current_portrait stay_current_landscape""".split()
FILLED = """map receipt_long electric_scooter devices location_on check_circle navigation alt_route battery_5_bar battery_alert play_arrow stop
fiber_manual_record electric_bolt restaurant two_wheeler inventory_2 warning error info home thumb_up thumb_down flag directions_bike layers""".split()
OK = re.compile(r'^[MmLlHhVvCcSsQqTtAaZz0-9.,\- ]+$')
def fetch(name, variant):
    path = os.path.join(DL, f'{name}__{variant}.svg')
    if not os.path.exists(path):
        url = f'https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/{name}/{variant}/24px.svg'
        with urllib.request.urlopen(url, timeout=30) as r:
            data = r.read(200_000)
        open(path, 'wb').write(data)
    svg = open(path, 'rb').read().decode('utf-8', 'strict')
    if 'viewBox="0 -960 960 960"' not in svg:
        raise SystemExit(f'{name}/{variant}: beklenmeyen viewBox')
    ds = re.findall(r'<path d="([^"]+)"', svg)
    if len(ds) != 1 or not OK.match(ds[0]):
        raise SystemExit(f'{name}/{variant}: beklenmeyen yol')
    return ds[0]
def pascal(n):
    return ''.join(p[:1].upper() + p[1:] for p in n.split('_'))
entries = []
for n in OUTLINED:
    entries.append((pascal(n), n, fetch(n, 'default')))
for n in FILLED:
    entries.append((pascal(n) + 'Filled', n + ' (dolu)', fetch(n, 'fill1')))
ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
used = set()
for d, _, fs in os.walk(ROOT):
    if '/build' in d or '/.git' in d or '/src/main' not in d + '/':
        continue
    for f in fs:
        if f.endswith('.kt') and f != 'WeemIcons.kt':
            used.update(re.findall(r'WeemIcons\.(\w+)', open(os.path.join(d, f), encoding='utf-8').read()))
dropped = sorted(e[0] for e in entries if e[0] not in used)
entries = [e for e in entries if e[0] in used]
missing = sorted(used - {e[0] for e in entries})
if missing:
    raise SystemExit(f'listede yok: {missing}')
entries.sort(key=lambda e: e[0])
lines = []
lines.append('package com.kuryelog.app.ui\n')
lines.append('import androidx.compose.ui.graphics.SolidColor')
lines.append('import androidx.compose.ui.graphics.Color')
lines.append('import androidx.compose.ui.graphics.vector.ImageVector')
lines.append('import androidx.compose.ui.graphics.vector.addPathNodes')
lines.append('import androidx.compose.ui.unit.dp\n')
lines.append('/**')
lines.append(' * WEEM simge seti (A10.4d, `docs/TASARIM.md` 7): Material Symbols Rounded, 24 dp, ağırlık 400; `…Filled` seçili/etkin hâl. Emoji yerine.')
lines.append(' * Kaynak: Google Material Symbols (https://fonts.google.com/icons), Apache License 2.0; yollar değiştirilmeden kopyalandı')
lines.append(' * (`viewBox 0 -960 960 960`). Kütüphane eklenmez; her simge ilk kullanımda kurulur. Üretici: `tools/icons/weem_icons.py`.')
lines.append(' */')
lines.append('@Suppress("MaxLineLength")')
lines.append('object WeemIcons {')
for prop, label, d in entries:
    lines.append(f'    /** `{label}` */')
    lines.append(f'    val {prop}: ImageVector by lazy {{ icon("{prop}", "{d}") }}')
lines.append('')
lines.append('    private fun icon(name: String, path: String): ImageVector =')
lines.append('        ImageVector.Builder(name, 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)')
lines.append('            .addGroup(translationY = 960f)')
lines.append('            .addPath(addPathNodes(path), fill = SolidColor(Color.Black))')
lines.append('            .clearGroup()')
lines.append('            .build()')
lines.append('}')
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
print(len(entries), 'icons; kullanılmadığı için yazılmayan:', len(dropped))
