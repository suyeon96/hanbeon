package kr.devfive.hanbeon

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 4칸 컨트롤러.
 *
 * 아직 껍데기다. 스캔이 돌지 않고 커서도 움직이지 않는다. 오버레이가 다른 앱 위에
 * 실제로 뜨는지, 포커스를 뺏지 않는지를 먼저 확인하려고 만든 것이다.
 *
 * 배치는 데스크톱과 같다(PRD F1). 이동은 왼쪽에 세로로, 선택은 오른쪽에 그 높이만큼,
 * 설정은 맨 아래. 자리를 다르게 두면 사용자가 기기를 옮길 때마다 다시 외워야 한다.
 */
class ControllerView(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setPadding(pad(12), pad(12), pad(12), pad(12))
        background =
            GradientDrawable().apply {
                cornerRadius = pad(20).toFloat()
                setColor(Color.parseColor("#F5F7F7"))
                setStroke(pad(1), Color.parseColor("#CFD8D8"))
            }

        val moves =
            LinearLayout(context).apply {
                orientation = VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        moves.addView(cell(">", height = 76))
        moves.addView(cell("<", height = 76, topGap = 10))

        val top =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            }
        top.addView(moves)
        top.addView(
            cell("Enter", height = 162, startGap = 10).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, pad(162), 1f).also { it.marginStart = pad(10) }
            },
        )

        addView(top)
        // 설정은 가장 드물게 쓰므로 맨 아래에 낮게 둔다.
        addView(cell("설정", height = 56, topGap = 10))
    }

    private fun cell(
        label: String,
        height: Int,
        topGap: Int = 0,
        startGap: Int = 0,
    ): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#1B2124"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            background =
                GradientDrawable().apply {
                    cornerRadius = pad(14).toFloat()
                    setColor(Color.WHITE)
                    setStroke(pad(2), Color.parseColor("#D8E0E0"))
                }
            layoutParams =
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pad(height)).also {
                    it.topMargin = pad(topGap)
                    it.marginStart = pad(startGap)
                }
        }

    private fun pad(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics,
        ).toInt()
}
