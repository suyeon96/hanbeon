package kr.devfive.hanbeon

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 보호자가 쓰는 화면.
 *
 * 스위치만 쓰는 사용자는 이 화면을 조작할 수 없다. 권한 대화상자는 4칸으로 빠져나올
 * 수 없기 때문이다(PRD 5.4의 '보호자가 띄워 준다'와 같은 전제). 그래서 큼직하고
 * 단계가 짧아야 한다.
 */
class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(48), dp(24), dp(24))
                setBackgroundColor(Color.WHITE)
            }

        root.addView(
            TextView(this).apply {
                text = "한번"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
                setTextColor(Color.parseColor("#1B2124"))
            },
        )
        root.addView(
            TextView(this).apply {
                text = "스위치로 다른 앱을 조작하려면 아래 권한이 필요합니다."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(Color.parseColor("#5A6468"))
                setPadding(0, dp(8), 0, dp(28))
            },
        )

        status =
            TextView(this).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, dp(20), 0, 0)
            }

        root.addView(
            big("다른 앱 위에 표시 권한 열기") {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ),
                )
            },
        )
        root.addView(big("컨트롤러 켜기") { OverlayService.start(this) })
        root.addView(big("컨트롤러 끄기") { OverlayService.stop(this) })
        root.addView(status)

        setContentView(root)

        // 인텐트로도 켤 수 있게 둔다. 서비스 자체는 exported=false로 잠가 두어야
        // 아무 앱이나 우리 컨트롤러를 띄울 수 없는데, 그러면 adb로 검증할 길이
        // 없어진다. 이 통로는 우리 Activity를 거치므로 잠금이 풀리지 않는다.
        if (intent?.getBooleanExtra(EXTRA_START_OVERLAY, false) == true) {
            OverlayService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        val allowed = Settings.canDrawOverlays(this)
        status.text =
            if (allowed) {
                "권한 있음. 컨트롤러를 켤 수 있습니다."
            } else {
                "권한 없음. 먼저 위 버튼으로 허용해 주세요."
            }
        status.setTextColor(
            if (allowed) Color.parseColor("#0E7A63") else Color.parseColor("#B3261E"),
        )
    }

    private fun big(
        label: String,
        onTap: () -> Unit,
    ): Button =
        Button(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setOnClickListener { onTap() }
            layoutParams =
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).also {
                    it.bottomMargin = dp(12)
                }
        }

    companion object {
        const val EXTRA_START_OVERLAY = "start_overlay"
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
}
