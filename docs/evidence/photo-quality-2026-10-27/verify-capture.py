#!/usr/bin/env python3
"""Independent floating-shutter photo capture helper for the photo-quality verification.

Usage:
    python verify-capture.py <RECOMMENDED|QUALITY> [tag]

Writes screenshots, logcat dumps and the pulled JPEG into the evidence directory.
"""
import json
import os
import re
import subprocess
import sys
import time

SERIAL = "emulator-5580"
PKG = "com.zgybeep.alibi.debug"
COMPONENT = f"{PKG}/app.myzel394.alibi.ShortcutActivity"
ACTION = f"{PKG}.shortcut.TAKE_PHOTO"
EVID = r"T:\Github\Alibi\docs\evidence\photo-quality-2026-10-27"


def adb(args, input_data=None, timeout=120):
    return subprocess.run(
        ["adb", "-s", SERIAL] + args,
        input=input_data,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        text=True,
        encoding="utf-8",
        errors="replace",
    )


def shell(cmd, input_data=None, timeout=120):
    return adb(["shell", cmd], input_data=input_data, timeout=timeout)


def out(cmd, timeout=120):
    return (shell(cmd, timeout=timeout).stdout or "")


def overlay_attrs():
    dump = out("dumpsys window windows", timeout=90)
    for block in dump.split("Window #"):
        if f"package={PKG}" not in block or "ty=APPLICATION_OVERLAY" not in block:
            continue
        m = re.search(r"mAttrs=\{\((\d+),(\d+)\)\((\d+)x(\d+)\)", block)
        if m:
            return tuple(map(int, m.groups()))
    return None


def list_photos():
    text = out(f"run-as {PKG} ls files/photos 2>/dev/null")
    return [line.strip() for line in text.splitlines() if line.strip()]


def photo_stat(name):
    text = out(f"run-as {PKG} stat -c '%s' 'files/photos/{name}' 2>/dev/null")
    try:
        return int(text.strip())
    except ValueError:
        return -1


def wait_until(fn, timeout, interval=0.8, what=""):
    end = time.time() + timeout
    while time.time() < end:
        value = fn()
        if value:
            return value
        time.sleep(interval)
    raise SystemExit(f"TIMEOUT waiting for {what}")


def logcat_dump():
    return out("logcat -d", timeout=90)


def write_settings(preference):
    payload = json.dumps(
        {
            "hasSeenOnboarding": True,
            "photoCaptureSettings": {"resolutionPreference": preference},
        },
        separators=(",", ":"),
    )
    cmd = (
        f"run-as {PKG} sh -c 'mkdir -p files/datastore && "
        "cat > files/datastore/settings.json'"
    )
    result = shell(cmd, input_data=payload + "\n", timeout=60)
    if result.returncode != 0:
        raise SystemExit("could not write settings: %r" % (result.stderr,))
    stored = out(f"run-as {PKG} cat files/datastore/settings.json")
    print("settings.json on device:", stored.strip(), flush=True)
    if preference not in stored:
        raise SystemExit("settings.json did not contain requested preference")
    return payload


def screencap(path):
    result = subprocess.run(
        ["adb", "-s", SERIAL, "exec-out", "screencap", "-p"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=60,
    )
    with open(path, "wb") as handle:
        handle.write(result.stdout)


def main():
    preference = sys.argv[1]
    tag = sys.argv[2] if len(sys.argv) > 2 else preference.lower()
    summary = {"preference": preference, "tag": tag, "steps": []}

    print(f"=== {preference} ({tag}) ===", flush=True)
    out(f"am force-stop {PKG}")
    write_settings(preference)
    adb(["logcat", "-c"])
    time.sleep(1)

    start = shell(f"am start -a {ACTION} -n {COMPONENT}", timeout=60)
    print("am start:", (start.stdout or "").strip(), "err:", (start.stderr or "").strip(), flush=True)
    time.sleep(2)

    attrs = wait_until(overlay_attrs, 30, what="floating overlay window")
    summary["overlayAttrs"] = attrs
    print("overlay attrs (x,y,w,h):", attrs, flush=True)

    log = wait_until(
        lambda: "Image capture mode=" in logcat_dump(),
        timeout=45,
        what="camera session log line",
    )
    print("camera session log:", log, flush=True)
    session_line = ""
    for line in logcat_dump().splitlines():
        if "Image capture mode=" in line:
            session_line = line
    summary["sessionLogLine"] = session_line
    print("session line:", session_line, flush=True)

    # Floating shutter should be READY by now (session bound + state published).
    time.sleep(7)
    screencap(os.path.join(EVID, f"verify-overlay-{tag}.png"))
    with open(os.path.join(EVID, f"verify-window-{tag}.txt"), "w", encoding="utf-8") as handle:
        handle.write(out("dumpsys window windows", timeout=90))

    x, y, w, h = attrs
    cx, cy = x + w // 2, y + h // 2
    before = set(list_photos())
    summary["photosBefore"] = sorted(before)
    print("photos before:", sorted(before), flush=True)

    new_name = None
    attempts = 0
    for attempts in range(1, 4):
        print(f"tap #{attempts} at ({cx},{cy})", flush=True)
        shell(f"input tap {cx} {cy}")
        time.sleep(2)
        try:
            fresh = wait_until(
                lambda: (set(list_photos()) - before) or None,
                timeout=12,
                what="new photo file",
            )
            new_name = sorted(fresh)[-1]
            break
        except SystemExit:
            print("no file after tap, retrying", flush=True)
    if new_name is None:
        with open(os.path.join(EVID, f"verify-logcat-{tag}.txt"), "w", encoding="utf-8") as handle:
            handle.write(logcat_dump())
        raise SystemExit("no photo produced after 3 taps")

    # Wait for the save pipeline to finish writing the file.
    last_size = -1
    stable = 0
    deadline = time.time() + 40
    while time.time() < deadline:
        size = photo_stat(new_name)
        if size > 0 and size == last_size:
            stable += 1
            if stable >= 2:
                break
        else:
            stable = 0
        last_size = size
        time.sleep(1.5)
    summary["newPhoto"] = new_name
    summary["sizeBytes"] = photo_stat(new_name)
    summary["tapAttempts"] = attempts
    print("new photo:", new_name, "size:", summary["sizeBytes"], flush=True)

    time.sleep(2)
    log_text = logcat_dump()
    with open(os.path.join(EVID, f"verify-logcat-{tag}.txt"), "w", encoding="utf-8") as handle:
        handle.write(log_text)

    latency = ""
    for line in log_text.splitlines():
        if "Photo captured" in line and "latencyMs=" in line:
            latency = line
    summary["captureLogLine"] = latency
    print("capture line:", latency, flush=True)

    pull = subprocess.run(
        ["adb", "-s", SERIAL, "exec-out", "run-as", PKG, "cat", f"files/photos/{new_name}"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=120,
    )
    jpeg_path = os.path.join(EVID, f"verify-{tag}.jpg")
    with open(jpeg_path, "wb") as handle:
        handle.write(pull.stdout)
    summary["jpegPath"] = jpeg_path
    summary["jpegBytesOnHost"] = os.path.getsize(jpeg_path)
    print("pulled JPEG:", jpeg_path, summary["jpegBytesOnHost"], "bytes", flush=True)

    screencap(os.path.join(EVID, f"verify-after-capture-{tag}.png"))
    out(f"am force-stop {PKG}")

    meta_path = os.path.join(EVID, f"verify-meta-{tag}.json")
    with open(meta_path, "w", encoding="utf-8") as handle:
        json.dump(summary, handle, indent=2, ensure_ascii=False)
    print("META:", json.dumps(summary, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
