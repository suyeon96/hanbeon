//! 아두이노 스위치를 시리얼로 받는다.
//!
//! 스위치는 HID 키보드가 아니다. 아두이노 우노(ATmega328P)는 USB HID가 물리적으로
//! 불가능해서, 펌웨어가 눌림에 `P` 뗌에 `R`을 줄 단위로 보낸다. 그래서 전역 단축키로는
//! 이 스위치를 영영 받을 수 없다(docs/PRD.md 7절).
//!
//! 시리얼이라서 얻는 것이 두 가지 더 있다.
//!
//! - LED를 되돌려 켤 수 있다. 키보드는 단방향이라 못 한다. 접근성 원칙 4는 상태를 두
//!   감각으로 알리라고 하고, PRD F7의 LED 항목이 이 통로를 쓴다.
//! - 연결을 확인할 수 있다. `HELLO`에 `HANBEON_UNO_V1`로 답하는 포트만 고른다. 그래서
//!   '안 누르는 중'과 '뽑혔음'을 구분할 수 있다(PRD F10).

use std::io::{Read, Write};
use std::sync::mpsc::{self, Receiver, Sender};
use std::thread;
use std::time::{Duration, Instant};

/// 펌웨어와 맞춘 값. 바꾸면 양쪽을 함께 고친다.
const BAUD: u32 = 115_200;
const IDENT: &str = "HANBEON_UNO_V1";

/// 핸드셰이크 응답을 기다리는 시간. 아두이노는 포트를 열면 리셋되고 부트로더가
/// 끝나야 답한다. 짧게 잡으면 멀쩡한 보드를 못 찾는다.
const HANDSHAKE_WAIT: Duration = Duration::from_millis(2500);

/// 읽기 타임아웃. 이 주기로 LED 명령을 함께 흘려보낸다.
const READ_TIMEOUT: Duration = Duration::from_millis(50);

/// 끊긴 뒤 다시 찾기까지.
const RETRY: Duration = Duration::from_millis(1000);

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Signal {
    Press,
    Release,
}

/// 스위치에 보내는 것.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Command {
    /// 커서가 한 칸 옮겨졌다. 짧게 깜빡인다.
    Flash,
    Off,
}

impl Command {
    fn line(self) -> &'static [u8] {
        match self {
            Command::Flash => b"FLASH\n",
            Command::Off => b"OFF\n",
        }
    }
}

/// 코어에 알릴 것.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Event {
    Connected {
        port: String,
    },
    /// 뽑혔다. PRD F10에 따라 스캔을 정지로 내린다. 커서만 계속 도는 상태로 두면
    /// 사용자는 눌러도 아무 일이 없는 이유를 알 수 없다.
    Disconnected,
    Signal(Signal),
}

/// 한 줄을 신호로 읽는다.
///
/// 모르는 줄은 버린다. 아두이노는 리셋 직후 잡음을 뱉기도 하고, 펌웨어가 나중에
/// 다른 줄을 더 보낼 수도 있다. 모르는 줄에 반응해 스위치가 눌린 것으로 치면
/// 사용자는 누르지도 않은 동작을 겪는다.
pub fn parse_line(line: &str) -> Option<Signal> {
    match line.trim() {
        "P" => Some(Signal::Press),
        "R" => Some(Signal::Release),
        _ => None,
    }
}

/// 우리 보드가 맞는지.
pub fn is_ours(reply: &str) -> bool {
    reply.trim() == IDENT
}

/// 시리얼은 줄 단위로 오지 않는다. 한 번 읽어 온 덩어리에서 완성된 줄만 꺼낸다.
#[derive(Default)]
pub struct Lines {
    buf: String,
}

