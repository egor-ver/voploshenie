import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = ROOT / "docs/tz-proftest.md"
ADDITIONS = ROOT / "tools/analyst_additions.json"
OUTPUT = ROOT / "app/src/main/assets/test_config.json"


def section(markdown, heading):
    start = markdown.index(heading)
    level = len(heading.split()[0])
    next_heading = re.search(r"^(#{1," + str(level) + r"})\s+", markdown[start + len(heading):], re.M)
    end = start + len(heading) + next_heading.start() if next_heading else len(markdown)
    return markdown[start:end]


def tables(markdown, heading):
    result = []
    for block in section(markdown, heading).splitlines():
        if not block.startswith("|"):
            continue
        cells = [cell.strip() for cell in block.strip().strip("|").split("|")]
        if all(re.fullmatch(r":?-{3,}:?", cell) for cell in cells):
            continue
        result.append(cells)
    if not result:
        return []
    header = result[0]
    return [dict(zip(header, row)) for row in result[1:] if len(row) <= len(header)]


def choices_from_breaks(value):
    return [re.sub(r"^\s*[а-яА-ЯёЁ]\)\s*", "", item.strip()) for item in re.split(r"<br\s*/?>", value) if item.strip()]


def build():
    markdown = SPEC.read_text(encoding="utf-8")
    additions = json.loads(ADDITIONS.read_text(encoding="utf-8"))
    part_specs = [
        ("profile", "### 6.1.", "PRF"), ("interests", "### 6.2.", "INT"),
        ("values", "### 6.3.", "VAL"), ("open", "### 6.4.", "OPEN"),
        ("sphere_interest", "### 6.5.", "SPH"), ("tasks", "### 6.6.", "TSK"),
        ("experience", "### 6.7.", "EXP"), ("constraints", "### 6.8.", "NAD"),
        ("needs", "### 6.9.", "NEED"), ("happiness", "### 6.10.", "OPEN"),
        ("consistency", "### 6.11.", "CHK"),
    ]
    types = {"profile": {"PRF01": "text", "PRF02": "single_choice", "PRF03": "text", "PRF04": "text", "PRF05": "scale", "PRF06": "single_choice"}, "interests": "pair", "values": "rank_values", "open": "text", "sphere_interest": "likert5", "tasks": "single_choice", "experience": "text_energy5", "constraints": "single_choice", "needs": "likert5", "happiness": "text", "consistency": "pair"}
    questions = {}
    for key, heading, prefix in part_specs:
        rows = tables(markdown, heading)
        normalized = []
        for row in rows:
            qid = next((v for v in row.values() if re.fullmatch(prefix + r"\d{2}", v)), None)
            if not qid:
                continue
            item = {"id": qid, "fields": row}
            item["type"] = types[key].get(qid) if isinstance(types[key], dict) else types[key]
            if key in ("open", "happiness"):
                item["result_section"] = "your_words"
            if key == "profile" and qid in ("PRF02", "PRF06"):
                item["options"] = []
                for value in row["Значения / правила"].split(";"):
                    course_group = re.fullmatch(r"\s*([\d, ]+) курс (.+?)\s*", value)
                    if qid == "PRF02" and course_group:
                        item["options"].extend(f"{course.strip()} курс {course_group.group(2)}" for course in course_group.group(1).split(","))
                    else:
                        item["options"].append(value.strip())
            if key == "profile" and qid == "PRF05":
                item["scale"] = {"min": 1, "max": 10}
            if key == "experience":
                item["energy"] = {"min": 1, "max": 5}
            if key in ("sphere_interest", "needs"):
                item["scale"] = {"min": 1, "max": 5}
            if key == "values":
                item["ranking"] = {"top": 3, "least": 2}
            if key == "tasks":
                item.update(domain=row["Домен"], prompt=row["Задание и варианты"].split("<br>")[0], options=choices_from_breaks("<br>".join(row["Задание и варианты"].split("<br>")[1:])), correct=row["Верный"])
            elif key == "constraints":
                value = row.get("Варианты (ID: a, b, c, d по порядку)", "")
                item["options"] = [{"id": match.group(1), "text": match.group(2).strip()} for match in re.finditer(r"NAD\d{2}\.([a-d])\s*(.*?)(?=NAD\d{2}\.[a-d]|$)", value)]
                if qid == "NAD07":
                    item["type"] = "text"
                    item["result_section"] = "nad_summary"
            elif key in ("interests", "consistency"):
                item.update(type_a=row.get("Тип А"), type_b=row.get("Тип Б"))
                if key == "consistency":
                    item["linked"] = row["Связан с"]
            elif key == "sphere_interest":
                item["sphere_id"] = row["Сфера (sphere_id)"]
            elif key == "values":
                item["anchor"] = row["Якорь Шейна (для результата)"]
            normalized.append(item)
        questions[key] = normalized
    profiles = []
    for row in tables(markdown, "### 7.5."):
        profiles.append({"id": row["sphere_id"], "riasec": [v.strip() for v in row["RIASEC (×3, ×2, ×1)"].split(",")], "domains": [v.strip() for v in row["Домены (×3, ×2, ×1)"].split(",")], "values": [v.strip() for v in row["Ценности"].split(",")], "needs": [v.strip() for v in row["Условия"].split(",")]})
    sph_by_id = {q["id"]: q["sphere_id"] for q in questions["sphere_interest"]}
    for profile in profiles:
        ids = [qid for qid, sid in sph_by_id.items() if sid == profile["id"]]
        profile["questions"] = ids
    sphere_details = tables(markdown, "### 9.4.")
    for profile, details in zip(profiles, sphere_details):
        profile["details"] = details
        profile["professions"] = additions["professions"][profile["id"]]

    texts = {}
    for key, heading in [("riasec", "### 9.1."), ("levels", "### 9.2."), ("anchors", "### 9.3."), ("spheres", "### 9.4."), ("disclaimer", "### 9.5."), ("consistency", "### 9.6."), ("experiments", "### 9.7.")]:
        raw = section(markdown, heading)
        raw = re.sub(r"(?m)^НЕОЬЯЗАТЕЛЬНОЕ.*$", "", raw)
        texts[key] = {"table": tables(markdown, heading), "paragraphs": [line.strip().strip("«»") for line in raw.splitlines() if line.strip() and not line.startswith("#") and not line.startswith("|") ]}
    h_rows = tables(markdown, "### 8.8.")
    parts = [{"id": name, "questions": questions[name]} for name, _, _ in part_specs]
    needs_part = next(part for part in parts if part["id"] == "needs")
    needs_questions = questions["needs"]
    selection_intro = next(re.search(r"После оценки [^.]+\.", line).group(0) for line in section(markdown, "### 6.9.").splitlines() if re.search(r"После оценки [^.]+\.", line))
    needs_part["steps"] = [
        {"id": "ratings", "type": "likert5", "question_ids": [question["id"] for question in needs_questions]},
        {"id": "pick3", "type": "pick3", "prompt": selection_intro, "required": True, "min": 3, "max": 3, "options": [{"id": question["id"], "text": question["fields"]["Условие"]} for question in needs_questions]},
    ]
    config = {"version": 1, "parts": parts, "questions": questions, "spheres": profiles, "levels": texts["levels"], "hints": h_rows, "texts": texts, "anchors": {q["id"]: q["anchor"] for q in questions["values"]}, "analyst": additions}
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    return config


if __name__ == "__main__":
    build()
