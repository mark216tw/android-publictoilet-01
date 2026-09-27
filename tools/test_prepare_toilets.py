import csv
import io
import unittest

from prepare_toilets import prepare


HEADERS = ["公廁編號", "公廁名稱", "縣市", "鄉鎮名稱", "村里名稱", "緯度", "經度", "公廁類型"]


def source(*records):
    stream = io.StringIO()
    writer = csv.DictWriter(stream, fieldnames=HEADERS)
    writer.writeheader()
    for identifier, name, category, *coords in records:
        writer.writerow({
            "公廁編號": identifier, "公廁名稱": name, "公廁類型": category,
            "縣市": "臺南市", "鄉鎮名稱": "南區", "村里名稱": "大成里",
            "緯度": coords[0] if coords else "22.98", "經度": "120.19",
        })
    stream.seek(0)
    return stream


class PrepareToiletsTest(unittest.TestCase):
    def test_merges_matching_types_but_keeps_other_floors(self):
        places, count = prepare(source(
            ("1", "醫院1F男廁", "男廁所"),
            ("2", "醫院1F女廁", "女廁所"),
            ("3", "醫院1F女廁", "女廁所"),
            ("4", "醫院2F男廁", "男廁所"),
        ))
        self.assertEqual((count, len(places)), (4, 2))
        self.assertEqual(places[0]["name"], "醫院1F")
        self.assertEqual(places[0]["id"], "1")
        self.assertEqual(places[0]["categories"], ["男廁所", "女廁所"])
        self.assertEqual(places[1]["name"], "醫院2F")

    def test_staff_mixed_label_and_same_place(self):
        places, _ = prepare(source(
            ("1", "奇美醫院樹林院區1F女廁", "女廁所"),
            ("2", "奇美醫院樹林院區1F員工混合廁", "混合廁所"),
            ("3", "洪外科醫院1F員工混合廁所", "混合廁所"),
            ("4", "博物館員工停車場混合廁", "混合廁所"),
        ))
        self.assertEqual([item["name"] for item in places], [
            "奇美醫院樹林院區1F", "洪外科醫院1F", "博物館員工停車場",
        ])
        self.assertEqual(places[0]["categories"], ["女廁所", "員工混合廁"])
        self.assertEqual(places[1]["categories"], ["員工混合廁"])
        self.assertEqual(places[2]["categories"], ["混合廁所"])

    def test_keeps_unmatched_description(self):
        name = "市場東側混合廁(三民路36號)旁"
        places, _ = prepare(source(("1", name, "混合廁所")))
        self.assertEqual(places[0]["name"], name)

    def test_missing_column_and_invalid_rows_fail(self):
        with self.assertRaisesRegex(ValueError, "缺少必要欄位"):
            prepare(io.StringIO("公廁編號,公廁名稱\n1,公園\n"))
        with self.assertRaisesRegex(ValueError, "座標超出有效範圍"):
            prepare(source(("1", "公園男廁", "男廁所", "nan")))
        with self.assertRaisesRegex(ValueError, "公廁編號重複"):
            prepare(source(("1", "公園男廁", "男廁所"), ("1", "公園女廁", "女廁所")))


if __name__ == "__main__":
    unittest.main()