impl Lines {
    /// 덩어리를 넣고 완성된 줄들을 받는다. 남은 조각은 다음 호출로 이어진다.
    pub fn feed(&mut self, chunk: &str) -> Vec<String> {
        self.buf.push_str(chunk);
        let mut out = Vec::new();
        while let Some(at) = self.buf.find('\n') {
            let line: String = self.buf.drain(..=at).collect();
            out.push(line.trim_end_matches(['\n', '\r']).to_string());
        }
        out
    }
}

/// LED 명령을 보내는 손잡이.
#[derive(Clone)]
pub struct Led {
    tx: Option<Sender<Command>>,
}

impl Led {
    pub fn send(&self, command: Command) {
        if let Some(tx) = &self.tx {
            let _ = tx.send(command);
        }
    }
}

fn log_enabled() -> bool {
    std::env::var("HANBEON_LOG").is_ok()
}

/// `HELLO`에 답하는 포트를 찾는다.
///
/// 못 찾는 것은 사용자가 겪는 흔한 실패다. 조용히 넘어가면 "눌러도 아무 일이
/// 없다"만 남고 원인을 알 수 없으므로, 무엇을 시도했는지 남긴다.
fn find_port() -> Option<(String, Box<dyn serialport::SerialPort>)> {
    let ports = match serialport::available_ports() {
        Ok(ports) => ports,
        Err(error) => {
            if log_enabled() {
                eprintln!("[switch] 포트 목록을 읽지 못했습니다: {error}");
            }
            return None;
        }
    };

    if log_enabled() {
        let names: Vec<&str> = ports.iter().map(|p| p.port_name.as_str()).collect();
        eprintln!("[switch] 포트 {}개: {names:?}", ports.len());
    }

    for info in ports {
        // 맥에서 같은 장치가 tty/cu 두 이름으로 보인다. cu 쪽만 쓴다.
        // tty 쪽은 캐리어 신호를 기다리느라 열 때 멈출 수 있다.
        if info.port_name.contains("/dev/tty.") {
            continue;
        }

        let mut port = match serialport::new(&info.port_name, BAUD)
            .timeout(READ_TIMEOUT)
            .open()
        {
            Ok(port) => port,
            Err(error) => {
                if log_enabled() {
                    eprintln!("[switch] {} 열지 못함: {error}", info.port_name);
                }
                continue;
            }
        };

        if port.write_all(b"HELLO\n").is_err() {
            continue;
        }
        let _ = port.flush();

        let mut lines = Lines::default();
        let mut chunk = [0u8; 256];
        let deadline = Instant::now() + HANDSHAKE_WAIT;

        while Instant::now() < deadline {
            match port.read(&mut chunk) {
                Ok(0) => {}
                Ok(n) => {
                    let text = String::from_utf8_lossy(&chunk[..n]).into_owned();
                    let got = lines.feed(&text);
                    if log_enabled() && !got.is_empty() {
                        eprintln!("[switch] {} 응답: {got:?}", info.port_name);
                    }
                    if got.iter().any(|line| is_ours(line)) {
                        return Some((info.port_name, port));
                    }
                }
                Err(error) if error.kind() == std::io::ErrorKind::TimedOut => {
                    // 아직 부트로더일 수 있다. 다시 물어본다.
                    let _ = port.write_all(b"HELLO\n");
                }
                Err(_) => break,
            }
        }
    }

    if log_enabled() {
        eprintln!("[switch] 우리 보드를 찾지 못했습니다.");
    }
    None
}

