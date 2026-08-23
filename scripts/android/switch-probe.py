"""스위치가 어떤 키를 어떻게 보내는지 본다.

PRD 7절은 스위치가 'USB/BT HID 키보드로서 특정 키 하나를 눌림 상태로 유지한다'고
가정한다. 이 가정이 틀리면 짧게/길게 누름을 판정할 수 없어 제품의 절반이 무너진다.
안드로이드에서 그 가정을 처음 확인하는 자리다.

`adb shell input keyevent`로는 검증할 수 없다. 그건 InputManager에 바로 넣는 것이라
`/dev/input`을 거치지 않아서 여기 잡히지 않는다. 실제 하드웨어를 눌러야 한다.

    python3 switch-probe.py <serial> [seconds]
    python3 switch-probe.py --selftest
"""

import re
import subprocess
import sys

# [   12345.678901] /dev/input/event9 EV_KEY       KEY_SPACE            DOWN
LINE = re.compile(
    r"\[\s*(\d+\.\d+)\]\s+(/dev/input/event\d+)\s+EV_KEY\s+(\S+)\s+"
    r"(DOWN_REPEAT|DOWN|UP)\b"
)


def parse(text):
    """getevent -lt 출력에서 키 눌림/뗌을 뽑는다."""
    out = []
    for line in text.replace("\r", "").splitlines():
        m = LINE.search(line)
        if m:
            at, dev, key, edge = m.groups()
            out.append((float(at), dev, key, edge))
    return out


def pair_holds(events):
    """눌림과 뗌을 짝지어 유지 시간(ms)을 낸다."""
    down, pairs = {}, []
    for at, dev, key, edge in events:
        if edge == "DOWN":
            down[(dev, key)] = at
        elif edge == "UP" and (dev, key) in down:
            pairs.append((dev, key, (at - down.pop((dev, key))) * 1000))
    return pairs


def device_names(serial):
    out = subprocess.run(
        ["adb", "-s", serial, "shell", "getevent", "-p"],
        capture_output=True, text=True,
    ).stdout.replace("\r", "")
    names, current = {}, None
    for line in out.splitlines():
        if line.startswith("add device"):
            current = line.split(":")[-1].strip()
        elif line.strip().startswith("name:") and current:
            names[current] = line.split(":", 1)[1].strip().strip('"')
    return names


def capture(serial, seconds):
    """기기 쪽에서 시간을 끊는다. 호스트에서 죽이면 버퍼가 날아갈 수 있다."""
    try:
        done = subprocess.run(
            ["adb", "-s", serial, "shell", f"timeout {seconds} getevent -lt"],
            capture_output=True, text=True, timeout=seconds + 15,
        )
        return done.stdout
    except subprocess.TimeoutExpired as e:
        return (e.stdout or b"").decode(errors="ignore") if isinstance(e.stdout, bytes) else (e.stdout or "")


def report(events, names):
    if not events:
        print("키 이벤트가 하나도 오지 않았습니다.")
        print("스위치가 꽂혀 있는지, 키보드로 인식되는지 확인해 주세요.")
        return False

    seen = {}
    for dev, key, ms in pair_holds(events):
        seen.setdefault((dev, key), []).append(ms)

    if not seen:
        downs = [e for e in events if e[3] == "DOWN"]
        print(f"눌림 {len(downs)}번이 왔지만 뗌이 짝지어지지 않았습니다.")
        return False

    print("=== 들어온 키 ===")
    for (dev, key), times in seen.items():
        name = names.get(dev, "?")
        avg = sum(times) / len(times)
        print(f"\n{key}   ({name}, {dev})")
        print(f"  누름 {len(times)}회, 눌림 시간 평균 {avg:.0f}ms "
              f"(최소 {min(times):.0f} / 최대 {max(times):.0f})")
        if max(times) < 60:
            print("  문제: 눌림이 유지되지 않음. 길게 누르기를 판정할 수 없음")
        else:
            print("  OK: 눌림이 유지됨. 짧게/길게 판정 가능")

    repeats = [e for e in events if e[3] == "DOWN_REPEAT"]
    if repeats:
        print(f"\n주의: 키 리피트가 {len(repeats)}번 왔습니다. 길게 누르면 한 번이")
        print("      여러 번으로 잡히므로 펌웨어에서 리피트를 꺼야 합니다.")
    else:
        print("\n키 리피트 없음.")
    return True


SAMPLE = """\
[   91.100000] /dev/input/event9 EV_KEY       KEY_F13              DOWN
[   91.100100] /dev/input/event9 EV_SYN       SYN_REPORT           00000000
[   91.350000] /dev/input/event9 EV_KEY       KEY_F13              UP
[   92.000000] /dev/input/event9 EV_KEY       KEY_F13              DOWN
[   92.900000] /dev/input/event9 EV_KEY       KEY_F13              DOWN_REPEAT
[   93.000000] /dev/input/event9 EV_KEY       KEY_F13              UP
"""


def selftest():
    events = parse(SAMPLE)
    assert len(events) == 5, events
    holds = pair_holds(events)
    assert len(holds) == 2, holds
    assert abs(holds[0][2] - 250) < 1, holds[0]
    assert abs(holds[1][2] - 1000) < 1, holds[1]
    assert holds[0][1] == "KEY_F13"
    assert [e[3] for e in events].count("DOWN_REPEAT") == 1, events
    print("파싱 자체 검증 통과 (눌림 250ms / 1000ms, 리피트 1회 인식)")
    report(events, {"/dev/input/event9": "가상 스위치"})


if __name__ == "__main__":
    if sys.argv[1:2] == ["--selftest"]:
        selftest()
    else:
        serial = sys.argv[1]
        seconds = int(sys.argv[2]) if len(sys.argv) > 2 else 20
        print(f"{seconds}초 동안 스위치를 여러 번 눌러 주세요. "
              f"짧게 몇 번, 길게 몇 번 섞어 주시면 좋습니다.\n")
        text = capture(serial, seconds)
        report(parse(text), device_names(serial))
