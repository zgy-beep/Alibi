"""Negative saved-folder probe; restores emulator settings in finally."""
from pathlib import Path
import subprocess
import time

ADB = r"C:\Android\Sdk\platform-tools\adb.exe"
SERIAL = "emulator-5580"
PACKAGE = "com.zgybeep.alibi.debug"
OUT = Path(__file__).resolve().parent


def run(*args, data=None):
    return subprocess.run([ADB, "-s", SERIAL, *args], input=data,
                          capture_output=True, timeout=30)


if run("shell", "getprop", "ro.kernel.qemu").stdout.strip() != b"1":
    raise SystemExit("Refusing to operate on a non-emulator")
run("shell", "am", "force-stop", PACKAGE)
before = run("exec-out", "run-as", PACKAGE, "cat", "files/datastore/settings.json")
try:
    run("shell", "run-as", PACKAGE, "mkdir", "-p", "files/datastore")
    run("shell", "run-as", PACKAGE, "tee", "files/datastore/settings.json",
        data=b'{"saveFolder":"not-a-tree-uri"}')
    run("logcat", "-c")
    launched = run("shell", "am", "start", "-W", "-f", "0x1000c000", "-n",
                   PACKAGE + "/app.myzel394.alibi.ShortcutActivity", "-a",
                   PACKAGE + ".shortcut.RECORD_AUDIO")
    (OUT / "invalid-folder-launch.txt").write_bytes(launched.stdout + launched.stderr)
    time.sleep(3)
    for name, args in [
        ("crash", ("logcat", "-d", "-b", "crash")),
        ("logcat", ("logcat", "-d", "-v", "threadtime")),
        ("services", ("shell", "dumpsys", "activity", "services", PACKAGE)),
    ]:
        (OUT / ("invalid-folder-" + name + ".txt")).write_bytes(run(*args).stdout)
finally:
    run("shell", "am", "force-stop", PACKAGE)
    if before.returncode == 0:
        run("shell", "run-as", PACKAGE, "tee", "files/datastore/settings.json", data=before.stdout)
    else:
        run("shell", "run-as", PACKAGE, "rm", "-f", "files/datastore/settings.json")
    print("Emulator settings restored")
