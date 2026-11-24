#!/usr/bin/env python3
"""Reference screenshots of every area of the Android app (docs/screens/), driven over adb on an EMULATOR only.

    python3 tools/screens/capture.py                  # German, light + dark extras, into docs/screens/android/
    python3 tools/screens/capture.py --lang en        # English set (docs/screens/android-en/)
    python3 tools/screens/capture.py --only today     # one area while iterating

It clears the debug app's data (pm clear), walks onboarding, then uses the debug demo menu (long-press „Heute“) for
the prototype scenarios. Never point it at a real phone: it erases the app's data. Requires the debug build installed
(tools/deploy.sh) and Pillow (python3 -m pip install pillow).
"""
import argparse
import html
import io
import re
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image

PKG = "com.nordinary.cyclus.debug"
ACT = "com.nordinary.cyclus.MainActivity"
ROOT = Path(__file__).resolve().parents[2]

# UI text per language, read from the app's own string resources (so every language works without a label list)
LANG_DIRS = {"en": "values", "de": "values-de", "fr": "values-fr", "es": "values-es", "pt": "values-pt", "it": "values-it",
             "pl": "values-pl", "cs": "values-cs", "hr": "values-hr"}
LOCALE_TAGS = {"en": "en-US", "de": "de-DE", "fr": "fr-FR", "es": "es-ES", "pt": "pt-PT", "it": "it-IT",
               "pl": "pl-PL", "cs": "cs-CZ", "hr": "hr-HR"}


def res_strings(lang: str) -> dict:
    out = {}
    d = ROOT / "androidApp" / "src" / "main" / "res" / LANG_DIRS[lang]
    for f in ("strings_tx.xml", "strings.xml"):
        x = (d / f).read_text(encoding="utf-8")
        for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', x, re.S):
            out[m.group(1)] = m.group(2)
        for m in re.finditer(r'<plurals name="([^"]+)">(.*?)</plurals>', x, re.S):
            for q in re.finditer(r'<item quantity="([a-z]+)">(.*?)</item>', m.group(2), re.S):
                out[f"{m.group(1)}#{q.group(1)}"] = q.group(2)
    def clean(t):
        t = html.unescape(t).replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
        return re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), t).replace("{b}", "").replace("{/b}", "")
    return {k: clean(v) for k, v in out.items()}


def labels(lang: str) -> dict:
    r = res_strings(lang)
    s = lambda k: r["tx_" + k] if "tx_" + k in r else r[k]
    # a stable piece of a formatted text (the longest part without placeholders)
    frag = lambda t: max(re.split(r"%\d+\$[sd]", t), key=len).strip()
    return dict(
        start=s("getStarted"), cont=s("cont"), read=s("noticeChk"), agree=s("agreeCont"), skip=s("skip"),
        noMemory=s("dontRemember"), notNow=s("notNow"), today=s("tabToday"), calendar=s("tabCal"), insights=s("tabIns"),
        day12=s("debug_sc_day12"), expected=s("debug_sc_expected"), period=s("debug_sc_period"), hbc=s("debug_sc_hbc"),
        conceive=s("debug_sc_conceive"), twoww=s("debug_sc_conceive2ww"), ringHelp=s("ringHelpA"), done=s("done"),
        cancel=s("cancel"), customize=s("customize"), compact=s("ringCompact"), large=s("ringLarge"),
        addWeek=f'{s("showCard")}: {s("card_week")}', editQuick=s("editQuick"), reset=s("resetLayout"), log=s("fab"),
        more=s("log_more"), allPatterns=frag(r["pat_all#other"]), how=s("howWorks"), report=s("reportT"),
        privacy=s("aPrivacy"), settings=s("aSettings"), notice=s("noticeRow"), erase=s("eraseT"), export=s("encBk"),
        notifications=s("notifT"), custom=s("pal_custom"), pin=s("pinT"), appLock=s("faceLock"),
        why=s("whyLink").rstrip("?  "), preview=s("previewBtn"), back=s("back"), close=s("close"),
        about=s("aboutT"), oss=s("ossT"), sqlc="SQLCipher",
        cards=[s(k) for k in ("card_usual", "card_temp", "card_mood", "card_doctor", "card_phase")],
        showCard=s("showCard"), dqPlus="+ " + s("dqAdd"), dqAdd=s("dqAdd"),
        # typed with adb, so plain ASCII
        question={"de": "Ist das noch normal?", "en": "Is this still normal?", "fr": "Est-ce normal?",
                  "es": "Es normal esto?", "pt": "Isto e normal?", "it": "E normale?",
                  "pl": "Czy to normalne?", "cs": "Je to normalni?", "hr": "Je li to normalno?"}[lang],
    )


