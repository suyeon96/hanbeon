import { globalCss, ThemeScript } from '@devup-ui/react'
import { resetCss } from '@devup-ui/reset-css'
import type { Metadata } from 'next'

resetCss()
globalCss({
  '*': {
    fontFamily:
      'Pretendard, -apple-system, BlinkMacSystemFont, "Segoe UI", "Malgun Gothic", sans-serif',
  },
  // floating 창은 투명 배경 위에 컨트롤러만 떠 있어야 한다.
  // 배경이 필요한 화면(설정)은 각자 컨테이너에서 칠한다.
  'html, body': {
    bg: 'transparent',
    overflow: 'hidden',
    userSelect: 'none',
  },
})

// 모션은 전역 차단이 아니라 '애초에 넣지 않는' 방식으로 없앤다.
// devup-ui의 globalCss는 at-rule 안의 중첩 셀렉터를 지원하지 않아
// `@media (prefers-reduced-motion)` 블록이 깨진다.
// 커서 이동에 트랜지션·애니메이션을 넣지 않는 규칙은 CLAUDE.md에 있다.

export const metadata: Metadata = {
  title: '한번',
  description: '상황적응형 싱글스위치 접근성 도구',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <head>
        <ThemeScript />
      </head>
      <body>{children}</body>
    </html>
  )
}
