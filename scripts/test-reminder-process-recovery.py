#!/usr/bin/env python3
"""Kill a real instrumented process at JDWP breakpoints on a disposable emulator."""
import os
import pathlib
import select
import subprocess
import time
import json
import shlex
import xml.etree.ElementTree as ET

SERIAL = os.environ["XIKE_DEVICE_SERIAL"]
SDK = pathlib.Path(os.environ.get("ANDROID_HOME", str(pathlib.Path.home() / "Library/Android/sdk")))
ADB = [str(SDK / "platform-tools/adb"), "-s", SERIAL]
JDB = pathlib.Path(os.environ["JAVA_HOME"]) / "bin/jdb"
OUT = pathlib.Path(os.environ["XIKE_REMINDER_REPORT_DIR"])
OUT.mkdir(parents=True, exist_ok=True)
TEST = "com.xike.app.ReminderProcessRecoveryTest#recoverAcrossProcessDeath"
NATURAL = os.environ.get("XIKE_REMINDER_NATURAL")


def preferences(package, filename):
    raw = adb("shell", "run-as", package, "cat", f"shared_prefs/{filename}.xml")
    root = ET.fromstring(raw)
    return {element.attrib["name"]: element.attrib.get("value", element.text) for element in root}, raw


def observe_natural(scenario, submitted):
    root = ET.fromstring((OUT / f"{scenario}-persisted.xml").read_text())
    initial = {element.attrib["name"]: element.attrib.get("value", element.text) for element in root}
    expected_id = initial["delivery-id"]
    retry_at = int(initial["pending-at"]) + 15 * 60 * 1000
    observer_pid = adb("shell", "pidof", "com.xike.app.test")
    start = time.monotonic()
    recovered_at = None
    samples = []
    print(f"NATURAL WAIT {scenario} expected_id={expected_id}; no further instrumentation or app launch", flush=True)
    while time.monotonic() - start < 75 * 60:
        now = time.monotonic()
        observed, raw = preferences("com.xike.app.test", "reminder-observer")
        events = json.loads(observed.get("events", "[]"))
        relevant = [event for event in events if event["tag"] == expected_id]
        if len(relevant) > 1:
            (OUT / "duplicate-events.xml").write_text(raw)
            raise AssertionError(f"Repeated system notification callback: {relevant}")
        state, state_raw = preferences("com.xike.app", "xike-reminder-schedule")
        app_pid = subprocess.run(ADB + ["shell", "pidof", "com.xike.app"], text=True, capture_output=True).stdout.strip()
        if adb("shell", "pidof", "com.xike.app.test") != observer_pid:
            raise AssertionError("Observer process restarted; callback continuity cannot be proven")
        samples.append({"elapsedSeconds": round(now - start, 1), "appPid": app_pid, "callbacks": len(relevant), "pendingAt": state.get("pending-at"), "deliveryId": state.get("delivery-id")})
        (OUT / "natural-observation.json").write_text(json.dumps(samples, indent=2))
        if relevant and "delivery-id" not in state:
            if recovered_at is None:
                if int(time.time() * 1000) < retry_at - 1000:
                    raise AssertionError("Recovery preceded the retry alarm; natural wakeup is not proven")
                recovered_at = now
                (OUT / "natural-recovered-state.xml").write_text(state_raw)
                (OUT / "natural-recovered-observer.xml").write_text(raw)
                (OUT / "natural-recovered-alarms.txt").write_text(adb("shell", "dumpsys", "alarm"))
                print(f"NATURAL RECOVERED {scenario} after {now - start:.1f}s; observing another 5 minutes", flush=True)
            if now - recovered_at >= 5 * 60:
                keys = adb("shell", "cmd", "notification", "list").splitlines()
                own = [key for key in keys if "|com.xike.app|402|" in key]
                if len(own) != 1 or expected_id not in own[0]:
                    raise AssertionError(f"Unexpected active notification keys: {own}")
                if int(state["pending-at"]) <= int(relevant[0]["observedAt"]):
                    raise AssertionError("Next reminder did not advance")
                (OUT / "natural-final-observer.xml").write_text(raw)
                (OUT / "natural-final-notification.txt").write_text(adb("shell", "cmd", "notification", "get", own[0]))
                (OUT / "natural-process-logcat.txt").write_text(adb("logcat", "-d", "-v", "threadtime", "-s", "ActivityManager", "ActivityTaskManager", "BroadcastQueue", "TestRunner", "System.out"))
                result = {"scenario": scenario, "passed": True, "elapsedSeconds": round(now - start, 1), "retryAt": retry_at, "observerPid": observer_pid, "notificationCallbacks": relevant, "activeKeys": own, "manuallyReopened": False, "manualBroadcast": False}
                (OUT / "natural-result.json").write_text(json.dumps(result, indent=2))
                print(f"PASS NATURAL {scenario}: {json.dumps(result)}", flush=True)
                return
        if len(samples) % 3 == 1:
            print(f"NATURAL PROGRESS {scenario} elapsed={now - start:.0f}s callbacks={len(relevant)} recovered={recovered_at is not None}", flush=True)
        time.sleep(20)
    raise TimeoutError("Natural retry did not complete within 75 minutes")


