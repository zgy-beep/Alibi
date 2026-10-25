"""Regression audit for launcher shortcuts and running-state UI.

This script operates only on the explicitly selected emulator below. It changes
the debug app's permissions and local settings, so it must never be pointed at a
physical device.
"""

from pathlib import Path
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
ADB = r"C:\Android\Sdk\platform-tools\adb.exe"
SERIAL = "emulator-5580"
DEBUG = "com.zgybeep.alibi.debug"
RELEASE = "com.zgybeep.alibi"
ACTIVITY_CLASS = "app.myzel394.alibi.MainActivity"
SHORTCUT_CLASS = "app.myzel394.alibi.ShortcutActivity"
RUNNING_DESCRIPTION = "Alibi 正在运行"
results = []


def adb(*args, timeout=60, binary=False, data=None):
    proc = subprocess.run(
        [ADB, "-s", SERIAL, *args],
        input=data,
        capture_output=True,
        timeout=timeout,
    )
    if binary:
        return proc.stdout
    return (proc.stdout + proc.stderr).decode("utf-8", errors="replace")


def save(name, content):
    target = OUT / name
    if isinstance(content, bytes):
        target.write_bytes(content)
    else:
        target.write_text(content, encoding="utf-8")


def record(name, passed, details):
    result = {"case": name, "passed": passed, "details": details}
    results.append(result)
    save("repair-results.json", json.dumps(results, ensure_ascii=False, indent=2))
    print(json.dumps(result, ensure_ascii=True), flush=True)


def stop_app(package):
    adb("shell", "am", "force-stop", package)


def home():
    adb("shell", "input", "keyevent", "KEYCODE_HOME")


def reset_logs():
    adb("logcat", "-c")


def crash_text():
    return adb("logcat", "-d", "-b", "crash")


def services(package):
    return adb("shell", "dumpsys", "activity", "services", package)


def activities():
    return adb("shell", "dumpsys", "activity", "activities")


def resumed_activity():
    match = re.search(r"topResumedActivity=ActivityRecord\{[^\n}]*\}", activities())
    return match.group(0) if match else ""


def write_settings(body):
    adb("shell", "run-as", DEBUG, "mkdir", "-p", "files/datastore")
    adb(
        "shell",
        "run-as",
        DEBUG,
        "tee",
        "files/datastore/settings.json",
        data=body.encode("utf-8"),
    )


def launch_main(package=DEBUG):
    return adb(
        "shell",
        "am",
        "start",
        "-W",
        "-n",
        f"{package}/{ACTIVITY_CLASS}",
    )


def launch_shortcut(action):
    return adb(
        "shell",
        "am",
        "start",
        "-W",
        "-f",
        "0x1000c000",
        "-n",
        f"{DEBUG}/{SHORTCUT_CLASS}",
        "-a",
        f"{DEBUG}.shortcut.{action}",
    )


def dump_ui(name):
    remote = f"/sdcard/{name}.xml"
    adb("shell", "uiautomator", "dump", remote)
    adb("pull", remote, str(OUT / (name + ".xml")))
    save(name + ".png", adb("exec-out", "screencap", "-p", binary=True))
    try:
        root = ET.parse(OUT / (name + ".xml"))
    except (ET.ParseError, FileNotFoundError):
        return False
    return any(
        node.attrib.get("content-desc") == RUNNING_DESCRIPTION
        or node.attrib.get("text") == RUNNING_DESCRIPTION
        for node in root.iter("node")
    )


if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
    raise SystemExit("Refusing to operate: explicit target is not an emulator")
if adb("shell", "getprop", "sys.boot_completed").strip() != "1":
    raise SystemExit("Explicit emulator has not completed boot")

adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
adb("shell", "wm", "dismiss-keyguard")
save(
    "device-environment.txt",
    "Serial: "
    + SERIAL
    + "\nAVD: "
    + adb("emu", "avd", "name")
    + "\nro.kernel.qemu="
    + adb("shell", "getprop", "ro.kernel.qemu")
    + "\nAndroid="
    + adb("shell", "getprop", "ro.build.version.release")
    + "\nAPI="
    + adb("shell", "getprop", "ro.build.version.sdk")
    + "\nABI="
    + adb("shell", "getprop", "ro.product.cpu.abi"),
)

