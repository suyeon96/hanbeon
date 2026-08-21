'use client'

import { Box, Text, VStack } from '@devup-ui/react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { useEffect, useState } from 'react'

import { type CursorState, SwitchButton } from '@/components/SwitchButton'
import { UndoPanel } from '@/components/UndoPanel'
import {
  DEFAULT_SCAN_INTERVAL_MS,
  SCAN_ACTIONS,
  type ScanError,
  type ScanSnapshot,
} from '@/lib/actions'

/**
 * floating 컨트롤러.
 *
 * 커서 순환과 선택 판정의 주인은 Rust 코어(`scan` 모듈)다.
 * 이 화면은 코어가 보낸 상태를 그리기만 한다.
 */
export default function FloatingPage() {
  const [snapshot, setSnapshot] = useState<ScanSnapshot>({
    cursor: 0,
    action: 'next',
    mode: 'scanning',
    intervalMs: DEFAULT_SCAN_INTERVAL_MS,
  })
  const [error, setError] = useState<ScanError | null>(null)

  useEffect(() => {
    // 첫 틱이 오기 전까지 화면이 비지 않도록 현재 상태를 한 번 맞춘다.
    invoke<ScanSnapshot>('scan_snapshot')
      .then(setSnapshot)
      .catch(() => {
        // 브라우저에서 화면만 확인할 때는 Tauri 컨텍스트가 없다.
      })

    const unlistenState = listen<ScanSnapshot>('scan://state', (event) =>
      setSnapshot(event.payload),
    )
    const unlistenError = listen<ScanError>('scan://error', (event) =>
      setError(event.payload),
    )

    return () => {
      unlistenState.then((stop) => stop()).catch(() => {})
      unlistenError.then((stop) => stop()).catch(() => {})
    }
  }, [])

  const { cursor, mode } = snapshot

  const cursorStateAt = (index: number): CursorState => {
    if (mode === 'paused' || index !== cursor) return 'idle'
    return mode === 'dwelling' ? 'dwelling' : 'scanning'
  }

  return (
    <VStack
      aria-label="한번 스위치 컨트롤러"
      bg="$containerBackground"
      borderColor={mode === 'paused' ? '$warning' : '$borderBold'}
      borderRadius="18px"
      borderStyle="solid"
      borderWidth="2px"
      boxSizing="border-box"
      gap="6px"
      h="100vh"
      p="10px"
      w="100vw"
    >
      {error && (
        <Box
          bg="$undoBg"
          borderRadius="8px"
          color="$undoText"
          px="8px"
          py="4px"
        >
          <Text typography="caption">{error.message}</Text>
        </Box>
      )}

      {mode === 'paused' && (
        <Text color="$warning" textAlign="center" typography="caption">
          일시정지 — 길게 눌러 다시 시작
        </Text>
      )}

      {mode === 'confirm' ? (
        <UndoPanel />
      ) : (
        <Box display="grid" flex={1} gap="8px" gridTemplateColumns="1fr 1fr">
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
    </VStack>
  )
}
