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

    /** 방금 화면을 굴렸는가. 굴린 뒤 한 번은 시스템에 자리를 되묻지 않는다. */
    private var resumeFromEdge = false

    /** 굴림이 끝난 뒤 좌표를 다시 읽으려고 쓴다. */
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

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
        val focused = lastFocused ?: focusedNode()
        if (focused == null) {
            Log.w(TAG, "선택 실패: 고른 요소가 없음")
            return false
        }

        var node: AccessibilityNodeInfo? = focused
        var depth = 0
        while (node != null) {
            // 우리 자신은 누르지 않는다. 컨트롤러의 칸을 눌러 버리면 사용자가
            // 고르려던 것 대신 우리 UI가 반응하고, 되돌릴 방법이 없다.
            if (isOurs(node)) {
                Log.w(TAG, "선택 실패: 우리 앱의 요소라 누르지 않음")
                return false
            }
            if (node.isClickable) {
                val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "선택 ${if (ok) "됨" else "거부"} ${describe(node)} (조상 $depth 단계 위)")
                if (ok) return true
                // 클릭이 거부되면 더 위에서 받아 줄 수도 있다. 웹뷰에서는
                // isClickable 이 참인데도 실제로 받지 않는 노드가 흔하다.
            }
            node = node.parent
            depth += 1
        }

        Log.w(TAG, "선택 실패: 누를 수 있는 조상이 없음 ${describe(focused)}")
        return false
    }

    private fun describe(node: AccessibilityNodeInfo): String {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        return "${node.className} \"${node.text ?: node.contentDescription ?: ""}\" " +
            "y=${bounds.top} clickable=${node.isClickable}"
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
        if (at < 0 && !resumeFromEdge) {
            val system = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            at = order.indexOfFirst { it == system }
        }
        // 굴린 직후에는 시스템에 되묻지 않는다. 크롬이 아직 굴리기 전에 고른 것을
        // 쥐고 있어서, 물으면 방금 놓은 자리로 그대로 돌아와 또 굴리기만 한다.
        resumeFromEdge = false
        val forward = direction == View.FOCUS_FORWARD

        // 포커스를 거부하는 노드가 있다. 거기서 멈추면 `lastFocused`가 그대로라
        // 다음 눌림에도 같은 노드를 또 시도하게 되고, 커서가 영영 움직이지 않는다.
        // 실기에서 크롬의 빈 FrameLayout 하나가 계속 거부해 `>`가 아무 일도 하지
        // 않았고, 고른 것이 없으니 `Enter`도 먹힐 수 없었다.
        //
        // 그래서 받아 주는 것을 만날 때까지 같은 방향으로 계속 나아간다. 방향을
        // 지키는 것이 중요하다 — `<`가 `>`의 정확한 역이어야 지나쳐도 되돌아올 수
        // 있다.
        //
        // 되감기 전에 먼저 끝까지 가 본다. 목록 끝에 닿았다는 것을 자리 번호로
        // 판정하면 안 된다. 마지막 몇 개가 포커스를 거부하면 커서가 마지막 자리에
        // 아예 도달하지 못해, 끝에 닿았다는 판정이 영영 참이 되지 않는다.
        val tail = if (forward) (at + 1) until order.size else (at - 1) downTo 0
        for (i in tail) {
            if (focusOn(order[i], at, i, order.size)) return true
        }

        // 여기까지 왔다는 것은 이 방향으로 더 갈 데가 없다는 뜻이다. 순서에는
        // **지금 보이는 것만** 들어 있어서, 굴리지 않으면 화면 밖 내용에 영영
        // 닿을 수 없다. 되감기는 굴릴 데가 없을 때만 한다.
        if (at >= 0 && scrollPage(root, forward)) {
            Log.i(TAG, "화면을 굴림 (${if (forward) "아래" else "위"}) at=$at/${order.size}")
            // 자리를 놓는다. 굴려도 크롬은 지금 고른 것을 화면 안에 붙들어 두므로,
            // 자리를 쥐고 있으면 다음 눌림에도 여전히 끝이라 또 굴리기만 하고
            // 영영 나아가지 못한다.
            //
            // 놓으면 다음 눌림은 새 화면의 처음부터(`>`) 또는 끝부터(`<`) 이어간다.
            // 방향에 맞는 쪽에서 시작하므로 `<`는 여전히 `>`의 역이다.
            lastFocused = null
            resumeFromEdge = true
            // 굴린 직후에는 노드 트리가 아직 안 바뀌어 있다. 여기서 기다리면
            // 스위치 처리가 그만큼 늦어져 눌림 판정이 흔들린다. 다음 눌림에서
            // 새 목록을 만나 이어서 옮긴다.
            return true
        }

        val head = if (forward) 0..at else (order.size - 1) downTo (at + 1)
        for (i in head) {
            if (focusOn(order[i], at, i, order.size)) return true
        }

        Log.w(TAG, "이동 실패: ${order.size}개가 모두 포커스를 거부함")
        return false
    }

    /** 이 노드로 커서를 옮긴다. 거부하면 거짓. */
    private fun focusOn(
        target: AccessibilityNodeInfo,
        from: Int,
        to: Int,
        total: Int,
    ): Boolean {
        if (!target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)) return false

        lastFocused = target

        // 화면 가장자리에 걸친 요소만 안으로 끌어들인다. 반쯤 잘려 보이는 것을
        // 고르라고 하면 사용자는 무엇을 고르는지 확인할 수 없다.
        //
        // **이미 다 보이는 것에는 걸지 않는다.** 웹페이지 전체를 담는 웹뷰가
        // 순서에 끼는데, 거기에 걸면 문서 첫머리가 끌어올려져 애써 굴린 화면이
        // 통째로 맨 위로 되돌아간다. 실기에서 이것 때문에 화면 아래쪽으로
        // 나아가지 못하고 같은 자리를 맴돌았다.
        val bounds = android.graphics.Rect()
        target.getBoundsInScreen(bounds)
        val screen =
            android.graphics.Rect(
                0,
                0,
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels,
            )
        if (!screen.contains(bounds)) {
            target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        }

        publishBounds(target)
        Log.i(TAG, "이동 됨 $from->$to/$total ${describe(target)}")
        return true
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

    /**
     * 굴릴 수 있는 조상을 찾아 한 화면 굴린다.
     *
     * 지금 고른 것에서 위로 올라가며 찾는다. 화면에 굴릴 수 있는 것이 여럿일 때
     * (목록 안의 목록) 사용자가 보고 있는 쪽을 굴려야 하기 때문이다.
     */
    private fun scrollPage(
        root: AccessibilityNodeInfo,
        forward: Boolean,
    ): Boolean {
        val action =
            if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }

        var node: AccessibilityNodeInfo? = lastFocused
        while (node != null) {
            if (node.isScrollable && node.performAction(action)) return true
            node = node.parent
        }

        // 조상 사슬로 못 찾는 경우가 있다. 크롬의 웹 콘텐츠는 굴릴 수 있는 것이
        // 웹뷰인데, 가상 노드의 parent 를 따라가도 거기까지 닿지 않는다.
        // 트리에서 직접 찾는다.
        return scrollables(root).any { it.performAction(action) }
    }

    /** 굴릴 수 있는 것을 트리에서 모은다. 큰 것부터 — 바깥쪽이 화면을 굴린다. */
    private fun scrollables(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || isOurs(node)) return
            if (node.isScrollable) out.add(node)
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }

        walk(root)
        return out
    }

    /**
     * 강조 테두리를 그릴 곳을 알린다.
     *
     * 두 번 보낸다. `ACTION_SHOW_ON_SCREEN`이 화면을 굴리면 좌표가 바뀌는데,
     * 굴림이 끝나기 전에 읽은 값으로 그리면 테두리가 엉뚱한 곳에 남는다.
     * 잠시 뒤 노드를 다시 읽어 한 번 더 보낸다.
     */
    private fun publishBounds(node: AccessibilityNodeInfo) {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        onFocusMoved?.invoke(bounds)

        main.postDelayed({
            if (node.refresh()) {
                val moved = android.graphics.Rect()
                node.getBoundsInScreen(moved)
                if (moved != bounds && !moved.isEmpty) onFocusMoved?.invoke(moved)
            }
        }, SCROLL_SETTLE_MS)
    }

    private fun focusables(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            // 우리 컨트롤러는 순서에 넣지 않는다. 스캔 대상은 대상 앱이지 우리가 아니다.
            if (isOurs(node)) return

            // 굴릴 수 있는 것은 고를 대상이 아니라 **내용을 담는 창**이다. 크롬의
            // 웹뷰가 여기 걸리는데, 거기에 접근성 포커스를 주는 것만으로 크롬이
            // 문서 첫머리로 굴러가 애써 내려온 화면이 통째로 맨 위로 돌아간다.
            // 실기에서 이것 때문에 화면 아래쪽으로 나아가지 못하고 맴돌았다.
            val usable =
                !node.isScrollable &&
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

        /** 굴림이 멎기를 기다리는 시간. 좌표를 다시 읽는 데만 쓴다. */
        private const val SCROLL_SETTLE_MS = 350L

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