install_debug = adb(
    "install",
    "-r",
    str(ROOT / "app/build/outputs/apk/debug/app-x86_64-debug.apk"),
    timeout=120,
)
save("install-debug.txt", install_debug)
record("install-debug", "Success" in install_debug, install_debug.strip())

adb("shell", "pm", "clear", DEBUG)
write_settings('{"hasSeenOnboarding":true}')
for permission in ("RECORD_AUDIO", "CAMERA", "POST_NOTIFICATIONS"):
    adb("shell", "pm", "revoke", DEBUG, "android.permission." + permission)
adb("shell", "appops", "set", DEBUG, "SYSTEM_ALERT_WINDOW", "deny")

stop_app(DEBUG)
home()
reset_logs()
save("main-launch.txt", launch_main())
time.sleep(2)
main_crash = crash_text()
save("main-launch-crash.txt", main_crash)
record(
    "debug-main-launches-without-shortcut-conflict",
    "FATAL EXCEPTION" not in main_crash,
    "MainActivity launched",
)

for case_name, action in (
    ("missing-microphone", "RECORD_AUDIO"),
    ("missing-camera-overlay", "TAKE_PHOTO"),
):
    stop_app(DEBUG)
    home()
    reset_logs()
    save(case_name + "-launch.txt", launch_shortcut(action))
    time.sleep(3)
    crash = crash_text()
    save(case_name + "-crash.txt", crash)
    record(
        case_name + "-opens-main-without-crash",
        "FATAL EXCEPTION" not in crash
        and ACTIVITY_CLASS in resumed_activity(),
        "Permission fallback reached MainActivity",
    )

for permission in ("RECORD_AUDIO", "CAMERA", "POST_NOTIFICATIONS"):
    adb("shell", "pm", "grant", DEBUG, "android.permission." + permission)
adb("shell", "appops", "set", DEBUG, "SYSTEM_ALERT_WINDOW", "allow")
write_settings('{"hasSeenOnboarding":true}')

stop_app(DEBUG)
home()
reset_logs()
save("audio-cold-launch.txt", launch_shortcut("RECORD_AUDIO"))
time.sleep(4)
audio_services = services(DEBUG)
audio_files = adb("shell", "run-as", DEBUG, "ls", "-laR", "files")
audio_crash = crash_text()
save("audio-cold-services.txt", audio_services)
save("audio-cold-files.txt", audio_files)
save("audio-cold-crash.txt", audio_crash)
record(
    "authorized-audio-starts-without-main",
    "AudioRecorderService" in audio_services
    and "isForeground=true" in audio_services
        and ACTIVITY_CLASS not in resumed_activity()
    and "FATAL EXCEPTION" not in audio_crash
    and ".aac" in audio_files,
    "Foreground audio service and AAC file present",
)

reset_logs()
save("audio-main-launch.txt", launch_main())
time.sleep(2)
audio_green = dump_ui("audio-running-ui")
record(
    "audio-running-green-indicator",
    audio_green and "FATAL EXCEPTION" not in crash_text(),
    "Running status semantics found" if audio_green else "Running status semantics missing",
)

reset_logs()
save("audio-repeat-launch.txt", launch_shortcut("RECORD_AUDIO"))
time.sleep(2)
repeat_crash = crash_text()
save("audio-repeat-crash.txt", repeat_crash)
save("audio-repeat-services.txt", services(DEBUG))
record(
    "repeat-audio-shortcut-is-safe",
    "FATAL EXCEPTION" not in repeat_crash
    and "isForeground=true" in services(DEBUG),
    "Existing audio session remained foreground",
)

stop_app(DEBUG)
home()
reset_logs()
save("audio-stopped-main-launch.txt", launch_main())
time.sleep(2)
stopped_green = dump_ui("audio-stopped-ui")
record(
    "audio-stopped-green-indicator-hidden",
    not stopped_green and "FATAL EXCEPTION" not in crash_text(),
    "Running status semantics absent after force stop",
)

