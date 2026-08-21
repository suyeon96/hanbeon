'use client'

import { Center, Text, VStack } from '@devup-ui/react'

/** 이 칸이 지금 어떤 상태인지. */
export type CursorState = 'idle' | 'scanning' | 'dwelling'

interface SwitchButtonProps {
  cursor: CursorState
  label: string
  name: string
}

/**
 * 스캔 커서가 머무는 칸.
 *
 * 활성 여부는 색만이 아니라 테두리 두께로도 구분한다. 색약 사용자와 고대비
 * 모드에서 색상 대비만으로는 위치를 놓칠 수 있기 때문이다.
 *
 * listbox/option 같은 role은 붙이지 않는다. floating 창은 포커스를 받지 않으므로
 * 화면 낭독기의 탐색 대상이 아니고, 잘못된 semantic을 노출하면 오히려 방해가 된다.
 */
export function SwitchButton({ cursor, label, name }: SwitchButtonProps) {
  const active = cursor !== 'idle'

  return (
    <VStack
      aria-label={name}
      bg={active ? '$scanCursorBg' : '$scanIdleBg'}
      borderColor={active ? '$scanCursor' : '$border'}
      borderRadius="14px"
      borderStyle={cursor === 'dwelling' ? 'dashed' : 'solid'}
      borderWidth={active ? '5px' : '2px'}
      color={active ? '$scanCursorText' : '$scanIdleText'}
      gap="0px"
      justifyContent="center"
      minH="60px"
    >
      <Center>
        <Text typography="switchLabel">{label}</Text>
      </Center>
      {/* 머무는 중에는 '한 번 더 누르면 같은 동작'임을 알려야 한다.
          이 상태를 모르면 사용자는 순환을 기다리며 아무것도 하지 않는다. */}
      {cursor === 'dwelling' && (
        <Center>
          <Text typography="caption">한 번 더</Text>
        </Center>
      )}
    </VStack>
  )
}
