"""
Builds src/main/resources/vocabcraft/items_vanilla.json: the Japanese name of every Minecraft item,
taken from the game's own ja_jp translation, with a kana reading added.

Readings come from pykakasi plus a hand-checked list of fixes (READING_FIXES) for words it gets wrong
in Minecraft names (剣 is けん, not つるぎ; 歪んだ is ゆがんだ; 玄武岩 is げんぶがん…).

Requirements:  pip install -r tools/requirements.txt
Usage (from the project root, after ./gradlew runClient has downloaded the game assets once):
    python tools/build_item_names.py
    python tools/build_item_names.py --ja path/to/ja_jp.json --en path/to/en_us.json

Note: the names are Mojang's translations. Fine for personal use; to publish the mod, read them from the
client's language files at runtime instead of bundling this file.
"""
import argparse
import json
import re
import sys
import zipfile
from pathlib import Path

MINECRAFT_VERSION = "26.3"
LOOM_CACHE = Path.home() / ".gradle" / "caches" / "fabric-loom"
OUTPUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "vocabcraft" / "items_vanilla.json"

ITEM_KEY = re.compile(r"(item|block)\.minecraft\.([a-z0-9_]+)")
KANA_ONLY = re.compile(r"^[぀-ヿー]+$")

# Applied to the Japanese name before pykakasi, longest first. Checked by hand against the 26.3 names.
READING_FIXES = {
    # rocks
    "玄武岩": "げんぶがん", "閃緑岩": "せんりょくがん", "深層岩": "しんそうがん",
    "鍾乳石": "しょうにゅうせき", "黒曜石": "こくようせき",
    # colors
    "黄緑色": "きみどりいろ", "薄灰色": "うすはいいろ", "赤色": "あかいろ", "白色": "しろいろ",
    "黒色": "くろいろ", "緑色": "みどりいろ", "色付き": "いろつき",
    # ice
    "薄氷": "うすごおり", "青氷": "あおごおり",
    # nuggets and clumps
    "銅塊": "どうかい", "金塊": "きんかい", "鉄塊": "てっかい", "氷塊": "ひょうかい", "塊": "かたまり",
    # food
    "生鮭": "なまざけ", "生の": "なまの",
    # nether wood
    "歪んだ": "ゆがんだ", "幹": "みき", "剥いだ": "はいだ",
    # pottery sherds and maps
    "射手": "しゃしゅ", "探検家": "たんけんか", "醸造家": "じょうぞうか", "人の壺": "ひとのつぼ",
    "風巻く者": "かざまくもの", "哀悼者": "あいとうしゃ", "遠吠え": "とおぼえ", "富の": "とみの",
    "宝物庫": "ほうもつこ",
    # misc
    "TNT": "てぃーえぬてぃー", "剣": "けん", "瓶": "びん", "尖った": "とがった", "記入済み": "きにゅうずみ", "打ち金": "うちがね",
    "機織り機": "はたおりき", "竹入り": "たけいり", "根付いた": "ねづいた", "燻製器": "くんせいき",
    "鍛冶型": "かじがた", "粘土玉": "ねんどだま", "キノコの柄": "きのこのえ", "焚き火": "たきび",
}
_FIX_PATTERN = re.compile("|".join(sorted(map(re.escape, READING_FIXES), key=len, reverse=True)))


def default_lang_files() -> tuple[Path, Path]:
    indexes = sorted((LOOM_CACHE / "assets" / "indexes").glob(f"{MINECRAFT_VERSION}-*.json"))
    if not indexes:
        sys.exit("Game assets not found. Run ./gradlew runClient once, or pass --ja and --en.")
    objects = json.loads(indexes[-1].read_text(encoding="utf-8"))["objects"]
    ja_hash = objects["minecraft/lang/ja_jp.json"]["hash"]
    ja = LOOM_CACHE / "assets" / "objects" / ja_hash[:2] / ja_hash
    jar = LOOM_CACHE / "minecraftMaven" / "net" / "minecraft" / "minecraft-clientonly-deobf" / MINECRAFT_VERSION \
        / f"minecraft-clientonly-deobf-{MINECRAFT_VERSION}.jar"
    return ja, jar


def read_en(path: Path) -> dict:
    if path.suffix == ".jar":
        with zipfile.ZipFile(path) as jar:
            return json.loads(jar.read("assets/minecraft/lang/en_us.json").decode("utf-8"))
    return json.loads(path.read_text(encoding="utf-8"))


def reading(name: str, kakasi) -> str:
    if KANA_ONLY.match(name):
        return name  # already kana (トロッコ, ジャングルのボタン): keep it as written
    fixed = _FIX_PATTERN.sub(lambda m: READING_FIXES[m.group(0)], name)
    return "".join(token["hira"] for token in kakasi.convert(fixed))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ja", type=Path, help="ja_jp.json (default: from the Loom asset cache)")
    parser.add_argument("--en", type=Path, help="en_us.json or the client jar (default: Loom's client jar)")
    args = parser.parse_args()

    import pykakasi  # imported here so --help works without it
    kakasi = pykakasi.kakasi()

    default_ja, default_en = default_lang_files() if not (args.ja and args.en) else (None, None)
    ja = json.loads((args.ja or default_ja).read_text(encoding="utf-8"))
    en = read_en(args.en or default_en)

    entries, skipped = {}, []
    for key in sorted(en, key=lambda k: (not k.startswith("item."), k)):  # item.* before block.*
        match = ITEM_KEY.fullmatch(key)
        if not match or key not in ja:
            continue
        item_id = f"minecraft:{match.group(2)}"
        if item_id in entries:  # an item and a block with the same id: keep the first (item.*)
            continue
        name = ja[key].strip()
        kana = reading(name, kakasi)
        if not KANA_ONLY.match(kana):
            skipped.append(f"{item_id} ({name})")
            continue
        entry = {"item": item_id}
        if not KANA_ONLY.match(name):
            entry["kanji"] = name  # written form: kanji, or letters like TNT
        entry["kana"] = kana
        entry["en"] = [en[key]]
        entries[item_id] = entry

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", encoding="utf-8", newline="\n") as f:
        f.write("[\n")
        f.write(",\n".join("  " + json.dumps(e, ensure_ascii=False) for e in entries.values()))
        f.write("\n]\n")
    print(f"{len(entries)} items -> {OUTPUT.name}; skipped {len(skipped)} without a kana reading: {', '.join(skipped)}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
