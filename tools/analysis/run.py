"""KuryePlus offline analysis: export(s) -> Markdown + CSV report (standard library only, no network).

python3 -I tools/analysis/run.py EXPORT.zip|DIR [EXPORT2 ...] --out OUT_DIR [--redact] [--hub LAT,LON | --hub default]

Every input is checked on its own: an input without its own ANALIZ_ANONIM.json (the full "share all
records" ZIP) refuses the whole run unless --redact is given; then anonymize.py anonymises the
non-anonymous inputs in memory before they are merged with the anonymous ones, and the raw data is
never written or analysed. A marker in one input never vouches for another.
Exit codes: 0 report written, 2 usage error, 3 refused (not anonymous), 4 unreadable input.
"""
import os
import sys
import time

sys.dont_write_bytecode = True  # keep the checkout clean (no __pycache__ next to the tool)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anonymize  # noqa: E402
import descriptive  # noqa: E402
import kl_export as K  # noqa: E402
import report  # noqa: E402

REFUSAL = ("Anonim olmayan dışa aktarım (kendi ANALIZ_ANONIM.json dosyası yok ya da bu dosya girdideki her dosyanın yanında değil). "
           "Uygulamada 'Analiz için paylaş (anonim)' ile dışa aktarın ya da --redact ekleyin (anonimleştirme önce bellekte yapılır, "
           "ham veri yazılmaz).")


def parse(argv):
    """-> (paths, opts), or (None, None) for --help or a malformed option."""
    paths, opts, i = [], {"out": None, "redact": False, "hub": None}, 0
    while i < len(argv):
        a = argv[i]
        if a in ("--out", "--hub") and i + 1 >= len(argv):
            return None, None
        if a == "--out":
            opts["out"] = argv[i + 1]; i += 2
        elif a == "--redact":
            opts["redact"] = True; i += 1
        elif a == "--hub":
            v = argv[i + 1]; i += 2
            if v == "default":
                opts["hub"] = K.HUB_DEFAULT
            else:
                try:
                    lat, lon = (float(x) for x in v.split(","))
                except ValueError:
                    return None, None
                if not (-90 <= lat <= 90 and -180 <= lon <= 180):
                    return None, None
                opts["hub"] = (lat, lon)
        elif a in ("-h", "--help") or a.startswith("--"):
            return None, None
        else:
            paths.append(a); i += 1
    return paths, opts


def prepare(paths, redact, log=print):
    """Load and gate every input on its own -> (exit code, anonymous merged K.Export or None, anonymizer counts or None).

    Redaction comes before any analysis (SPEC 2.1.0 section 18.1), so the gate is per input, never on the
    merged export (which keeps the first marker it sees). Without --redact any non-anonymous input refuses
    the run; with --redact the non-anonymous inputs are merged (one pseudonym secret for all of them) and
    anonymised in memory, then merged with the anonymous inputs. Raw rows never reach the result.
    """
    parts = []
    for p in paths:
        try:
            part = K.from_memory(K.read_files(p), os.path.basename(os.path.normpath(p)))
        except (OSError, ValueError) as e:
            log("Okunamadı: %s" % e)
            return 4, None, None
        if not part.days:
            log("Dışa aktarımda gün klasörü yok (YYYY-MM-DD/...): %s" % p)
            return 4, None, None
        parts.append(part)
    raw = [part for part in parts if not part.anonymous]
    if raw and not redact:
        log(REFUSAL)
        log("Anonim olmayan girdi: %s" % ", ".join(part.source for part in raw))
        return 3, None, None
    if redact and not raw:
        log("Dışa aktarım zaten anonim; --redact gerekmiyor.")
    note = anon_raw = None
    if raw:
        merged_raw = K.Export(source=", ".join(part.source for part in raw))
        for part in raw:
            merged_raw.add(part.files())
            merged_raw.ignored.extend(part.ignored)
        a = anonymize.Anonymizer()  # its secret dies with it
        anon_raw, note = a.export(merged_raw), dict(a.counts)
    exp = K.Export(source=", ".join(part.source for part in parts))
    for part in parts:  # input order; the anonymised raw inputs go in where the first of them was
        if part.anonymous:
            exp.add(part.files())
            exp.ignored.extend(part.ignored)
        elif anon_raw is not None:
            exp.add(anon_raw)
            anon_raw = None
    return 0, exp, note


def main(argv, log=print):
    paths, opts = parse(argv)
    if not paths or not opts or not opts["out"]:
        log(__doc__)
        return 2
    t0 = time.time()
    code, exp, note = prepare(paths, opts["redact"], log)
    if code:
        return code
    res = descriptive.analyse(exp, opts["hub"])
    files = report.build(res, opts["out"])
    log("Rapor: %s (%d dosya, %d gün, %.1f s)%s" % (os.path.join(opts["out"], "rapor.md"), len(files), len(exp.days), time.time() - t0,
                                                    "; anonimleştirme: %s" % ", ".join("%s=%d" % kv for kv in sorted(note.items())) if note else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
