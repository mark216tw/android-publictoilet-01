"""Convert the Tainan public-toilet CSV into the asset consumed by the app.

Usage: python tools/prepare_toilets.py new-version.csv
       python tools/prepare_toilets.py new-version.csv --check
"""

import argparse
import csv
import json
import math
import os
import tempfile
from pathlib import Path
from typing import TextIO


ASSET = Path(__file__).resolve().parents[1] / "app/src/main/assets/toilets.json"
REQUIRED = {"公廁編號", "公廁名稱", "縣市", "鄉鎮名稱", "村里名稱", "緯度", "經度", "公廁類型"}
TRAILING_SEPARATORS = " -－：:"


def place_name(name: str, category: str) -> str:
    if category == "混合廁所" and name.endswith(("員工混合廁所", "員工混合廁")):
        endings = ("員工混合廁所", "員工混合廁")
    else:
        endings = (category, category.removesuffix("所"))
    for ending in endings:
        if ending and name.endswith(ending):
            return name[: -len(ending)].rstrip(TRAILING_SEPARATORS) or name
    return name


def display_category(name: str, category: str) -> str:
    if category == "混合廁所" and name.endswith(("員工混合廁所", "員工混合廁")):
        return "員工混合廁"
    return category


def prepare(source: TextIO) -> tuple[list[dict], int]:
    reader = csv.DictReader(source, strict=True)
    if reader.fieldnames is None:
        raise ValueError("CSV 是空檔案，缺少欄位標題")
    reader.fieldnames = [field.strip().lstrip("\ufeff") for field in reader.fieldnames]
    missing = REQUIRED.difference(reader.fieldnames)
    if missing:
        raise ValueError("缺少必要欄位：" + "、".join(sorted(missing)))

    places: dict[tuple[float, float, str, str], dict] = {}
    seen_ids: set[str] = set()
    invalid: list[str] = []
    count = 0
    for row in reader:
        count += 1
        number = reader.line_num
        if None in row or any(row[field] is None for field in REQUIRED):
            invalid.append(f"第 {number} 行：欄位數量不符")
            continue
        values = {field: row[field].strip() for field in REQUIRED}
        if any(not value for value in values.values()):
            invalid.append(f"第 {number} 行：必要欄位為空")
            continue
        identifier = values["公廁編號"]
        if identifier in seen_ids:
            invalid.append(f"第 {number} 行：公廁編號重複（{identifier}）")
            continue
        seen_ids.add(identifier)
        try:
            lat, lon = float(values["緯度"]), float(values["經度"])
        except ValueError:
            invalid.append(f"第 {number} 行：座標不是數字")
            continue
        if not (math.isfinite(lat) and math.isfinite(lon) and -90 <= lat <= 90 and -180 <= lon <= 180):
            invalid.append(f"第 {number} 行：座標超出有效範圍")
            continue

        name = values["公廁名稱"]
        category = values["公廁類型"]
        region = values["縣市"] + values["鄉鎮名稱"] + values["村里名稱"]
        base = place_name(name, category)
        key = (lat, lon, region, base)
        if key not in places:
            places[key] = {
                "id": identifier, "name": base, "region": region,
                "categories": [], "latitude": lat, "longitude": lon,
            }
        label = display_category(name, category)
        if label not in places[key]["categories"]:
            places[key]["categories"].append(label)

    if invalid:
        summary = "\n".join(invalid[:10])
        extra = f"\n另有 {len(invalid) - 10} 筆錯誤" if len(invalid) > 10 else ""
        raise ValueError(f"CSV 有 {len(invalid)} 筆無效資料，未產生輸出：\n{summary}{extra}")
    if not places:
        raise ValueError("CSV 沒有可使用的廁所資料")
    return list(places.values()), count


def write_asset(path: Path, places: list[dict]) -> None:
    if not path.parent.is_dir():
        raise ValueError(f"輸出目錄不存在：{path.parent}")
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", newline="\n", dir=path.parent,
            prefix=".toilets-", suffix=".tmp", delete=False,
        ) as handle:
            temporary = Path(handle.name)
            json.dump(places, handle, ensure_ascii=False, separators=(",", ":"))
            handle.write("\n")
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="整理臺南市公廁 CSV，產生 Android JSON 資產")
    parser.add_argument("input", type=Path, help="政府資料開放平臺下載的 CSV")
    parser.add_argument("--output", type=Path, default=ASSET, help="輸出 JSON（預設為 App 資產路徑）")
    parser.add_argument("--check", action="store_true", help="僅檢查與統計，不寫入檔案")
    args = parser.parse_args()
    try:
        with args.input.open(encoding="utf-8-sig", newline="") as source:
            places, raw_count = prepare(source)
        if not args.check:
            if args.input.resolve() == args.output.resolve():
                raise ValueError("輸出路徑不得與來源檔案相同")
            write_asset(args.output, places)
    except (OSError, UnicodeError, csv.Error, ValueError) as error:
        parser.exit(1, f"資料整理失敗：{error}\n")
    print(f"原始 {raw_count} 筆，整理後 {len(places)} 個場所，無效資料 0 筆")
    if not args.check:
        print(f"已輸出：{args.output}")


if __name__ == "__main__":
    main()
