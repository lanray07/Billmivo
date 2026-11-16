"""Extract and validate release translations. Translation requests contain only static product copy."""
import argparse
from collections import Counter
import hashlib
import html
import json
import os
from pathlib import Path
import re
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
STORE = ROOT / "store"
DRAFTS = ROOT / "deliverables/localisation"
TAGS = json.loads((STORE / "locales.json").read_text(encoding="utf-8"))
FORMAT = re.compile(r"%(?:\d+\$)?[ds]|\bBillmivo(?: Premium)?\b")
CONTEXT = ("Billmivo is an offline household bill tracker. A forecast is a list of upcoming bill balances. "
           "Confirmed means the user knows the amount. Estimated means it is expected. A share is a planned "
           "part of a household bill. A payer is the person who made a recorded payment. "
           "Keep financial wording concise, plain and consistent.")


def write_json(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(".tmp")
    temp.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temp.replace(path)


def extraction(locale):
    source = ET.parse(STRINGS).getroot()
    keys = {f"string/{item.attrib['name']}": item.text or "" for item in source.findall("string")}
    for plural in source.findall("plurals"):
        examples = {item.attrib["quantity"]: item.text or "" for item in plural}
        for category in TAGS[locale].get("plurals", ("one", "other")):
            keys[f"plural/{plural.attrib['name']}/{category}"] = examples.get(category, examples["other"])
    listing = json.loads((STORE / "listings.json").read_text(encoding="utf-8"))["en-GB"]
    for field in ("name", "short", "long", "release_notes", "support"):
        keys[f"listing/{field}"] = listing[field]
    for field in ("features", "keywords", "headlines"):
        for i, text in enumerate(listing[field]):
            keys[f"listing/{field}/{i}"] = text
    for range_, phrase in (("7", "seven"), ("30", "thirty"), ("90", "ninety")):
        keys[f"voice/{range_}"] = phrase
    for action, phrase in (("overdue", "show overdue bills"), ("sharing", "show household cost sharing"),
                           ("increases", "show bill increases"), ("reminders", "show bill reminders")):
        keys[f"voice/{action}"] = phrase
    return keys


def source_id(text):
    return hashlib.sha256(text.encode()).hexdigest()


def to_xml(text):
    return "<t>" + FORMAT.sub(lambda token: "<ph>" + token.group() + "</ph>", html.escape(text, quote=False)) + "</t>"


def from_xml(text):
    root = ET.fromstring(text)
    if root.tag != "t" or any(child.tag != "ph" or len(child) for child in root):
        raise ValueError("Unexpected markup in translation")
    return "".join(root.itertext())


def verify_text(source, target):
    if not isinstance(target, str) or not target.strip():
        raise ValueError("Missing translation")
    if Counter(FORMAT.findall(source)) != Counter(FORMAT.findall(target)):
        raise ValueError("Changed format arguments or brand placeholders")


def translate(texts, target):
    key = os.environ.get("BILLMIVO_DEEPL_KEY")
    host = os.environ.get("BILLMIVO_DEEPL_HOST", "api-free.deepl.com")
    if not key:
        raise SystemExit("BILLMIVO_DEEPL_KEY is required for provider-backed machine translation.")
    if host not in {"api-free.deepl.com", "api.deepl.com"}:
        raise SystemExit("BILLMIVO_DEEPL_HOST must be an official DeepL API host.")
    body = json.dumps({"text": [to_xml(text) for text in texts], "source_lang": "EN",
                       "target_lang": target, "tag_handling": "xml", "tag_handling_version": "v2",
                       "ignore_tags": ["ph"], "context": CONTEXT}).encode()
    request = Request(f"https://{host}/v2/translate", data=body,
                      headers={"Authorization": f"DeepL-Auth-Key {key}", "Content-Type": "application/json"})
    try:
        with urlopen(request, timeout=30) as response:
            results = json.load(response)["translations"]
    except HTTPError as error:
        raise SystemExit(f"Translation API returned HTTP {error.code}.") from None
    except URLError:
        raise SystemExit("Translation API is unavailable.") from None
    if len(results) != len(texts):
        raise ValueError("Unexpected translation result count")
    return [from_xml(value["text"]) for value in results]


def prepare(locale, include_machine):
    path = DRAFTS / f"{locale}.json"
    defaults = extraction(locale)
    existing = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    old = existing.get("translations", {})
    entries = {}
    pending = []
    for key, text in defaults.items():
        previous = old.get(key)
        if previous and previous["sourceHash"] == source_id(text) and previous["target"]:
            entries[key] = previous
        else:
            entries[key] = {"source": text, "sourceHash": source_id(text), "target": "", "reviewed": False}
            pending.append(key)
    if include_machine:
        for start in range(0, len(pending), 35):
            batch = pending[start:start + 35]
            result = translate([defaults[key] for key in batch], TAGS[locale]["deepl"])
            for key, text in zip(batch, result):
                verify_text(defaults[key], text)
                entries[key]["target"] = text
            write_json(path, {"locale": locale, "translations": entries})
    write_json(path, {"locale": locale, "translations": entries})
    print(f"{locale}: {len(entries) - len(pending)} cached, {len(pending)} new source entries")


def render(locale):
    draft = json.loads((DRAFTS / f"{locale}.json").read_text(encoding="utf-8"))["translations"]
    source = extraction(locale)
    if set(draft) != set(source):
        raise ValueError("Draft does not match the current source keys")
    for key, text in source.items():
        entry = draft[key]
        if entry["sourceHash"] != source_id(text) or entry["reviewed"] is not True:
            raise ValueError(f"{key}: current translation requires review")
        verify_text(text, entry["target"])
    out = DRAFTS / "reviewed" / locale
    out.mkdir(parents=True, exist_ok=True)
    xml = ET.Element("resources")
    plurals = {}
    listing = {}
    voice = {}
    for key, entry in draft.items():
        kind, name, *args = key.split("/")
        value = entry["target"]
        android_value = value.replace("\\", "\\\\").replace("\n", "\\n").replace("'", "\\'").replace('"', '\\"')
        if kind == "string":
            ET.SubElement(xml, "string", name=name).text = android_value
        if kind == "plural":
            if name not in plurals:
                plurals[name] = ET.SubElement(xml, "plurals", name=name)
            ET.SubElement(plurals[name], "item", quantity=args[0]).text = android_value
        if kind == "listing":
            if args:
                listing.setdefault(name, []).append(value)
            else:
                listing[name] = value
        if kind == "voice":
            voice[name] = value
    ET.indent(xml)
    ET.ElementTree(xml).write(out / "strings.xml", encoding="utf-8", xml_declaration=True)
    write_json(out / "listing.json", listing)
    write_json(out / "voice.json", voice)
    print(f"{locale}: rendered {len(draft)} reviewed entries for integration")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("extract", "translate", "render"))
    parser.add_argument("locale", choices=tuple(TAGS))
    args = parser.parse_args()
    if args.command == "render":
        render(args.locale)
    else:
        prepare(args.locale, args.command == "translate")
