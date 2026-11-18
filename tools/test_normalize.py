import importlib.util
import json
import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("normalize_catalog", ROOT / "tools/normalize_catalog.py")
normalize_catalog = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = normalize_catalog
spec.loader.exec_module(normalize_catalog)


class NormalizeCatalogTest(unittest.TestCase):
    def test_hybrid_and_office_formats_and_search_url_detection(self):
        self.assertEqual(normalize_catalog.normalize_format("офис/гибрид/удаленно"), "hybrid")
        self.assertEqual(normalize_catalog.normalize_format("гибрид"), "hybrid")
        self.assertEqual(normalize_catalog.normalize_format("офис"), "offline")
        self.assertEqual(normalize_catalog.url_kind("https://skillbox.ru/course/marketing-researches/"), "program")
        self.assertEqual(normalize_catalog.url_kind("https://stepik.org/catalog/search?search=Excel"), "search")

    def test_date_cases_and_ambiguous_inputs(self):
        cases = {
            "сентябрь 2026 – июнь 2027": ("2026-09", "2027-06"),
            "ноябрь 2026 – январь 2027": ("2026-11", "2027-01"),
            "6–16 ноября 2026": ("2026-11-06", "2026-11-16"),
            "24 октября 2026": ("2026-10-24",),
            "октябрь 2026": ("2026-10",),
            "2027-04-01": ("2027-04-01",),
            "16.01.2027-15.03.2027": ("2027-01-16", "2027-03-15"),
            "20 октября – 27 ноября 2026": ("2026-10-20", "2026-11-27"),
            "29–30 октября 2026": ("2026-10-29", "2026-10-30"),
            "30 октября – 30 ноября 2026": ("2026-10-30", "2026-11-30"),
            "22–23 октября 2026": ("2026-10-22", "2026-10-23"),
            "ноябрь 2026 – январь 2027": ("2026-11", "2027-01"),
            "2026": ("2026",),
            "круглогодично": (),
        }
        for source, expected in cases.items():
            with self.subTest(source=source):
                dates, rolling = normalize_catalog.normalize_dates(source)
                self.assertEqual(tuple(item["date"] for item in dates), expected)
                self.assertEqual(rolling, source == "круглогодично")
        self.assertEqual(normalize_catalog.normalize_dates("обновлен в 2026"), ([], False))
        self.assertEqual(normalize_catalog.normalize_dates("8 октября / 22 октября / 5 ноября 2026"), ([], False))

    def test_real_catalog_has_112_records_and_normalized_schema(self):
        source = json.loads((ROOT / "data/catalog.json").read_text(encoding="utf-8"))
        overrides = json.loads((ROOT / "data/catalog_overrides.json").read_text(encoding="utf-8"))
        result = [normalize_catalog.normalize_record(row, overrides) for row in source]
        self.assertEqual(len(result), 112)
        self.assertEqual(len({item["id"] for item in result}), 112)
        required = {"id", "type", "title", "direction", "spheres", "format", "audience_text", "dates", "date_text", "is_rolling", "url", "url_kind"}
        self.assertTrue(all(required <= item.keys() for item in result))
        self.assertTrue(all(item["type"] in {"olympiad", "internship", "course", "event"} for item in result))
        self.assertTrue(all(item["format"] in {None, "remote", "offline", "hybrid"} for item in result))
        self.assertEqual(result[12]["type"], "event")
        self.assertFalse(result[7]["url"].endswith("ё"))
        self.assertGreaterEqual(sum(bool(item["dates"]) for item in result), 15)

    def test_generation_is_deterministic_and_writes_report(self):
        script = ROOT / "tools/normalize_catalog.py"
        subprocess.run(["py", "-I", str(script)], check=True, cwd=ROOT)
        target = ROOT / "app/src/main/assets/catalog.json"
        first = target.read_bytes()
        subprocess.run(["py", "-I", str(script)], check=True, cwd=ROOT)
        self.assertEqual(target.read_bytes(), first)
        items = json.loads(first)
        self.assertEqual(len(items), 112)
        self.assertTrue((ROOT / "tools/normalize_report.md").is_file())


if __name__ == "__main__":
    unittest.main()
