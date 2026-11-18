import json
import re
from pathlib import Path
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]
MONTHS = {
    "января": 1, "февраля": 2, "марта": 3, "апреля": 4, "мая": 5,
    "июня": 6, "июля": 7, "августа": 8, "сентября": 9,
    "октября": 10, "ноября": 11, "декабря": 12,
    "январь": 1, "февраль": 2, "март": 3, "апрель": 4, "май": 5,
    "июнь": 6, "июль": 7, "август": 8, "сентябрь": 9,
    "октябрь": 10, "ноябрь": 11, "декабрь": 12,
}
DATE = r"(?P<day>\d{1,2})\s+(?P<month>января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\s+(?P<year>20\d{2})"


def iso(year, month, day=None):
    return f"{year:04d}-{month:02d}" + (f"-{day:02d}" if day is not None else "")


def normalize_dates(value):
    source = value.strip()
    if not source:
        return [], False
    if "круглогодично" in source.lower():
        return [], True
    if re.fullmatch(r"20\d{2}", source):
        return [{"kind": "start", "date": source, "precision": "year"}], False
    numeric = re.fullmatch(r"(\d{2})\.(\d{2})\.(20\d{2})\s*[-–—]\s*(\d{2})\.(\d{2})\.(20\d{2})", source)
    if numeric:
        a, b = numeric.group(1, 2, 3), numeric.group(4, 5, 6)
        return [
            {"kind": "start", "date": iso(int(a[2]), int(a[1]), int(a[0])), "precision": "day"},
            {"kind": "end", "date": iso(int(b[2]), int(b[1]), int(b[0])), "precision": "day"},
        ], False
    if re.fullmatch(r"20\d{2}-\d{2}-\d{2}", source):
        return [{"kind": "start", "date": source, "precision": "day"}], False
    months = "января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря|январь|февраль|март|апрель|май|июнь|июль|август|сентябрь|октябрь|ноябрь|декабрь"
    period = re.fullmatch(rf"\s*({months})\s+(20\d{{2}})\s*[–—-]\s*({months})\s+(20\d{{2}})\s*", source, re.I)
    if period:
        m1, y1, m2, y2 = period.groups()
        return [
            {"kind": "start", "date": iso(int(y1), MONTHS[m1.lower()]), "precision": "month"},
            {"kind": "end", "date": iso(int(y2), MONTHS[m2.lower()]), "precision": "month"},
        ], False
    one_month = re.fullmatch(rf"\s*({months})\s+(20\d{{2}})\s*", source, re.I)
    if one_month:
        month, year = one_month.groups()
        return [{"kind": "start", "date": iso(int(year), MONTHS[month.lower()]), "precision": "month"}], False
    match = re.fullmatch(DATE, source, re.I)
    if match:
        day, month, year = match.groups()
        return [{"kind": "start", "date": iso(int(year), MONTHS[month.lower()], int(day)), "precision": "day"}], False
    split_range = re.fullmatch(rf"(\d{{1,2}})\s+({months})\s*[–—-]\s*(\d{{1,2}})\s+({months})\s+(20\d{{2}})", source, re.I)
    if split_range:
        day1, month1, day2, month2, year = split_range.groups()
        return [
            {"kind": "start", "date": iso(int(year), MONTHS[month1.lower()], int(day1)), "precision": "day"},
            {"kind": "end", "date": iso(int(year), MONTHS[month2.lower()], int(day2)), "precision": "day"},
        ], False
    day_range = re.fullmatch(rf"(\d{{1,2}})[–—-](\d{{1,2}})\s+({months})\s+(20\d{{2}})", source, re.I)
    if day_range:
        start, end, month, year = day_range.groups()
        return [
            {"kind": "start", "date": iso(int(year), MONTHS[month.lower()], int(start)), "precision": "day"},
            {"kind": "end", "date": iso(int(year), MONTHS[month.lower()], int(end)), "precision": "day"},
        ], False
    return [], False


def normalize_format(value):
    value = value.lower()
    remote = any(word in value for word in ("онлайн", "дистанционно", "удаленно"))
    hybrid = "гибрид" in value
    offline = hybrid or any(word in value for word in ("очно", "офис", "москва", "санкт-петербург"))
    remote = remote or hybrid
    if remote and offline:
        return "hybrid"
    if remote:
        return "remote"
    if offline:
        return "offline"
    return None


def url_kind(url):
    parsed = urlparse(url)
    path = parsed.path.rstrip("/")
    if re.search(r"(?:^|/)search(?:/|$)", path.lower()) or "search=" in parsed.query.lower():
        return "search"
    if not path or path == "":
        return "site_home"
    return "program"


def normalize_record(record, overrides):
    fields = overrides.get(record["id"], {})
    name = record["name"]
    kind = fields.get("type", record.get("type"))
    if "конференция" in name.lower():
        kind = "event"
    url = fields.get("url", record.get("url", "")).replace(".pdfё", ".pdf")
    dates, rolling = normalize_dates(record.get("date", ""))
    result = {
        "id": record["id"], "type": kind, "title": fields.get("title", name),
        "direction": record.get("direction", ""), "spheres": record.get("spheres", []),
        "format": fields.get("format", normalize_format(record.get("format", ""))),
        "audience_text": record.get("audience", ""), "dates": fields.get("dates", dates),
        "date_text": record.get("date", ""), "is_rolling": fields.get("is_rolling", rolling),
        "url": url, "url_kind": fields.get("url_kind", url_kind(url)),
    }
    for key in ("organizer", "city"):
        value = fields.get(key)
        if value:
            result[key] = value
    return result


def main():
    source = json.loads((ROOT / "data/catalog.json").read_text(encoding="utf-8"))
    overrides_path = ROOT / "data/catalog_overrides.json"
    overrides = json.loads(overrides_path.read_text(encoding="utf-8")) if overrides_path.exists() else {}
    output = [normalize_record(record, overrides) for record in source]
    target = ROOT / "app/src/main/assets/catalog.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    no_dates = [item["id"] for item in output if not item["dates"] and not item["is_rolling"]]
    no_format = [item["id"] for item in output if item["format"] is None]
    home = [item["id"] for item in output if item["url_kind"] == "site_home"]
    report = ["# Нормализация каталога", "", f"Записей: {len(output)}", "",
              "## Без нормализованной даты", *(f"- {item}" for item in no_dates), "",
              "## Без формата", *(f"- {item}" for item in no_format), "",
              "## Ссылка на главную", *(f"- {item}" for item in home), ""]
    (ROOT / "tools/normalize_report.md").write_text("\n".join(report), encoding="utf-8")


if __name__ == "__main__":
    main()
