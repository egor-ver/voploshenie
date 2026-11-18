import json
import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("build_test_config", Path(__file__).with_name("build_test_config.py"))
generator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(generator)


class BuildTestConfigTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config = generator.build()

    def test_generates_expected_question_counts(self):
        expected = {"profile": 6, "interests": 15, "values": 10, "open": 3, "sphere_interest": 14, "tasks": 10, "experience": 5, "constraints": 7, "needs": 8, "happiness": 3, "consistency": 5}
        self.assertEqual({name: len(rows) for name, rows in self.config["questions"].items()}, expected)

    def test_task_keys_and_options_are_parsed_in_order(self):
        tasks = self.config["questions"]["tasks"]
        self.assertEqual([q["correct"] for q in tasks], ["в", "в", "б", "б", "в", "б", "б", "г", "в", "б"])
        self.assertEqual(tasks[5]["options"], ["сокращение срока доставки всегда ухудшает сервис", "рост жалоб связан с тем, что клиентов чаще не заставали дома", "курьеры стали работать хуже", "клиенты предпочитают медленную доставку"])
        self.assertEqual([option["id"] for option in self.config["questions"]["constraints"][0]["options"]], ["a", "b", "c", "d"])

    def test_questions_have_input_types_and_scale_boundaries(self):
        questions = self.config["questions"]
        self.assertEqual(questions["profile"][4]["scale"], {"min": 1, "max": 10})
        self.assertEqual(questions["sphere_interest"][0]["type"], "likert5")
        self.assertEqual(questions["values"][0]["ranking"], {"top": 3, "least": 2})
        self.assertEqual(questions["experience"][0]["energy"], {"min": 1, "max": 5})
        self.assertEqual(questions["constraints"][-1]["type"], "text")

    def test_test_parts_preserve_order_and_needs_pick_three_step(self):
        self.assertEqual([part["id"] for part in self.config["parts"]], ["profile", "interests", "values", "open", "sphere_interest", "tasks", "experience", "constraints", "needs", "happiness", "consistency"])
        needs = next(part for part in self.config["parts"] if part["id"] == "needs")
        pick3 = needs["steps"][1]
        self.assertEqual(pick3["type"], "pick3")
        self.assertEqual((pick3["min"], pick3["max"], pick3["required"]), (3, 3, True))
        self.assertEqual([option["id"] for option in pick3["options"]], [f"NEED{i:02d}" for i in range(1, 9)])
        self.assertEqual(pick3["prompt"], "После оценки всех восьми условий пользователь выбирает три самых важных.")

    def test_profile_courses_and_nad07_are_parsed_from_spec(self):
        profile = self.config["questions"]["profile"]
        self.assertEqual(profile[1]["options"], [f"{i} курс бакалавриата/специалитета" for i in range(1, 7)] + ["1 курс магистратуры", "2 курс магистратуры", "аспирантура"])
        nad07 = self.config["questions"]["constraints"][-1]
        self.assertEqual(nad07["fields"]["Вопрос"], "Другие ограничения, которые важно учесть (text, по желанию)")
        self.assertEqual(nad07["type"], "text")

    def test_required_text_is_preserved_verbatim(self):
        expected = {
            "INT01": "Починить или собрать устройство руками.",
            "VAL03": "зарабатывать заметно больше среднего",
            "SPH14": "Обеспечивать бесперебойную работу сетей и оборудования связи.",
            "TSK06": "Прочитайте: «Компания сократила срок доставки с 5 до 3 дней, но жалобы выросли на 20 %, потому что курьеры чаще не заставали клиентов дома». Какой вывод следует из текста?",
            "NAD05": "Финансовая ситуация",
            "NEED07": "Спокойствие и предсказуемость: понятные задачи и стабильный доход",
            "CHK05": "Точность и контроль качества.",
        }
        all_questions = {q["id"]: q for rows in self.config["questions"].values() for q in rows}
        field_by_id = {"INT01": "Вариант А", "VAL03": "Пояснение на карточке", "SPH14": "Утверждение", "TSK06": "Задание и варианты", "NAD05": "Вопрос", "NEED07": "Условие", "CHK05": "Вариант Б"}
        for qid, text in expected.items():
            actual = all_questions[qid]["fields"][field_by_id[qid]].split("<br>")[0]
            self.assertEqual(actual, text)
        self.assertEqual(all_questions["INT01"]["type_a"], "R")
        self.assertEqual(all_questions["INT01"]["type_b"], "I")
        self.assertEqual(all_questions["CHK05"]["linked"], "INT15")

    def test_build_is_deterministic_and_writes_utf8_json(self):
        output = generator.ROOT / "tools/.test_config_tmp.json"
        try:
            with patch.object(generator, "OUTPUT", output):
                generator.build()
                first = output.read_bytes()
                generator.build()
                second = output.read_bytes()
        finally:
            output.unlink(missing_ok=True)
            self.assertEqual(first, second)
            self.assertEqual(json.loads(first), self.config)
            self.assertIn("Починить", first.decode("utf-8"))

    def test_profiles_link_exactly_two_sphere_questions_each(self):
        self.assertEqual(len(self.config["spheres"]), 7)
        self.assertTrue(all(len(sphere["questions"]) == 2 for sphere in self.config["spheres"]))
        self.assertEqual(self.config["analyst"]["source"], "analyst")
        self.assertEqual(len(self.config["hints"]), 5)
        self.assertEqual(self.config["spheres"][0]["professions"], ["менеджмент", "предпринимательство", "маркетинг"])
        self.assertTrue(all(q["result_section"] == "your_words" for q in self.config["questions"]["open"]))
        self.assertEqual(self.config["questions"]["constraints"][-1]["result_section"], "nad_summary")
        self.assertNotIn("НЕОЬЯЗАТЕЛЬНОЕ", json.dumps(self.config["texts"]["experiments"], ensure_ascii=False))


if __name__ == "__main__":
    unittest.main()