def adb(*args):
    command_args = ["shell", shlex.join(args[1:])] if args and args[0] == "shell" else list(args)
    return subprocess.check_output(ADB + command_args, text=True).strip()


def await_text(process, expected, log, timeout=90):
    deadline = time.monotonic() + timeout
    result = ""
    while time.monotonic() < deadline:
        ready, _, _ = select.select([process.stdout], [], [], 0.2)
        if ready:
            data = os.read(process.stdout.fileno(), 8192).decode(errors="replace")
            if not data:
                raise RuntimeError(f"Debugger exited while waiting for {expected}: {result}")
            log.write(data)
            log.flush()
            result += data
            if expected in result:
                return result
    raise TimeoutError(f"Waiting for {expected}: {result}")


def command(process, text):
    process.stdin.write((text + "\n").encode())
    process.stdin.flush()


name = adb("emu", "avd", "name")
if name.splitlines()[0] not in {"xike_reminder_crash", "xike_reminder_crash_post"}:
    raise RuntimeError("Only the task's disposable xike_reminder_crash AVD is allowed")
source = pathlib.Path("app/src/main/java/com/xike/app/ReminderScheduler.kt").read_text().splitlines()
after_notify = next(i + 2 for i, line in enumerate(source) if "manager.notify(deliveryId," in line)
confirmation = next(i for i, line in enumerate(source) if '.remove("delivery-id")' in line)
after_commit = next(i + 1 for i, line in enumerate(source) if "alarmManager.setAndAllowWhileIdle(" in line and i > confirmation)
scenarios = [
    ("before-submit", "stop in com.xike.app.ReminderScheduler.showReminder", False, False),
    ("after-submit", f"stop at com.xike.app.ReminderScheduler:{after_notify}", True, False),
    ("after-confirm", f"stop at com.xike.app.ReminderScheduler:{after_commit}", True, True),
]
if NATURAL:
    scenarios = [scenario for scenario in scenarios if scenario[0] == NATURAL]
    if NATURAL not in {"before-submit", "after-submit"}:
        raise ValueError("XIKE_REMINDER_NATURAL must be before-submit or after-submit")
    if os.environ.get("XIKE_REMINDER_RESUME_NATURAL") == "yes":
        observe_natural(NATURAL, NATURAL == "after-submit")
        exit(0)
