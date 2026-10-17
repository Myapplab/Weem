"""Redaction for KuryeLog screen text (step 0 before ANY analysis of real exports).

Line-based and label-aware: personal values follow known UI labels on Wolt/Bolt cards.
Keeps UI labels, prices, minutes, km, restaurant names/addresses (business data) and
order-id SHAPE (ids are replaced by a keyed hash so joins inside one dataset still work).

python3 -I redact.py --selftest
Library: Redactor(secret).lines(pkg, lines) -> redacted lines
"""
import hashlib
import hmac
import re
import sys

AZ_PHONE = re.compile(r"(?<![\d+])\(?\s*(?:\+?\s*994\s*|0)?\s*\(?\s*(10|12|50|51|55|60|70|77|99)\s*\)?[\s.\-]*(\d{3})[\s.\-]*(\d{2})[\s.\-]*(\d{2})(?!\d)")
INTL_PHONE = re.compile(r"(?<!\d)\+\s*\d[\d\s().\-]{8,16}\d")
ORDER_ID = re.compile(r"#([A-Za-z0-9]{3,12})\b")
CODE = re.compile(r"(?i)(domofon(?:un)?(?:\s+kodu?)?|qap[ıi](?:n[ıi]n)?\s+kodu?|door\s*code|entrance\s*code|intercom|код(?:\s+\w+)?|kod)\s*[:=\-–]?\s*#?\s*[0-9][0-9#*\s]{0,12}")
PERSON_ID_LINE = re.compile(r"^(?P<name>[^#\n]{1,40}?)\s+(?P<id>#[A-Za-z0-9]{3,12})$")  # Bolt: "Alici A. #8P6D2"
ID_PERSON_LINE = re.compile(r"^(?P<id>#[A-Za-z0-9]{3,12})\s+(?P<name>[^#\d\n]{1,40})$")  # "#014 Aysel T." (one OCR line)
ID_ONLY_LINE = re.compile(r"^#[A-Za-z0-9]{3,12}$")
# G1 (13 Oct 2026): "Aysel T." / "O Aysel T." (Wolt timeline icon) anywhere is a customer's name.
NAME_INITIAL = re.compile(r"^(?:\S{1,2}\s+)?((?:[^\W\d_]|['’-]){2,21}) ([^\W\d_])\.$")


def is_name_initial(s):
    m = NAME_INITIAL.match(s)
    return bool(m) and m.group(1)[:1].isupper() and m.group(2).isupper()
NUMERIC_HINT = re.compile(r"\d+\s*(min|km|₼|azn)", re.I)
NAME_AFTER = {"dropoff to", "drop off to"}
ADDR_AFTER = {"address", "customer drop-off", "customer drop off", "delivery address", "drop-off address", "unvan", "ünvan", "ünvanı",
              "çatdırılma ünvanı", "adres", "адрес"}
NOTE_AFTER = {"instructions", "notes", "note", "additional note", "address details", "qeyd", "qeydlər", "elave qeyd", "əlavə qeyd", "təlimat",
              "təlimatlar", "комментарий", "инструкции"}
SMALL_AFTER = {"floor", "apartment", "flat", "entrance", "building", "block", "kat", "daire", "mertebe", "mərtəbə", "menzil", "mənzil", "giriş",
               "bina", "blok", "podyezd", "этаж", "квартира", "подъезд"}
COLLECT_AFTER = {"collect order", "pick up order", "pickup order"}  # Wolt pickup card: "#id" then the customer's name
SECTION_END = NAME_AFTER | ADDR_AFTER | NOTE_AFTER | SMALL_AFTER | COLLECT_AFTER | {
    "translate", "call", "chat", "items", "info", "order details", "from", "delivery issues?", "difficulties with the delivery?",
    "send a message", "order delivered!", "order delivered", "delivered", "arrived", "slide to confirm", "check id", "accept", "decline",
    "delivery timeline", "get help", "view"}
MENU = {"delivery settings", "insights", "finances", "stats", "earn extra", "benefits and insurance", "referrals", "help center",
        "all deliveries", "earnings", "campaigns", "inbox", "performance", "profile", "help", "invite friends", "about", "settings", "support"}


def norm(s):
    return re.sub(r"\s+", " ", s.strip().lower())


