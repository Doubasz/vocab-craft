"""
Builds the bundled JLPT decks (src/main/resources/vocabcraft/decks/jlpt_n1..n5.json).

Source: jamsinclair/open-anki-jlpt-decks (MIT), based on the JLPT lists by Jonathan Waller
(tanos.co.uk). The source commit is pinned so the output is reproducible.

Usage (from the project root):
    python tools/build_jlpt_decks.py                 # download the pinned CSVs
    python tools/build_jlpt_decks.py --csv-dir DIR   # use n1.csv..n5.csv already in DIR

Romaji is not written to the decks: the mod generates it from the kana (KanaConverter.toRomaji).
"""
import argparse
import csv
import io
import json
import re
import sys
import urllib.request
from pathlib import Path

SOURCE_REPO = "jamsinclair/open-anki-jlpt-decks"
SOURCE_COMMIT = "1ad66734417aca9dbcca6b2d5ee440cb13ab3ba0"
SOURCE_URL = f"https://raw.githubusercontent.com/{SOURCE_REPO}/{SOURCE_COMMIT}/src/n{{level}}.csv"
OUTPUT_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "vocabcraft" / "decks"

KANA_ONLY = re.compile(r"^[぀-ヿー]+$")
AFFIX = re.compile(r"[～〜~]")  # counters and prefixes like ～円, お～: not standalone words
INTERJECTION_TAG = re.compile(r"\s*\(かん\)")
PARENTHESES = re.compile(r"\s*[(（][^)）]*[)）]")
MAX_MEANINGS = 8


def read_csv(level: int, csv_dir: Path | None) -> list[dict]:
    if csv_dir:
        text = (csv_dir / f"n{level}.csv").read_text(encoding="utf-8")
    else:
        with urllib.request.urlopen(SOURCE_URL.format(level=level), timeout=30) as response:
            text = response.read().decode("utf-8")
    return list(csv.DictReader(io.StringIO(text)))


def spellings(field: str) -> list[str]:
    """'パート (タイム)' -> ['パート', 'パートタイム']; '足; 脚' -> ['足', '脚']."""
    result = []
    for part in field.split(";"):
        part = INTERJECTION_TAG.sub("", part).strip()
        if not part:
            continue
        short = PARENTHESES.sub("", part).strip()
        full = re.sub(r"[\s()（）]", "", part)
        for candidate in (short, full):
            if candidate and candidate not in result:
                result.append(candidate)
    return result


def split_meanings(meaning: str) -> list[str]:
    """Splits on ';' and ',' outside parentheses."""
    parts, depth, current = [], 0, []
    for ch in meaning:
        if ch in "(（":
            depth += 1
        elif ch in ")）":
            depth = max(0, depth - 1)
        if ch in ";," and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(ch)
    parts.append("".join(current))
    return [p.strip() for p in parts if p.strip()]


def meanings(meaning: str) -> tuple[str, list[str]]:
    """Returns (prompt, accepted): the prompt keeps its notes, accepted answers drop them."""
    segments = split_meanings(meaning)
    accepted = []
    for segment in segments:
        stripped = re.sub(r"\s+", " ", PARENTHESES.sub("", segment).replace("~", "")).strip(" .")
        if stripped and stripped not in accepted:
            accepted.append(stripped)
    return (segments[0] if segments else ""), accepted


def unique(items) -> list:
    return list(dict.fromkeys(items))


def convert(level: int, rows: list[dict]) -> tuple[dict, int]:
    words: dict[str, dict] = {}
    skipped = 0
    for row in rows:
        expression, reading = row["expression"], row["reading"]
        if AFFIX.search(expression) or AFFIX.search(reading):
            skipped += 1
            continue
        written = spellings(expression)
        readings = [r for r in spellings(reading) if KANA_ONLY.match(r)]
        prompt, accepted = meanings(row["meaning"])
        if not written or not readings or not prompt:
            skipped += 1
            continue

        kana = readings[0]
        kanji = None if KANA_ONLY.match(written[0]) else written[0]
        alternatives = unique(s for s in written + readings if s not in (kanji, kana))

        word_id = f"{kanji or kana}|{kana}"
        if word_id in words:  # same word listed twice: merge accepted meanings
            word = words[word_id]
            word["en"] = unique(word["en"] + accepted)[:MAX_MEANINGS]
            continue
        word = {"id": word_id}
        if kanji:
            word["kanji"] = kanji
        word["kana"] = kana
        if alternatives:
            word["alt"] = alternatives
        word["en"] = unique([prompt] + accepted)[:MAX_MEANINGS]
        words[word_id] = word

    deck = {
        "id": f"jlpt_n{level}",
        "title": f"JLPT N{level}",
        "source": f"https://github.com/{SOURCE_REPO} @ {SOURCE_COMMIT[:7]} (based on tanos.co.uk JLPT lists)",
        "words": list(words.values()),
    }
    return deck, skipped


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--csv-dir", type=Path, help="read n1.csv..n5.csv from this folder instead of downloading")
    args = parser.parse_args()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for level in (5, 4, 3, 2, 1):
        deck, skipped = convert(level, read_csv(level, args.csv_dir))
        path = OUTPUT_DIR / f"jlpt_n{level}.json"
        with path.open("w", encoding="utf-8", newline="\n") as f:
            json.dump(deck, f, ensure_ascii=False, indent=1)
            f.write("\n")
        print(f"N{level}: {len(deck['words'])} words, {skipped} skipped -> {path.name}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
