package kr.devfive.hanbeon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

/**
 * 4칸 컨트롤러를 다른 앱 위에 올린다.
 *
 * 이 제품은 '다른 앱을 조작하는 것'이라 우리 화면이 앞에 나오면 안 된다. 그래서
 * Activity가 아니라 포그라운드 Service가 `TYPE_APPLICATION_OVERLAY` 창을 직접 올린다.
 * Tauri의 안드로이드 지원은 Activity만 주기 때문에 여기는 순수 안드로이드로 짠다.
 *
 * 포그라운드 서비스로 두는 이유는 오래 살아야 하기 때문이다. 사용자는 스위치 말고
 * 다른 조작 수단이 없어서, 시스템이 우리를 조용히 죽이면 되살릴 방법이 없다.
 */
class OverlayService : Service() {
    private var windows: WindowManager? = null
    private var controller: ControllerView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        show()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "한번 실행 중",
                // 낮게 잡는다. 알림음이 울리면 스캔 소리와 섞여 사용자가 무엇이
                // 일어났는지 구분하지 못한다.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )

        val notice =
            Notification.Builder(this, CHANNEL)
                .setContentTitle("한번이 실행 중입니다")
                .setContentText("스위치로 화면을 조작할 수 있습니다")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build()

        startForeground(NOTIFICATION_ID, notice)
    }

    private fun show() {
        if (!Settings.canDrawOverlays(this)) {
            // 권한 없이 창을 올리면 예외로 죽는다. 조용히 물러나고 안내 화면이
            // 권한을 받게 둔다.
            stopSelf()
            return
        }

        val view = ControllerView(this)
        val manager = getSystemService(WindowManager::class.java)

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // NOT_FOCUSABLE이 이 앱의 생명줄이다. 포커스를 받으면 우리가 옮기려는
                // 대상 앱의 포커스를 뺏어, 정작 조작할 것이 사라진다. 데스크톱에서
                // NSPanel과 WS_EX_NOACTIVATE로 애써 만드는 상태가 여기서는 기본이다.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = MARGIN
        params.y = MARGIN

        manager.addView(view, params)
        windows = manager
        controller = view
    }

    override fun onDestroy() {
        controller?.let { view -> runCatching { windows?.removeView(view) } }
        controller = null
        windows = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "hanbeon.overlay"
        private const val NOTIFICATION_ID = 1
        private const val MARGIN = 24

        fun start(context: android.content.Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