class Redactor:
    def __init__(self, secret: bytes):
        self.secret = secret

    def h(self, kind, value, n=6):
        return hmac.new(self.secret, (kind + ":" + norm(value)).encode(), hashlib.sha256).hexdigest()[:n].upper()

    def shape(self, kind, value):
        """Shape-preserving keyed pseudonym: digits->digits, A-Z->A-Z, a-z->a-z (parser sees the same pattern)."""
        d = hmac.new(self.secret, (kind + ":" + value).encode(), hashlib.sha256).digest()
        out = []
        for i, ch in enumerate(value):
            b = d[i % len(d)]
            out.append(str(b % 10) if ch.isdigit() else chr(65 + b % 26) if ch.isupper() else chr(97 + b % 26) if ch.islower() else ch)
        return "".join(out)

    def free(self, s):
        s = AZ_PHONE.sub("[tel]", s)
        s = INTL_PHONE.sub("[tel]", s)
        s = CODE.sub("[code]", s)
        s = ORDER_ID.sub(lambda m: "#" + self.shape("id", m.group(1)), s)
        return s

    def lines(self, pkg, lines):
        out, mode, after_label = [], None, 0
        small_left, prev_small = 0, False
        menu = sum(1 for x in lines if norm(x) in MENU) >= 3
        # G1: the profile header before the first menu entry ("Name Surname ARGO ., Online") is the courier's own account.
        first_menu = next((k for k, x in enumerate(lines) if norm(x) in MENU), len(lines)) if menu else 0
        for i, raw in enumerate(lines):
            n = norm(raw)
            was_small, prev_small = prev_small, False
            if menu and (i == 0 or i < first_menu):
                out.append(raw if n == "online" else "[self], Online" if n.endswith(", online") else "[self]")
                continue
            if n in SECTION_END or n.startswith("items (") or n.startswith("drop off in") or n.startswith("dropoff "):
                out.append(raw)
                mode = (("name" if n in NAME_AFTER else "addr" if n in ADDR_AFTER else "note" if n in NOTE_AFTER else
                         "small" if n in SMALL_AFTER else "collect" if n in COLLECT_AFTER else None))
                if mode == "small":  # OCR may emit "Entrance / Floor / Apartment" first and the values after them
                    small_left = small_left + 1 if was_small else 1
                    prev_small = True
                after_label = 0
                continue
            m = PERSON_ID_LINE.match(raw.strip())
            if m and not NUMERIC_HINT.search(raw):
                out.append("Name" + self.h("name", m.group("name"), 4) + " #" + self.shape("id", m.group("id")[1:]))
                mode = None
                continue
            m = ID_PERSON_LINE.match(raw.strip())
            if m and not NUMERIC_HINT.search(raw) and m.group("name")[:1].isupper():  # "#408 hazırdır" is not a name
                out.append("#" + self.shape("id", m.group("id")[1:]) + " Name" + self.h("name", m.group("name"), 4))
                mode = None
                continue
            if mode == "collect":
                if ID_ONLY_LINE.match(raw.strip()):
                    out.append(self.free(raw))
                    mode = "name_only"  # the line after the order id is the customer's name
                else:
                    out.append("Name" + self.h("name", raw, 4))
                    mode = None
                continue
            if mode == "name_only":
                out.append("Name" + self.h("name", raw, 4))
                mode = None
                continue
            if mode == "name":
                out.append("Name" + self.h("name", raw, 4))
                mode, after_label = "addr", 1  # Wolt: name line is followed by the address line
                continue
            if mode == "addr":
                out.append("[addr:" + self.h("addr", raw) + "]")
                mode = None
                continue
            if mode == "note":
                out.append("[note]")
                continue  # every line until the next known label
            if mode == "small":
                out.append("[n]")
                small_left -= 1
                if small_left <= 0:
                    mode, small_left = None, 0
                continue
            # G1: a Wolt order id read without its "Collect order" label (map labels in between) is still followed by the name;
            # after "From" it is followed by the restaurant.
            if ID_ONLY_LINE.match(raw.strip()) and (norm(lines[i - 1]) if i else "") != "from":
                out.append(self.free(raw))
                mode = "name_only"
                continue
            if is_name_initial(raw.strip()):
                out.append("Name" + self.h("name", raw, 4))
                continue
            out.append(self.free(raw))
        return out


