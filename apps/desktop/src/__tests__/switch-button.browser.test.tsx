import { describe, expect, it } from 'bun:test'

import { SwitchButton } from '@/components/SwitchButton'
import { UndoPanel } from '@/components/UndoPanel'
import { SCAN_ACTIONS } from '@/lib/actions'

describe('SwitchButton', () => {
  it('순환·머무름·비활성을 구분해 렌더한다', () => {
    expect(
      <SwitchButton cursor="scanning" label=">" name="다음으로" />,
    ).toMatchSnapshot()
    expect(
      <SwitchButton cursor="dwelling" label=">" name="다음으로" />,
    ).toMatchSnapshot()
    expect(
      <SwitchButton cursor="idle" label="<" name="이전으로" />,
    ).toMatchSnapshot()
  })

  // 칸이 홀수일 때 마지막 하나가 한 줄을 통째로 쓴다. 빈칸을 남기면
  // 사용자는 거기에도 무언가 있다고 읽는다.
  it('마지막 홀수 칸은 한 줄을 통째로 쓴다', () => {
    expect(
      <SwitchButton cursor="idle" full label="이전 곡" name="이전 곡으로" />,
    ).toMatchSnapshot()
  })
})

describe('UndoPanel', () => {
  it('되돌리기 창을 렌더한다', () => {
    expect(<UndoPanel />).toMatchSnapshot()
  })
})

describe('SCAN_ACTIONS', () => {
  it('스캔 순서는 다음 - 이전 - 선택 - 설정 이다', () => {
    expect(SCAN_ACTIONS.map((action) => action.id)).toEqual([
      'next',
      'prev',
      'enter',
      'settings',
    ])
  })
})