/// 스위치를 계속 지켜본다. 뽑히면 다시 찾는다.
///
/// 사건을 콜백이 아니라 채널로 넘긴다. 받는 쪽(`Scanner`)이 LED 손잡이를 쥔
/// `Host`보다 늦게 만들어지기 때문이다. 콜백으로 받으면 배선이 순환한다.
pub fn watch() -> (Led, Receiver<Event>) {
    let (tx, rx): (Sender<Command>, Receiver<Command>) = mpsc::channel();
    let (events_tx, events_rx) = mpsc::channel::<Event>();
    let on_event = move |event: Event| {
        let _ = events_tx.send(event);
    };

    thread::spawn(move || {
        loop {
            let Some((name, port)) = find_port() else {
                thread::sleep(RETRY);
                continue;
            };

            on_event(Event::Connected { port: name.clone() });

            let mut writer = match port.try_clone() {
                Ok(writer) => writer,
                Err(_) => {
                    on_event(Event::Disconnected);
                    thread::sleep(RETRY);
                    continue;
                }
            };
            // `read_line`을 쓰지 않는다. 타임아웃이 줄 중간에 걸리면 그때까지 읽은
            // 조각이 남는데, 다음 회차에서 버퍼를 비우면 그 눌림이 통째로 사라진다.
            // 짧은 메시지라 드물지만 잃으면 사용자는 눌러도 반응이 없는 것을 겪는다.
            let mut reader = port;
            let mut lines = Lines::default();
            let mut chunk = [0u8; 256];

            loop {
                // 읽기가 타임아웃될 때마다 밀린 LED 명령을 흘려보낸다.
                while let Ok(command) = rx.try_recv() {
                    if writer.write_all(command.line()).is_err() {
                        break;
                    }
                    let _ = writer.flush();
                }

                match reader.read(&mut chunk) {
                    Ok(0) => break,
                    Ok(n) => {
                        let text = String::from_utf8_lossy(&chunk[..n]).into_owned();
                        for line in lines.feed(&text) {
                            if let Some(signal) = parse_line(&line) {
                                on_event(Event::Signal(signal));
                            }
                        }
                    }
                    Err(error) if error.kind() == std::io::ErrorKind::TimedOut => continue,
                    Err(_) => break,
                }
            }

            on_event(Event::Disconnected);
            thread::sleep(RETRY);
        }
    });

    (Led { tx: Some(tx) }, events_rx)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 눌림과_뗌을_읽는다() {
        assert_eq!(parse_line("P"), Some(Signal::Press));
        assert_eq!(parse_line("R"), Some(Signal::Release));
        assert_eq!(parse_line("P\r\n"), Some(Signal::Press));
    }

    #[test]
    fn 모르는_줄은_버린다() {
        // 아두이노는 리셋 직후 잡음을 뱉는다. 그걸 눌림으로 치면 사용자는
        // 누르지도 않은 동작을 겪는다.
        assert_eq!(parse_line(""), None);
        assert_eq!(parse_line("HANBEON_UNO_V1"), None);
        assert_eq!(parse_line("PR"), None);
        assert_eq!(parse_line("\u{0}\u{ff}"), None);
    }

    #[test]
    fn 핸드셰이크_응답을_알아본다() {
        assert!(is_ours("HANBEON_UNO_V1"));
        assert!(is_ours("HANBEON_UNO_V1\r\n"));
        assert!(!is_ours("HANBEON_UNO_V2"));
        assert!(!is_ours("OK"));
    }

    #[test]
    fn 조각난_줄을_이어_붙인다() {
        let mut lines = Lines::default();
        assert!(lines.feed("P").is_empty());
        assert_eq!(lines.feed("\nR\n"), vec!["P", "R"]);
    }

    #[test]
    fn 여러_줄이_한_번에_와도_다_꺼낸다() {
        let mut lines = Lines::default();
        assert_eq!(lines.feed("P\nR\nP\n"), vec!["P", "R", "P"]);
    }

    #[test]
    fn 캐리지리턴을_떼어_낸다() {
        let mut lines = Lines::default();
        assert_eq!(lines.feed("HANBEON_UNO_V1\r\n"), vec!["HANBEON_UNO_V1"]);
    }

    #[test]
    fn 명령은_줄바꿈으로_끝난다() {
        // 펌웨어가 개행을 만나야 명령을 실행한다.
        assert_eq!(Command::Flash.line(), b"FLASH\n");
        assert_eq!(Command::Off.line(), b"OFF\n");
    }
}