for scenario, breakpoint, submitted, acknowledged in scenarios:
    print(f"START {scenario}: {breakpoint}", flush=True)
    with (OUT / f"{scenario}-submit.txt").open("w") as instrument_log:
        if NATURAL:
            setup = adb("shell", "am", "instrument", "-w", "-e", "class", TEST, "-e", "dedicatedReminderDevice", "yes", "-e", "reminderProcessPhase", "arm", "com.xike.app.test/androidx.test.runner.AndroidJUnitRunner")
            instrument_log.write(setup)
            instrument_log.flush()
            if "OK (1 test)" not in setup:
                raise AssertionError(setup)
            adb("shell", "cmd", "package", "unstop", "com.xike.app.test")
            listener = "com.xike.app.test/com.xike.app.ReminderNotificationObserver"
            adb("shell", "cmd", "notification", "disallow_listener", listener)
            adb("shell", "cmd", "notification", "allow_listener", listener)
            listener_deadline = time.monotonic() + 10
            while time.monotonic() < listener_deadline:
                if subprocess.run(ADB + ["shell", "pidof", "com.xike.app.test"], text=True, capture_output=True).stdout.strip():
                    break
                time.sleep(0.2)
            armed, _ = preferences("com.xike.app", "xike-reminder-schedule")
            due = int(armed["pending-at"]) / 1000
            while int(adb("shell", "date", "+%s")) <= due:
                time.sleep(0.5)
            # Instrumentation has ended. The killed process below is a normal app process.
            instrument = subprocess.Popen(ADB + ["shell", "am", "start", "-D", "-n", "com.xike.app/.MainActivity"], stdout=instrument_log, stderr=subprocess.STDOUT)
        else:
            instrument = subprocess.Popen(ADB + ["shell", "am", "instrument", "-w", "-e", "debug", "true", "-e", "class", TEST, "-e", "dedicatedReminderDevice", "yes", "-e", "reminderProcessPhase", "submit", "com.xike.app.test/androidx.test.runner.AndroidJUnitRunner"], stdout=instrument_log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 20
        pid = ""
        while time.monotonic() < deadline:
            pid = subprocess.run(ADB + ["shell", "pidof", "com.xike.app"], text=True, capture_output=True).stdout.strip()
            if pid:
                break
            time.sleep(0.2)
        if not pid:
            raise RuntimeError("Instrumentation process not found")
        port = adb("forward", "tcp:0", f"jdwp:{pid}")
        debugger = subprocess.Popen([str(JDB), "-J-Duser.language=en", "-J-Duser.country=US", "-attach", f"localhost:{port}"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        with (OUT / f"{scenario}-debugger.txt").open("w") as debug_log:
            await_text(debugger, ">", debug_log)
            command(debugger, breakpoint)
            await_text(debugger, ">", debug_log)
            command(debugger, "cont")
            hit = await_text(debugger, "main[1]", debug_log)
            if "Breakpoint hit:" not in hit:
                raise AssertionError(f"Expected a production breakpoint, got: {hit}")
            if NATURAL:
                adb("shell", "input", "keyevent", "KEYCODE_HOME")
                (OUT / "pre-kill-activity.txt").write_text(adb("shell", "dumpsys", "activity", "activities"))
            # The VM is suspended at the real production boundary, never a test hook.
            adb("shell", "run-as", "com.xike.app", "kill", "-9", pid)
            print(f"KILLED {scenario} pid={pid}: {hit[-350:]}", flush=True)
        instrument.wait(timeout=15)
        debugger.wait(timeout=15)
        adb("forward", "--remove", f"tcp:{port}")
        if NATURAL:
            adb("shell", "am", "clear-debug-app")
            (OUT / "post-kill-package.txt").write_text(adb("shell", "dumpsys", "package", "com.xike.app"))
        (OUT / f"{scenario}-persisted.xml").write_text(adb("shell", "run-as", "com.xike.app", "cat", "shared_prefs/xike-reminder-schedule.xml"))
        (OUT / f"{scenario}-alarms.txt").write_text(adb("shell", "dumpsys", "alarm"))
    if NATURAL:
        observe_natural(scenario, submitted)
        continue
    result = adb("shell", "am", "instrument", "-w", "-e", "class", TEST, "-e", "dedicatedReminderDevice", "yes", "-e", "reminderProcessPhase", "verify", "-e", "submitted", "yes" if submitted else "no", "-e", "acknowledged", "yes" if acknowledged else "no", "com.xike.app.test/androidx.test.runner.AndroidJUnitRunner")
    (OUT / f"{scenario}-verify.txt").write_text(result)
    if "OK (1 test)" not in result:
        raise AssertionError(f"Recovery failed for {scenario}: {result}")
    print(f"PASS {scenario}: {result}", flush=True)
print("Natural retry passed" if NATURAL else "All three production crash boundaries passed", flush=True)
