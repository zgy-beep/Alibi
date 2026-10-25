"""Reproduce acceptance probes on the explicitly selected emulator only.

Changes emulator app permissions and stops test app processes between cases.
Does not select devices automatically or access a physical device.
"""
from pathlib import Path
import datetime
import json
import re
import subprocess
import time

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
ADB = r"C:\Android\Sdk\platform-tools\adb.exe"
SERIAL = "emulator-5580"
PACKAGE = "com.zgybeep.alibi.debug"
CLASS_ROOT = "app.myzel394.alibi"
results = []


def adb(*args, timeout=40):
    proc = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, timeout=timeout)
    return (proc.stdout + proc.stderr).decode("utf-8", errors="replace")


def save(name, content):
    (OUT / name).write_text(content, encoding="utf-8")


def snapshot(name):
    save(name + "-logcat.txt", adb("logcat", "-d", "-v", "threadtime"))
    crash = adb("logcat", "-d", "-b", "crash")
    save(name + "-crash.txt", crash)
    services = adb("shell", "dumpsys", "activity", "services", PACKAGE)
    save(name + "-services.txt", services)
    activities = adb("shell", "dumpsys", "activity", "activities")
    save(name + "-activities.txt", activities)
    files = adb("shell", "run-as", PACKAGE, "ls", "-laR", "files")
    save(name + "-files.txt", files)
    record = {
        "case": name,
        "crashed": "FATAL EXCEPTION" in crash,
        "audio_service_present": "AudioRecorderService" in services,
        "photo_service_present": "PhotoCaptureService" in services,
        "main_activity_record_present": "MainActivity" in activities,
        "foreground_service": "isForeground=true" in services,
    }
    results.append(record)
    save("emulator-results.json", json.dumps(results, ensure_ascii=False, indent=2))
    print(json.dumps(record, ensure_ascii=True), flush=True)


def reset():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    adb("logcat", "-c")


def shortcut(action):
    return adb("shell", "am", "start", "-W", "-f", "0x1000c000", "-n",
               PACKAGE + "/" + CLASS_ROOT + ".ShortcutActivity", "-a",
               PACKAGE + ".shortcut." + action)


if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
    raise SystemExit("Refusing to operate: target is not an emulator")
if adb("shell", "getprop", "sys.boot_completed").strip() != "1":
    raise SystemExit("Emulator has not completed boot")
save("device-environment.txt", "Host date (UTC): " + datetime.datetime.now(datetime.timezone.utc).isoformat()
     + "\nSerial: " + SERIAL + "\nAVD: " + adb("emu", "avd", "name")
     + "\nro.kernel.qemu=" + adb("shell", "getprop", "ro.kernel.qemu")
     + "\nAndroid=" + adb("shell", "getprop", "ro.build.version.release")
     + "\nAPI=" + adb("shell", "getprop", "ro.build.version.sdk"))

reset()
for permission in ("RECORD_AUDIO", "CAMERA", "POST_NOTIFICATIONS"):
    print(adb("shell", "pm", "revoke", PACKAGE, "android.permission." + permission), flush=True)
print(adb("shell", "appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "deny"), flush=True)

for name, action in [("missing-microphone", "RECORD_AUDIO"), ("missing-camera-overlay", "TAKE_PHOTO")]:
    reset()
    save(name + "-launch.txt", shortcut(action))
    time.sleep(3)
    snapshot(name)

for permission in ("RECORD_AUDIO", "CAMERA", "POST_NOTIFICATIONS"):
    print(adb("shell", "pm", "grant", PACKAGE, "android.permission." + permission), flush=True)
print(adb("shell", "appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "allow"), flush=True)

reset()
save("audio-cold-launch.txt", shortcut("RECORD_AUDIO"))
time.sleep(5)
snapshot("audio-cold")
save("audio-repeat-launch.txt", shortcut("RECORD_AUDIO"))
time.sleep(3)
snapshot("audio-repeat")

reset()
save("photo-cold-launch.txt", shortcut("TAKE_PHOTO"))
time.sleep(7)
snapshot("photo-cold")
save("photo-cold-windows.txt", adb("shell", "dumpsys", "window", "windows"))
proc = subprocess.run([ADB, "-s", SERIAL, "exec-out", "screencap", "-p"], capture_output=True, timeout=30)
(OUT / "photo-cold-screen.png").write_bytes(proc.stdout)
save("photo-repeat-launch.txt", shortcut("TAKE_PHOTO"))
time.sleep(3)
snapshot("photo-repeat")
windows = adb("shell", "dumpsys", "window", "windows")
window = re.search(r"Window #\d+ Window\{[^\n]*" + re.escape(PACKAGE) + r"\}:(.*?)(?=\n  Window #|\Z)", windows, re.S)
frame = re.search(r"mAttrs=\{\((\d+),(\d+)\)\((\d+)x(\d+)\)", window.group(1)) if window else None
if frame:
    x, y, w, h = map(int, frame.groups())
    save("photo-tap.txt", adb("shell", "input", "tap", str(x + w // 2), str(y + h // 2)))
    time.sleep(4)
    snapshot("photo-tap")
else:
    save("photo-tap.txt", "No overlay geometry found; tap probe was not executed")

reset()
save("unknown-action-launch.txt", shortcut("UNKNOWN"))
time.sleep(2)
snapshot("unknown-action")

reset()
release = "com.zgybeep.alibi"
save("release-install.txt", adb("install", "-r", str(ROOT / "app/build/outputs/apk/release/app-x86_64-release.apk"), timeout=90))
save("release-main-launch.txt", adb("shell", "am", "start", "-W", "-n", release + "/" + CLASS_ROOT + ".MainActivity"))
time.sleep(3)
save("release-main-crash.txt", adb("logcat", "-d", "-b", "crash"))
adb("shell", "am", "force-stop", release)
print("Audit probes complete", flush=True)