T = {lang: labels(lang) for lang in LANG_DIRS}

class Emu:
    def __init__(self, serial: str, out: Path, scale: float):
        if not serial.startswith("emulator-"):
            sys.exit(f"refusing {serial}: emulators only (this erases the app's data)")
        self.serial, self.out, self.scale = serial, out, scale
        self.w, self.h = map(int, re.findall(r"(\d+)x(\d+)", self.sh("wm size"))[-1])
        out.mkdir(parents=True, exist_ok=True)

    def adb(self, *a, binary=False):
        r = subprocess.run(["adb", "-s", self.serial, *a], capture_output=True)
        return r.stdout if binary else r.stdout.decode(errors="replace")

    def sh(self, cmd):
        return self.adb("shell", cmd)

    def nodes(self):
        self.sh("uiautomator dump /sdcard/ui.xml")
        xml = self.adb("exec-out", "cat", "/sdcard/ui.xml")
        for m in re.finditer(r"<node [^>]*>", xml):
            n = m.group(0)
            b = list(map(int, re.findall(r"\d+", re.search(r'bounds="([^"]*)"', n).group(1))))
            # attribute values may be single-quoted when they contain a double quote (e.g. a license name)
            attr = lambda k: (lambda m: html.unescape(m.group(2)) if m else "")(re.search(rf' {k}=(["\'])(.*?)\1', n))
            yield attr("text"), attr("content-desc"), b

    def find(self, label, desc=False, tries=3):
        for _ in range(tries):
            ns = list(self.nodes())
            # by text first, then by content description (icon-only buttons such as the collapsed FAB)
            for text, d, b in ns:
                if (label in d) if desc else (label in text):
                    return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
            if not desc:
                for text, d, b in ns:
                    if label in d:
                        return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
            time.sleep(0.8)
        return None

    def tap(self, label, desc=False, wait=1.2, required=True, seek=3):
        p = self.find(label, desc, tries=2)
        # below the fold: scroll down a little at a time until it shows up
        steps = [(.70, .40)] * seek + [(.35, .75)] * (seek * 2)  # down first, then back up past the start
        for a, b in steps:
            if p is not None:
                break
            self.sh(f"input swipe {self.w // 2} {int(self.h * a)} {self.w // 2} {int(self.h * b)} 300")
            time.sleep(0.6)
            p = self.find(label, desc, tries=1)
        if p is None:
            if required:
                raise RuntimeError(f"not on screen: {label!r}")
            return False
        # half under the sticky screen header (a tap there hits the header): bring the target down a little first
        if not desc and self.h * 0.105 < p[1] < self.h * 0.16:
            self.sh(f"input swipe {self.w // 2} {int(self.h * .45)} {self.w // 2} {int(self.h * .55)} 400")
            time.sleep(0.8)
            p = self.find(label, desc, tries=1) or p
        # the floating "Log" button covers the bottom right (wider in long languages): bring the target up first
        if p[1] > self.h * 0.72:
            self.sh(f"input swipe {self.w // 2} {int(self.h * .70)} {self.w // 2} {int(self.h * .45)} 400")
            time.sleep(0.8)
            p = self.find(label, desc, tries=1) or p
        self.sh(f"input tap {p[0]} {p[1]}")
        time.sleep(wait)
        return True

    def tap_exact(self, label, wait=0.35):
        hits = [((b[0] + b[2]) // 2, (b[1] + b[3]) // 2) for text, _, b in self.nodes() if text == label]
        if not hits:
            raise RuntimeError(f"not on screen: {label!r}")
        x, y = max(hits, key=lambda q: q[1])  # the keypad sits low on the screen
        self.sh(f"input tap {x} {y}")
        time.sleep(wait)

    def long_press(self, label):
        # the topmost match (the screen title, not the tab label of the same name)
        hits = [((b[0] + b[2]) // 2, (b[1] + b[3]) // 2) for text, _, b in self.nodes() if text == label]
        p = min(hits, key=lambda q: q[1]) if hits else None
        if p is None:
            raise RuntimeError(f"not on screen: {label!r}")
        self.sh(f"input swipe {p[0]} {p[1]} {p[0]} {p[1]} 900")
        time.sleep(1.2)

    def scroll(self, times=1, up=False):
        a, b = (int(self.h * .30), int(self.h * .78)) if up else (int(self.h * .78), int(self.h * .30))
        for _ in range(times):
            self.sh(f"input swipe {self.w // 2} {a} {self.w // 2} {b} 350")
            time.sleep(0.7)

    def top(self):
        self.scroll(6, up=True)

    def home(self, t):
        """Back to the app's Today tab, whatever happened before (brings the task to the front, taps the bottom tab)."""
        self.sh(f"am start -n {PKG}/{ACT}")
        time.sleep(1.2)
        for _ in range(4):
            # the tab bar shows „Heute“ near the bottom; pushed screens and sheets don't: go back until it appears
            hits = [((b[0] + b[2]) // 2, (b[1] + b[3]) // 2) for text, _, b in self.nodes() if text == t["today"]]
            tab = [q for q in hits if q[1] > self.h * .8]
            if tab:
                self.sh(f"input tap {tab[0][0]} {tab[0][1]}")
                time.sleep(1.0)
                break
            self.key("KEYCODE_BACK")
        self.top()

    def key(self, k):
        self.sh(f"input keyevent {k}")
        time.sleep(1.0)

    def shot(self, name):
        raw = self.adb("exec-out", "screencap", "-p", binary=True)
        img = Image.open(io.BytesIO(raw)).convert("RGB")
        if self.scale != 1:
            img = img.resize((round(img.width * self.scale), round(img.height * self.scale)), Image.LANCZOS)
        # 256-colour palette: UI screenshots look the same at about 40% of the size (keeps the repo small)
        img.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(self.out / f"{name}.png", optimize=True)
        print("  ", name)


def demo(e: Emu, t, scenario):
    e.top()
    e.long_press(t["today"])
    e.tap(scenario, wait=2.5)
    e.top()


def run(e: Emu, lang: str, only: str | None, dark: bool):
    t = T[lang]
    want = lambda area: only in (None, area)

    print("reset")
    e.sh(f"pm clear {PKG}")
    e.sh("cmd uimode night no")
    e.sh(f"cmd locale set-app-locales {PKG} --locales {LOCALE_TAGS[lang]}")
    e.sh(f"am start -n {PKG}/{ACT}")
    time.sleep(3)

    # onboarding (always walked: the rest needs it)
    if want("onboarding"): e.shot("01-onboarding-1-welcome")
    e.tap(t["start"])
    if want("onboarding"): e.shot("01-onboarding-2-privacy")
    e.tap(t["cont"])
    if want("onboarding"): e.shot("01-onboarding-3-notice")
    e.tap(t["read"]); e.tap(t["agree"])
    if want("onboarding"): e.shot("01-onboarding-4-import")
    e.tap(t["skip"])
    if want("onboarding"): e.shot("01-onboarding-5-last-period")
    e.tap(t["noMemory"])
    if want("onboarding"): e.shot("01-onboarding-6-life-stage")
    e.tap(t["cont"])
    if want("onboarding"): e.shot("01-onboarding-7-app-lock")
    e.tap(t["notNow"], wait=2)
    if want("today"): e.shot("02-today-empty")

    demo(e, t, t["day12"])
    if want("today"):
        e.shot("02-today-track-1")
        e.scroll(); e.shot("02-today-track-2")
        e.scroll(2); e.shot("02-today-track-3")
        e.top(); e.tap(t["ringHelp"], desc=True); e.shot("02-today-ring-explainer"); e.tap(t["done"])
    if want("edit"):
        e.home(t)
        e.scroll(5); e.tap(t["customize"], wait=1.8)
        e.shot("03-edit-1")
        e.scroll(); e.shot("03-edit-2")
        e.scroll(2); e.shot("03-edit-3-library")
        e.tap(t["addWeek"], desc=True)
        e.top(); e.scroll(); e.tap(t["editQuick"], wait=1.8); e.shot("03-edit-quick-picks"); e.tap(t["done"])
        e.top(); e.tap(t["compact"]); e.shot("03-edit-compact-ring")
        e.tap(t["done"]); e.shot("03-today-compact-1")
        e.scroll(5); e.shot("03-today-compact-2-week")
        # back to the default layout for the rest
        e.tap(t["customize"], wait=1.8); e.scroll(6); e.tap(t["reset"], required=False)
        e.top(); e.tap(t["large"], required=False); e.tap(t["done"])
    if want("cards"):
        # step 2–3 cards: add all five from the library, then a question; reset afterwards
        e.home(t)
        e.scroll(5); e.tap(t["customize"], wait=1.8)
        for name in t["cards"]:
            e.tap(f"{t['showCard']}: {name}", desc=True, seek=12)
        e.shot("03-edit-4-step23")
        e.top(); e.tap(t["done"]); e.top()
        for i in range(1, 4):
            e.scroll(); e.shot(f"08-cards-{i}")
        e.tap(t["dqPlus"], seek=8, wait=2.5)
        e.sh("input text " + t["question"].replace(" ", "%s")); time.sleep(0.8)
        e.tap_exact(t["dqAdd"], wait=1.2); e.key("KEYCODE_BACK")
        e.shot("08-cards-questions-sheet")
        e.key("KEYCODE_BACK")
        demo(e, t, t["twoww"])
        e.home(t); e.top()
        for i in range(1, 4):
            e.scroll(); e.shot(f"08-cards-conceive-{i}")
        demo(e, t, t["day12"])
        e.home(t); e.scroll(6); e.tap(t["customize"], wait=1.8); e.scroll(8); e.tap(t["reset"], required=False)
        e.top(); e.tap(t["done"])
    if want("log"):
        e.home(t)
        e.top(); e.tap(t["log"], wait=1.8)
        e.shot("04-log-1")
        e.scroll(); e.shot("04-log-2")
        e.scroll(2); e.shot("04-log-3")
        e.tap(t["more"], required=False); e.scroll(2); e.shot("04-log-4-more")
        e.key("KEYCODE_BACK")
    if want("calendar"):
        e.home(t)
        e.tap(t["calendar"], wait=1.8); e.shot("05-calendar-1")
        e.scroll(); e.shot("05-calendar-2-day")
        e.scroll(2); e.shot("05-calendar-3-legend")
        e.top(); e.tap(t["today"], wait=1.5)
    if want("insights"):
        e.home(t)
        e.tap(t["insights"], wait=1.8); e.shot("06-insights-1")
        e.scroll(); e.shot("06-insights-2")
        e.scroll(); e.shot("06-insights-3")
        e.scroll(2); e.shot("06-insights-4")
        e.top()
        if e.tap(t["allPatterns"], required=False):
            e.shot("06-patterns-1"); e.scroll(); e.shot("06-patterns-2"); e.key("KEYCODE_BACK")
        e.top(); e.scroll()
        if e.tap(t["how"], required=False):
            e.shot("06-method-sheet"); e.tap(t["done"])
    if want("report"):
        e.home(t)
        e.top(); e.tap(t["insights"], wait=1.2)
        for _ in range(3):
            if e.tap(t["report"], required=False, wait=1.8): break
            e.scroll()
        e.shot("07-report-1"); e.scroll(); e.shot("07-report-2")
        e.scroll(3)
        if e.tap(t["preview"], required=False, wait=4):
            e.shot("07-report-3-pdf"); e.scroll(); e.shot("07-report-4-pdf")
        e.key("KEYCODE_BACK"); e.key("KEYCODE_BACK")
        e.tap(t["today"], required=False)
    if want("privacy"):
        e.home(t)
        e.top(); e.tap(t["privacy"], desc=True, wait=1.8)
        e.shot("08-privacy-1"); e.scroll(); e.shot("08-privacy-2"); e.scroll(); e.shot("08-privacy-3"); e.scroll(2); e.shot("08-privacy-4")
        e.tap(t["notice"]); e.shot("08-privacy-notice-history"); e.tap(t["close"], seek=0)
        e.tap(t["erase"]); e.shot("08-privacy-erase"); e.tap(t["cancel"], seek=0)
        e.tap(t["export"]); e.shot("08-privacy-export"); e.tap(t["cancel"], seek=0)
        e.key("KEYCODE_BACK")
    if want("settings"):
        e.home(t)
        e.top(); e.tap(t["settings"], desc=True, wait=1.8)
        e.shot("09-settings-1"); e.scroll(); e.shot("09-settings-2"); e.scroll(2); e.shot("09-settings-3")
        e.top()
        if e.tap(t["custom"], required=False, wait=1.8):
            e.shot("09-settings-custom-colours"); e.key("KEYCODE_BACK")
        e.scroll(3)
        if e.tap(t["notifications"], required=False, wait=1.8):
            e.shot("09-notifications"); e.key("KEYCODE_BACK")
        e.key("KEYCODE_BACK")
    if want("about"):
        e.home(t)
        e.top(); e.tap(t["settings"], desc=True, wait=1.8)
        e.tap(t["about"], wait=1.8); e.shot("09-about")
        e.tap(t["notice"]); e.shot("09-about-notice"); e.tap(t["close"], seek=0)
        e.tap(t["oss"], wait=2.0); e.shot("09-about-licenses")
        e.tap(t["sqlc"], wait=1.8, seek=30); e.shot("09-about-license-text"); e.tap(t["close"], seek=0)
        e.key("KEYCODE_BACK"); e.key("KEYCODE_BACK"); e.key("KEYCODE_BACK")
    if want("lock"):
        e.home(t)
        e.top(); e.tap(t["privacy"], desc=True, wait=1.8)
        e.tap(t["pin"], wait=1.8); e.shot("10-lock-pin-setup")
        for _ in range(2):
            for d in "123456":
                e.tap_exact(d)
            time.sleep(1.2)
        e.tap(t["appLock"], required=False)  # app lock on (the PIN is what unlocks it on an emulator without a screen lock)
        e.key("KEYCODE_HOME"); time.sleep(1.5)
        e.sh(f"am start -n {PKG}/{ACT}"); time.sleep(2.5)
        e.shot("10-lock-screen")
        for d in "123456":
            e.tap_exact(d)
        time.sleep(1.5)
    for key, name in [("period", "11-today-in-period"), ("expected", "11-today-expected"), ("hbc", "11-today-paused")]:
        if want("states"):
            e.home(t); demo(e, t, t[key]); e.shot(name)
    if want("conceive"):
        e.home(t)
        demo(e, t, t["conceive"]); e.shot("12-conceive-1"); e.scroll(); e.shot("12-conceive-2")
        e.top(); e.scroll(3)
        if e.tap(t["why"], required=False, wait=1.8):
            e.shot("12-conceive-why-sheet"); e.key("KEYCODE_BACK")
        e.top(); e.tap(t["calendar"], wait=1.8); e.shot("12-conceive-calendar"); e.tap(t["today"])
        demo(e, t, t["twoww"]); e.shot("12-conceive-2ww")
    if dark and want("dark"):
        e.home(t); e.sh("cmd uimode night yes"); time.sleep(2)
        demo(e, t, t["day12"]); e.shot("13-dark-today")
        e.tap(t["calendar"], wait=1.5); e.shot("13-dark-calendar")
        e.tap(t["insights"], wait=1.5); e.shot("13-dark-insights")
        e.tap(t["today"]); e.tap(t["log"], wait=1.8); e.shot("13-dark-log"); e.key("KEYCODE_BACK")
        e.sh("cmd uimode night no")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", default="emulator-5554")
    ap.add_argument("--lang", choices=list(LANG_DIRS), default="de")
    ap.add_argument("--only")
    ap.add_argument("--scale", type=float, default=0.5)
    ap.add_argument("--no-dark", action="store_true")
    a = ap.parse_args()
    # the German set is the committed reference; other languages go to build/ for review only
    out = ROOT / "docs" / "screens" / "android" if a.lang == "de" else ROOT / "build" / "screens" / a.lang
    out.mkdir(parents=True, exist_ok=True)
    e = Emu(a.serial, out, a.scale)
    run(e, a.lang, a.only, dark=not a.no_dark)


if __name__ == "__main__":
    main()
