package kr.devfive.hanbeon

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/**
 * 4칸 컨트롤러.
 *
 * 무엇을 그릴지는 코어가 보낸 상태가 정한다. 여기서 커서를 옮기거나 순서를 만들지
 * 않는다. 배치는 데스크톱과 같다(PRD F1) — 이동은 왼쪽에 세로로, 선택은 오른쪽에
 * 그 높이만큼, 설정은 맨 아래. 자리가 다르면 사용자가 기기를 옮길 때마다 다시
 * 외워야 한다.
 */
class ControllerView(context: Context) : LinearLayout(context) {
    private val moves = LinearLayout(context)
    private val extras = LinearLayout(context)
    private val status = TextView(context)
    private val enter: TextView
    private val settings: TextView

    /** 칸 이름 → 그 칸을 그리는 뷰. 커서 위치를 표시할 때 쓴다. */
    private val cells = mutableMapOf<String, TextView>()

    init {
        orientation = VERTICAL
        setPadding(pad(12), pad(12), pad(12), pad(12))
        background =
            GradientDrawable().apply {
                cornerRadius = pad(20).toFloat()
                setColor(Color.parseColor("#F5F7F7"))
                setStroke(pad(1), Color.parseColor("#CFD8D8"))
            }

        moves.orientation = VERTICAL
        moves.layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        moves.addView(cell(">", height = 76))
        moves.addView(cell("<", height = 76, topGap = 10))

        enter = cell("Enter", height = 162)
        enter.layoutParams = LayoutParams(0, pad(162), 1f).also { it.marginStart = pad(10) }

        val top =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams =
                    LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            }
        top.addView(moves)
        top.addView(enter)
        addView(top)

        // 앱별 칸이 붙는 자리. 없을 때는 아무 자리도 차지하지 않는다.
        extras.orientation = VERTICAL
        extras.layoutParams =
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(extras)

        settings = cell("설정", height = 56, topGap = 10)
        addView(settings)

        // 알릴 것이 없어도 비우지 않는다. 나타났다 사라지는 줄은 그때마다 칸을
        // 밀어 올려 사용자가 커서 위치를 다시 찾게 만든다(PRD F5).
        status.gravity = Gravity.CENTER
        status.setTextColor(Color.parseColor("#5A6468"))
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        status.setPadding(0, pad(8), 0, 0)
        status.text = "준비 중"
        addView(status)
    }

    /** 코어가 보낸 상태를 그린다. */
    fun render(json: String) {
        val state = runCatching { JSONObject(json) }.getOrNull() ?: return
        val list = state.optJSONArray("cells") ?: return
        val cursor = state.optInt("cursor", -1)

        rebuildExtras(list)

        for (i in 0 until list.length()) {
            val cell = list.optJSONObject(i) ?: continue
            val view = cells[cell.optString("label")] ?: continue
            val here = i == cursor
            paint(view, here)
            // 커서가 있는 칸에만 이름을 붙인다. 모든 칸에 설명을 달면 글자가 늘어
            // 정작 커서 위치가 묻힌다(PRD F7).
            view.text =
                if (here) "${cell.optString("label")}\n${cell.optString("name")}"
                else cell.optString("label")
        }

        val interval = state.optLong("intervalMs", 0)
        if (interval > 0 && status.tag == null) {
            status.text = "%.1f초마다".format(interval / 1000.0)
        }
    }

    /** 간격이 바뀐 이유를 6초간 띄운다. 그 뒤에는 현재 속도로 돌아간다(PRD F5). */
    fun notice(json: String) {
        val reason = runCatching { JSONObject(json).optString("reason") }.getOrNull() ?: return
        if (reason.isEmpty()) return
        status.text = reason
        status.setTextColor(Color.parseColor("#0E7A63"))
        status.tag = true
        postDelayed({
            status.tag = null
            status.setTextColor(Color.parseColor("#5A6468"))
        }, NOTICE_MS)
    }

    /** 앱별 칸이 사라졌을 때 자리를 비운다. */
    fun fitCells(count: Int) {
        if (count == 0) {
            extras.removeAllViews()
            cells.keys.retainAll(setOf(">", "<", "Enter", "설정"))
        }
    }

    private fun rebuildExtras(list: JSONArray) {
        val wanted = mutableListOf<Pair<String, String>>()
        for (i in 0 until list.length()) {
            val cell = list.optJSONObject(i) ?: continue
            if (cell.optString("kind") == "extra") {
                wanted.add(cell.optString("label") to cell.optString("name"))
            }
        }
        if (wanted.size == extras.childCount) return

        extras.removeAllViews()
        wanted.forEach { (label, _) ->
            // 앱별 칸은 기본 칸과 다르게 그린다. 지금 앱에서만 쓸 수 있고 앱이
            // 바뀌면 사라지는 칸이라, 같아 보이면 사용자가 자리를 외운다(PRD F11).
            extras.addView(cell(label, height = 64, topGap = 10, accent = true))
        }
    }

    private fun paint(
        view: TextView,
        here: Boolean,
    ) {
        val accent = view.getTag(ACCENT_KEY) == true
        val fill = if (here) "#0E7A63" else if (accent) "#EFF6F4" else "#FFFFFF"
        val line = if (here) "#0E7A63" else if (accent) "#9CC7BC" else "#D8E0E0"
        view.setTextColor(if (here) Color.WHITE else Color.parseColor("#1B2124"))
        view.background =
            GradientDrawable().apply {
                cornerRadius = pad(if (accent) 20 else 14).toFloat()
                setColor(Color.parseColor(fill))
                // 색만으로 구분하지 않는다. 커서가 있으면 테두리도 굵어진다(원칙 6).
                setStroke(pad(if (here) 5 else 2), Color.parseColor(line))
            }
    }

    private fun cell(
        label: String,
        height: Int,
        topGap: Int = 0,
        accent: Boolean = false,
    ): TextView {
        val view =
            TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (accent) 18f else 26f)
                setTag(ACCENT_KEY, accent)
                layoutParams =
                    LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pad(height)).also {
                        it.topMargin = pad(topGap)
                    }
            }
        paint(view, false)
        cells[label] = view
        return view
    }

    private fun pad(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics,
        ).toInt()

    private companion object {
        /** 뷰에 '앱별 칸인가'를 달아 두는 키. */
        const val ACCENT_KEY = 0x7f000001

        /** 조정 이유를 띄워 두는 시간. 데스크톱과 같다. */
        const val NOTICE_MS = 6000L
    }
}
