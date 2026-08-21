import { describe, expect, it } from 'bun:test'

import { StatusLine } from '@/components/StatusLine'

describe('StatusLine', () => {
  it('알릴 것이 없으면 현재 속도를 보여준다', () => {
    expect(
      <StatusLine intervalMs={2500} mode="scanning" notice={null} />,
    ).toMatchSnapshot()
  })

  it('간격이 바뀌면 그 이유를 같은 자리에 띄운다', () => {
    expect(
      <StatusLine
        intervalMs={1700}
        mode="scanning"
        notice="최근 반응이 빨라져 1.8초 → 1.7초"
      />,
    ).toMatchSnapshot()
  })

  // 멈춰 있다는 사실이 무엇보다 먼저다. 조정 문구에 가려 정지 상태를
  // 놓치면 사용자는 스위치가 고장 난 줄 안다.
  it('정지 중에는 조정 이유보다 정지 안내가 앞선다', () => {
    expect(
      <StatusLine
        intervalMs={1700}
        mode="paused"
        notice="실수가 감지되어 1.8초 → 2.2초"
      />,
    ).toMatchSnapshot()
  })
})
