package kr.devfive.hanbeon

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * 대상 앱을 실제로 조작하는 쪽.
 *
 * 데스크톱은 `Tab`·`Enter`를 OS에 주입하지만 안드로이드는 다른 앱으로의 키 주입을
 * 막는다. 허용된 길은 접근성 서비스뿐이고, 여기서는 키를 보내는 대신 **접근성
 * 포커스를 노드 트리에서 옮긴다**(PRD 5.5).
 *
 * 스위치 입력은 여기로 오지 않는다. 아두이노가 HID 키보드가 아니라 시리얼로
 * `P`/`R`을 보내기 때문이다(PRD 7절). 이 서비스는 **출력 전용**이다.
 */
class HanbeonAccessibilityService : AccessibilityService() {
    /**
     * 마지막으로 우리가 옮긴 자리.
     *
     * 스캔 위치는 우리가 쥔다. 데스크톱에서 상태기계가 커서를 쥐는 것과 같은
     * 이유다 — 위치를 남에게 물으면 답이 흔들려 다음에 무엇이 올지 예측할 수 없다.
     */
    private var lastFocused: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "접근성 서비스 연결됨")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * 지금 앞에 있는 앱이 바뀌면 알린다.
     *
     * 데스크톱이 300ms마다 물어보던 것을 여기서는 시스템이 밀어 준다.
     * 앱별 칸(PRD F11)은 이 신호로만 갈아 끼운다.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val app = event.packageName?.toString() ?: return
            if (app != foreground) {
                foreground = app
                // 앱이 바뀌면 자리를 버린다. 이전 앱의 노드를 들고 있으면 새 화면에서
                // 엉뚱한 곳을 가리킨다.
                lastFocused = null
                Log.i(TAG, "앞에 있는 앱: $app")
            }
        }
    }

    override fun onInterrupt() = Unit

    /** 다음 요소로. 데스크톱의 `Tab`에 해당한다. */
    fun moveNext(): Boolean = move(View.FOCUS_FORWARD)

    /** 이전 요소로. 데스크톱의 `Shift+Tab`에 해당한다. */
    fun movePrevious(): Boolean = move(View.FOCUS_BACKWARD)

    /**
     * 지금 포커스를 가진 것을 고른다. 데스크톱의 `Enter`에 해당한다.
     *
     * 누를 수 없는 노드에 포커스가 있으면 누를 수 있는 조상을 찾아 올라간다.
     * 목록의 한 줄에서 실제로 클릭을 받는 것은 바깥 컨테이너인 경우가 흔하다.
     */
    fun select(): Boolean {
        val focused = lastFocused ?: focusedNode() ?: return false
        var node: AccessibilityNodeInfo? = focused
        while (node != null) {
            // 우리 자신은 누르지 않는다. 컨트롤러의 칸을 눌러 버리면 사용자가
            // 고르려던 것 대신 우리 UI가 반응하고, 되돌릴 방법이 없다.
            if (isOurs(node)) return false
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            node = node.parent
        }
        return false
    }

    /** 되돌리기. 데스크톱의 뒤로가기 단축키에 해당한다(PRD F6). */
    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    /**
     * 포커스를 한 칸 옮긴다.
     *
     * `focusSearch`를 쓰지 않는다. 그것은 **입력 포커스**를 위한 탐색이라, 돌려준
     * 노드가 접근성 포커스를 거부하는 일이 잦다. 실제로 크롬에서 EditText를
     * 돌려주고는 `ACTION_ACCESSIBILITY_FOCUS`가 거부됐다.
     *
     * 대신 트리를 훑어 순서를 직접 만든다. 순서를 우리가 쥐고 있어야 `<`로
     * 되돌아가는 것이 `>`의 정확한 역이 된다. 그게 이 제품의 핵심 약속이다.
     */
    private fun move(direction: Int): Boolean {
        val root = targetRoot()
        if (root == null) {
            Log.w(TAG, "이동 실패: 조작할 앱의 창을 못 찾음")
            return false
        }

        val order = focusables(root)
        if (order.isEmpty()) {
            Log.w(TAG, "이동 실패: 초점 가능한 요소가 없음 (root=${root.packageName})")
            return false
        }

        // 시스템에 물으면 안 된다. 웹뷰 안에 포커스가 있으면 컨테이너인 웹뷰를
        // 돌려줘서, 매번 같은 자리로 읽히고 커서가 제자리를 맴돈다. 우리가 마지막에
        // 옮긴 곳을 기억해 두고 그것을 새 목록에서 찾는다.
        val remembered = lastFocused
        var at = if (remembered == null) -1 else order.indexOfFirst { it == remembered }
        if (at < 0) {
            val system = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            at = order.indexOfFirst { it == system }
        }
        val next =
            when {
                // 아직 아무 데도 없으면 첫 요소부터. 이게 없으면 사용자가 처음
                // 누를 때 아무 일도 일어나지 않고 왜인지 알 방법이 없다.
                at < 0 -> 0
                direction == View.FOCUS_FORWARD -> (at + 1) % order.size
                else -> (at - 1 + order.size) % order.size
            }

        val target = order[next]
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        if (ok) {
            lastFocused = target
            // 안드로이드는 접근성 포커스를 눈에 보이게 그려 주지 않는다.
            // 어디가 골라져 있는지 사용자가 봐야 하므로 우리가 그린다(PRD F7).
            val bounds = android.graphics.Rect()
            target.getBoundsInScreen(bounds)
            onFocusMoved?.invoke(bounds)
        }
        Log.i(
            TAG,
            "이동 ${if (ok) "됨" else "거부"} ${at}->${next}/${order.size} " +
                "${target.className} \"${target.text ?: target.contentDescription ?: ""}\"",
        )
        return ok
    }

    /**
     * 훑어서 순서를 만든다.
     *
     * 화면에 보이고, 켜져 있고, 누르거나 초점을 받을 수 있는 것만 넣는다. 보이지
     * 않는 것을 순서에 넣으면 사용자는 아무 일도 일어나지 않는 칸을 만나고,
     * 그 순간 자리로 동작을 기억하는 전제가 깨진다.
     */
    /**
     * 조작할 창의 노드 트리.
     *
     * `rootInActiveWindow`를 그대로 쓰면 안 된다. 설정 화면을 열거나 컨트롤러를
     * 만지면 활성 창이 **우리 자신**이 되고, 그러면 우리 앱을 스캔하려다 초점
     * 가능한 요소가 하나도 없어 이동이 통째로 실패한다. 실기에서 이 상태가
     * 이어지자 놓침이 쌓여 주사 간격이 1.8초에서 3.5초까지 늘어났다 —
     * 사용자는 실수한 적이 없는데 앱이 느려진다.
     *
     * 데스크톱의 가림 판정에도 '활성 앱이 우리 자신이면 건너뛴다'는 같은 규칙이
     * 있다. 우리가 앞에 있으면 뒤에 있는 대상 앱 창 중 가장 위의 것을 고른다.
     */
    private fun targetRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null && !isOurs(active)) return active

        return windows
            .asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            // 겹쳐 있으면 위에 있는 것이 사용자가 보고 있는 창이다.
            .sortedByDescending { it.layer }
            .mapNotNull { it.root }
            .firstOrNull { !isOurs(it) }
    }

    /**
     * 우리 앱의 노드인가.
     *
     * `packageName`은 `String`이 아니라 `CharSequence`다. `SpannedString` 같은
     * 다른 구현체가 오면 `==` 비교가 조용히 거짓이 되어 걸러지지 않는다.
     */
    private fun isOurs(node: AccessibilityNodeInfo): Boolean =
        node.packageName?.toString() == packageName

    private fun focusables(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            // 우리 컨트롤러는 순서에 넣지 않는다. 스캔 대상은 대상 앱이지 우리가 아니다.
            if (isOurs(node)) return

            val usable =
                node.isVisibleToUser &&
                    node.isEnabled &&
                    (node.isClickable || node.isFocusable || node.isEditable)
            if (usable) out.add(node)

            for (i in 0 until node.childCount) walk(node.getChild(i))
        }

        walk(root)
        return out
    }

    private fun focusedNode(): AccessibilityNodeInfo? {
        val root = targetRoot() ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }

    companion object {
        private const val TAG = "한번"

        /** 켜져 있으면 그 인스턴스. 꺼져 있으면 `null`. */
        @Volatile
        var instance: HanbeonAccessibilityService? = null
            private set

        /** 지금 앞에 있는 앱의 패키지 이름. 앱별 칸을 고르는 근거다(PRD F11). */
        @Volatile
        var foreground: String? = null
            private set

        /** 포커스가 옮겨 갈 때마다 그 화면 좌표를 받는다. 테두리를 그리는 쪽이 쓴다. */
        @Volatile
        var onFocusMoved: ((android.graphics.Rect) -> Unit)? = null
    }
}
