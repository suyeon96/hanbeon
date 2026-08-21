'use client'

import { Center, Text } from '@devup-ui/react'

import type { ScanMode } from '@/lib/actions'
import { formatSeconds } from '@/lib/format'

interface StatusLineProps {
  /** 현재 주사 간격. 알릴 것이 없을 때 이 값을 보여준다. */
  intervalMs: number
  mode: ScanMode
  /** 적응 로직이 간격을 바꾼 이유. 잠시 떴다가 사라진다. */
  notice: string | null
}

/**
 * 컨트롤러 맨 아래 한 줄.
 *
 * 자동으로 바뀐 것은 반드시 보여야 한다(PRD F5, 원칙 2). 사용자는 스위치
 * 타이밍을 몸으로 익히는데, 속도가 소리 없이 바뀌면 갑자기 놓치기 시작하고
 * 왜 그런지 알 방법이 없다.
 *
 * 알릴 것이 없을 때도 줄을 비우지 않고 현재 속도를 보여준다. 나타났다
 * 사라지는 줄은 그때마다 아래 4칸을 밀어 올려, 커서 위치를 다시 찾게 만든다.
 */
export function StatusLine({ intervalMs, mode, notice }: StatusLineProps) {
  const paused = mode === 'paused'

  // 정지 > 최근 조정 > 현재 속도. 멈춰 있다는 사실이 무엇보다 먼저다.
  const message = paused
    ? '일시정지 — 길게 눌러 다시 시작'
    : (notice ?? `${formatSeconds(intervalMs)}마다`)

  return (
    <Center flexShrink={0} h="22px" overflow="hidden" w="100%">
      <Text
        color={paused ? '$warning' : notice ? '$primary' : '$caption'}
        // 평소 안내와 달라졌다는 것을 색만으로 알리지 않는다(원칙 6).
        fontWeight={paused || notice ? 700 : 400}
        overflow="hidden"
        textOverflow="ellipsis"
        typography="caption"
        whiteSpace="nowrap"
      >
        {message}
      </Text>
    </Center>
  )
}
