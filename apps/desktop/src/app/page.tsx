'use client'

import { Box, Text, VStack } from '@devup-ui/react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { useEffect, useState } from 'react'

import { DragHandle } from '@/components/DragHandle'
import { ScanProgress } from '@/components/ScanProgress'
import { StatusLine } from '@/components/StatusLine'
import { type CursorState, SwitchButton } from '@/components/SwitchButton'
import { UndoPanel } from '@/components/UndoPanel'
import {
  type CoverEvent,
  DEFAULT_SCAN_INTERVAL_MS,
  INTERVAL_NOTICE_MS,
  SCAN_ACTIONS,
  type ScanError,
  type ScanSnapshot,
} from '@/lib/actions'
import type { IntervalEvent } from '@/lib/profile'

const INITIAL: ScanSnapshot = {
  cursor: 0,
  action: 'next',
  mode: 'scanning',
  intervalMs: DEFAULT_SCAN_INTERVAL_MS,
  phaseMs: DEFAULT_SCAN_INTERVAL_MS,
  remainingMs: DEFAULT_SCAN_INTERVAL_MS,
}

/**
 * 코어가 보낸 상태와, 그것이 도착한 시각.
 *
 * 남은 시간은 '코어가 알려준 잔여 시간'에서 '그 뒤로 흐른 시간'을 빼야 나온다.
 * 도착 시각을 함께 들고 있지 않으면 이벤트 사이에서 눈금이 멈춘다.
 */
interface Timed {
  snapshot: ScanSnapshot
  at: number
}

/**
 * floating 컨트롤러.
 *
 * 커서 순환과 선택 판정의 주인은 Rust 코어(`scan` 모듈)다.
 * 이 화면은 코어가 보낸 상태를 그리기만 한다.
 */
export default function FloatingPage() {
  const [timed, setTimed] = useState<Timed>(() => ({
    snapshot: INITIAL,
    // 코어의 첫 응답이 오기 전까지 쓰는 임시 기준. 화면에 그려지는 값이
    // 아니라 서버·클라이언트가 달라도 문제가 되지 않는다.
    at: performance.now(),
  }))
  const [error, setError] = useState<ScanError | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [cover, setCover] = useState<CoverEvent>({
    covered: false,
    percent: 100,
  })

  useEffect(() => {
    let noticeTimer: ReturnType<typeof setTimeout> | undefined

    const receive = (snapshot: ScanSnapshot) =>
      setTimed({ snapshot, at: performance.now() })

    // 첫 틱이 오기 전까지 화면이 비지 않도록 현재 상태를 한 번 맞춘다.
    invoke<ScanSnapshot>('scan_snapshot')
      .then(receive)
      .catch(() => {
        // 브라우저에서 화면만 확인할 때는 Tauri 컨텍스트가 없다.
      })

    const unlistenState = listen<ScanSnapshot>('scan://state', (event) =>
      receive(event.payload),
    )
    const unlistenError = listen<ScanError>('scan://error', (event) =>
      setError(event.payload),
    )
    // 적응 로직이 속도를 바꿨을 때만 온다. 사용자가 모르는 채로 지나가서는
    // 안 되는 변화라서, 설정 화면이 닫혀 있어도 여기서 알린다(PRD F5).
    const unlistenInterval = listen<IntervalEvent>(
      'scan://interval',
      (event) => {
        setNotice(event.payload.reason)
        clearTimeout(noticeTimer)
        noticeTimer = setTimeout(() => setNotice(null), INTERVAL_NOTICE_MS)
      },
    )

    // 조작할 요소를 우리가 가리고 있는지. 코어가 상태가 바뀔 때만 보낸다.
    const unlistenCover = listen<CoverEvent>('window://cover', (event) =>
      setCover(event.payload),
    )

    return () => {
      clearTimeout(noticeTimer)
      unlistenState.then((stop) => stop()).catch(() => {})
      unlistenError.then((stop) => stop()).catch(() => {})
      unlistenInterval.then((stop) => stop()).catch(() => {})
      unlistenCover.then((stop) => stop()).catch(() => {})
    }
  }, [])

  const { snapshot, at } = timed
  const { cursor, mode } = snapshot

  // 되돌리기 창에서는 흐리게 하지 않는다. 3초 안에 눌러야 하는 안전 장치인데,
  // 그 순간 화면이 흐려지면 되돌릴 기회를 놓친다. 가려진 것보다 이쪽이 무겁다.
  const dimmed = cover.covered && mode !== 'confirm'

  const cursorStateAt = (index: number): CursorState => {
    if (mode === 'paused' || index !== cursor) return 'idle'
    return mode === 'dwelling' ? 'dwelling' : 'scanning'
  }

  return (
    <VStack
      aria-label="한번 스위치 컨트롤러"
      bg="$containerBackground"
      borderColor={mode === 'paused' ? '$warning' : '$borderBold'}
      borderRadius="20px"
      borderStyle="solid"
      borderWidth="2px"
      boxSizing="border-box"
      gap="8px"
      h="100vh"
      opacity={dimmed ? cover.percent / 100 : 1}
      overflow="hidden"
      p="10px"
      w="100vw"
    >
      <DragHandle />

      <ScanProgress
        mode={mode}
        phaseMs={snapshot.phaseMs}
        remainingMs={snapshot.remainingMs}
        startedAt={at}
      />

      {error && (
        <Box
          bg="$undoBg"
          borderRadius="8px"
          color="$undoText"
          flexShrink={0}
          px="8px"
          py="4px"
        >
          <Text typography="caption">{error.message}</Text>
        </Box>
      )}

      {mode === 'confirm' ? (
        <UndoPanel />
      ) : (
        <Box
          display="grid"
          flex={1}
          gap="8px"
          gridTemplateColumns="1fr 1fr"
          minH="0"
        >
          {SCAN_ACTIONS.map((action, index) => (
            <SwitchButton
              key={action.id}
              cursor={cursorStateAt(index)}
              label={action.label}
              name={action.name}
            />
          ))}
        </Box>
      )}

      <StatusLine
        intervalMs={snapshot.intervalMs}
        mode={mode}
        notice={notice}
      />
    </VStack>
  )
}