def selftest():
    r = Redactor(b"test-secret")
    wolt = ["Dropoff to", "Alici B. Soyad", "Sintetik kuce 81, Bakı", "Notes", "BLOCK B\n(Domofon kodu: 68)", "Mertebe 6, tel 055 123 45 67",
            "Translate", "Order details", "From", "Punkap", "#408", "Order delivered!"]
    bolt = ["Drop off in -1 min", "Alici A. #8P6D2", "Items (1)", "Address", "Mehdi Huseyn 40, Baku", "Instructions", "Meet at my door",
            "Floor", "3", "Arrived", "Slide to confirm"]
    offer = ["Ready in 3 min", "Decline", "₼2.31", "2 stops (2.8 km) • 8–16 min", "Izmir Doner Tantuni No1", "9RQW+344 Baku, Baku, AZ1000",
             "Customer drop-off", "Mirzəağa Əliyev 249, Bakı, 1014", "Accept"]
    menu = ["Test Courier", "Online", "Delivery settings", "Insights", "Finances", "Stats"]
    a, b, c, d = r.lines("w", wolt), r.lines("b", bolt), r.lines("w", offer), r.lines("w", menu)
    blob = "\n".join(a + b + c + d)
    assert re.search(r"#\d{3}\b", "\n".join(a)), "3-digit id shape lost"
    assert r.lines("b", ["Drop off in 2 min", "Alici E #Q3FYM"]) != r.lines("b", ["Drop off in 2 min", "Alici A. #8P6D2"])
    for secret in ["Alici B", "Sintetik kuce", "68", "123 45 67", "Alici A", "8P6D2", "Mehdi Huseyn 40", "Meet at my door", "Mirzəağa", "Test Courier", "#408"]:
        assert secret not in blob, (secret, blob)
    for keep in ["₼2.31", "2 stops (2.8 km) • 8–16 min", "Izmir Doner Tantuni No1", "9RQW+344 Baku, Baku, AZ1000", "Punkap", "Accept", "Drop off in -1 min"]:
        assert keep in blob, keep
    # N15 (2026-10-07): Wolt pickup card shows the customer's name under "#id"; OCR may list labels before values.
    pick = r.lines("w", ["Pickup from", "TEST RESTORAN", "Test küç. 1, AZ1010 Baku", "Notes", "Restoran həyətin içindədir",
                         "Translate", "Collect order", "#014", "Aysel T.", "ORDER DETAILS", "1 x Test", "Collect"])
    cols = r.lines("w", ["Dropoff to", "Aysel Testova", "Yasamal, Bakı", "Entrance", "Floor", "Apartment", "Menzil33", "4", "33",
                         "Order details"])
    rows = r.lines("w", ["Floor", "3", "Apartment", "12", "Order details", "₼3.94"])
    one = r.lines("w", ["Collect order", "#014 Aysel T.", "ORDER DETAILS"])
    az = r.lines("b", ["Ünvan", "Nəsimi r., Şərifzadə 12", "Giriş", "2", "Qeydlər", "Zəngi basmayın"])
    assert az[1].startswith("[addr:") and az[3] == "[n]" and az[5] == "[note]", az
    blob2 = "\n".join(pick + cols + rows + one)
    for secret in ["Aysel", "Testova", "Menzil33", "Yasamal", "#014"]:
        assert secret not in blob2, (secret, blob2)
    assert cols[6:9] == ["[n]", "[n]", "[n]"] and cols[9] == "Order details", cols
    assert rows == ["Floor", "[n]", "Apartment", "[n]", "Order details", "₼3.94"], rows
    assert re.fullmatch(r"#\d{3}", pick[7]) and pick[8].startswith("Name"), pick
    assert one[1].startswith("#") and "Name" in one[1], one
    ready = r.lines("w", ["#408 hazırdır"])
    assert ready[0].endswith(" hazırdır") and ready[0] != "#408 hazırdır", ready
    for keep in ["TEST RESTORAN", "Test küç. 1, AZ1010 Baku", "Collect order", "ORDER DETAILS", "1 x Test", "Collect"]:
        assert keep in pick, keep
    print("redact selftest OK")
    for x in (a, b, c, d, pick, cols, rows, one, az):
        print(x)


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        selftest()