stop_app(DEBUG)
home()
reset_logs()
save("photo-cold-launch.txt", launch_shortcut("TAKE_PHOTO"))
time.sleep(7)
photo_crash = crash_text()
photo_services = services(DEBUG)
photo_windows = adb("shell", "dumpsys", "window", "windows")
save("photo-cold-crash.txt", photo_crash)
save("photo-cold-services.txt", photo_services)
save("photo-cold-windows.txt", photo_windows)
record(
    "authorized-photo-starts-floating",
    "PhotoCaptureService" in photo_services
    and "isForeground=true" in photo_services
    and "appop=SYSTEM_ALERT_WINDOW" in photo_windows
        and ACTIVITY_CLASS not in resumed_activity()
    and "FATAL EXCEPTION" not in photo_crash,
    "Floating foreground photo capture service present",
)

reset_logs()
save("photo-main-launch.txt", launch_main())
time.sleep(2)
photo_green = dump_ui("photo-running-ui")
record(
    "photo-running-green-indicator",
    photo_green and "FATAL EXCEPTION" not in crash_text(),
    "Running status semantics found" if photo_green else "Running status semantics missing",
)
home()
time.sleep(2)
save("photo-screen.png", adb("exec-out", "screencap", "-p", binary=True))
photo_windows = adb("shell", "dumpsys", "window", "windows")
save("photo-tap-windows.txt", photo_windows)
window = re.search(
    r"Window #\d+ Window\{[^\n]*" + re.escape(DEBUG) + r"\}:(.*?)(?=\n  Window #|\Z)",
    photo_windows,
    re.S,
)
frame = re.search(r"mAttrs=\{\((\d+),(\d+)\)\((\d+)x(\d+)\)", window.group(1)) if window else None
if frame:
    x, y, w, h = map(int, frame.groups())
    adb("shell", "input", "tap", str(x + w // 2), str(y + h // 2))
    time.sleep(4)
photo_files = adb("shell", "run-as", DEBUG, "ls", "-laR", "files")
photo_log = adb("logcat", "-d", "-v", "threadtime")
save("photo-tap-files.txt", photo_files)
save("photo-tap-logcat.txt", photo_log)
record(
    "floating-button-saves-photo",
    bool(frame) and ".jpg" in photo_files and "Photo saved:" in photo_log,
    "JPEG saved from floating trigger" if frame else "Overlay geometry not found",
)

stop_app(DEBUG)
home()
reset_logs()
write_settings('{"hasSeenOnboarding":true,"saveFolder":"not-a-tree-uri"}')
save("invalid-folder-launch.txt", launch_shortcut("RECORD_AUDIO"))
time.sleep(3)
invalid_crash = crash_text()
save("invalid-folder-crash.txt", invalid_crash)
record(
    "invalid-folder-recovers-without-crash",
    "FATAL EXCEPTION" not in invalid_crash
        and ACTIVITY_CLASS in resumed_activity(),
    "Invalid folder opened MainActivity without crash",
)
write_settings('{"hasSeenOnboarding":true}')

stop_app(DEBUG)
home()
reset_logs()
release_install = adb(
    "install",
    "-r",
    str(ROOT / "app/build/outputs/apk/release/app-x86_64-release.apk"),
    timeout=120,
)
save("install-release.txt", release_install)
save("release-main-launch.txt", launch_main(RELEASE))
time.sleep(3)
release_crash = crash_text()
save("release-launch-crash.txt", release_crash)
record(
    "release-main-launches-without-shortcut-conflict",
    "Success" in release_install and "FATAL EXCEPTION" not in release_crash,
    "Release MainActivity launched",
)

stop_app(RELEASE)
home()
failed = [item["case"] for item in results if not item["passed"]]
save(
    "repair-summary.txt",
    f"cases={len(results)}\npassed={len(results) - len(failed)}\nfailed={failed}\n",
)
print(f"Repair audit complete: {len(results) - len(failed)}/{len(results)} passed", flush=True)
if failed:
    raise SystemExit("Failed cases: " + ", ".join(failed))
