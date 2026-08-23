package kr.devfive.hanbeon

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import kotlin.concurrent.thread

/**
 * 아두이노 스위치를 USB 시리얼로 읽는다.
 *
 * 스위치는 HID 키보드가 아니다. 아두이노 우노(ATmega328P)는 USB HID가 물리적으로
 * 불가능해서 펌웨어가 눌림에 `P` 뗌에 `R`을 보낸다(PRD 7절). 그래서 접근성 서비스의
 * `onKeyEvent()`로는 영영 오지 않고, USB Host API로 직접 읽어야 한다.
 *
 * 데스크톱의 `switch.rs`와 같은 프로토콜을 쓴다. 프로토콜을 바꾸면 양쪽을 함께 고친다.
 *
 * CDC-ACM을 직접 다룬다. 라이브러리를 들이지 않는 이유는 우리 보드가 정품 우노라
 * 표준 CDC-ACM이기 때문이다. 클론(CH340)을 받게 되면 그때 라이브러리를 검토한다.
 */
class UsbSwitch(
    private val context: Context,
    private val onEvent: (Event) -> Unit,
) {
    sealed interface Event {
        data class Connected(val name: String) : Event

        data object Disconnected : Event

        /** 눌림. */
        data object Press : Event

        /** 뗌. */
        data object Release : Event
    }

    private var connection: UsbDeviceConnection? = null
    private var reader: Thread? = null
    @Volatile private var running = false

    private val permissionReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action != ACTION_PERMISSION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) {
                    open()
                } else {
                    // 스위치만 쓰는 사용자는 이 대화상자를 조작할 수 없다.
                    // 보호자가 눌러 줘야 한다(PRD 5.4와 같은 전제).
                    Log.w(TAG, "USB 권한이 거부됐습니다. 보호자가 허용해 줘야 합니다.")
                }
            }
        }

    fun start() {
        context.registerReceiver(
            permissionReceiver,
            IntentFilter(ACTION_PERMISSION),
            Context.RECEIVER_NOT_EXPORTED,
        )
        open()
    }

    fun stop() {
        running = false
        reader?.interrupt()
        reader = null
        connection?.close()
        connection = null
        runCatching { context.unregisterReceiver(permissionReceiver) }
    }

    private fun manager() = context.getSystemService(UsbManager::class.java)

    private fun findBoard(): UsbDevice? =
        manager().deviceList.values.firstOrNull { device ->
            device.vendorId == ARDUINO_VENDOR || hasCdc(device)
        }

    private fun hasCdc(device: UsbDevice): Boolean =
        (0 until device.interfaceCount).any { i ->
            device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_CDC_DATA
        }

    private fun open() {
        val device = findBoard()
        if (device == null) {
            Log.w(TAG, "스위치를 찾지 못했습니다. 꽂혀 있는지 확인해 주세요.")
            return
        }

        if (!manager().hasPermission(device)) {
            val intent =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(ACTION_PERMISSION).setPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            manager().requestPermission(device, intent)
            return
        }

        Log.i(
            TAG,
            "보드 인터페이스 ${device.interfaceCount}개: " +
                (0 until device.interfaceCount).joinToString {
                    "#${device.getInterface(it).id}/cls=${device.getInterface(it).interfaceClass}"
                },
        )

        val data = dataInterface(device)
        if (data == null) {
            Log.w(TAG, "CDC 데이터 인터페이스를 찾지 못했습니다.")
            return
        }

        val opened = manager().openDevice(device) ?: return
        if (!opened.claimInterface(data, true)) {
            Log.w(TAG, "인터페이스를 잡지 못했습니다.")
            opened.close()
            return
        }

        configure(opened, device)

        val input = endpoint(data, UsbConstants.USB_DIR_IN)
        val output = endpoint(data, UsbConstants.USB_DIR_OUT)
        if (input == null) {
            Log.w(TAG, "읽기 엔드포인트가 없습니다.")
            opened.close()
            return
        }

        connection = opened
        onEvent(Event.Connected(device.deviceName))
        Log.i(TAG, "스위치 연결됨 ${device.deviceName} (VID ${device.vendorId})")

        // 우리 보드가 맞는지 물어본다. 데스크톱과 같은 핸드셰이크다.
        output?.let { opened.bulkTransfer(it, HELLO, HELLO.size, 500) }

        running = true
        reader = thread(name = "hanbeon-usb") { readLoop(opened, input) }
    }

    /**
     * 보드레이트와 DTR을 맞춘다.
     *
     * 이걸 빠뜨리면 포트는 열리는데 한 바이트도 오지 않는다. 아두이노는 DTR이
     * 올라가야 말을 시작한다.
     */
    private fun configure(
        connection: UsbDeviceConnection,
        device: UsbDevice,
    ) {
        val control = device.getInterface(0)
        connection.claimInterface(control, true)

        // SET_LINE_CODING: 115200 8N1
        val coding =
            byteArrayOf(
                0x00, 0xC2.toByte(), 0x01, 0x00, // 115200 리틀엔디언
                0x00, // 정지 비트 1
                0x00, // 패리티 없음
                0x08, // 데이터 8비트
            )
        val codingResult = connection.controlTransfer(0x21, 0x20, 0, 0, coding, coding.size, 500)

        // SET_CONTROL_LINE_STATE: DTR|RTS
        val lineResult = connection.controlTransfer(0x21, 0x22, 0x03, 0, null, 0, 500)
        Log.i(TAG, "보드레이트 설정=$codingResult DTR설정=$lineResult (음수면 실패)")
    }

    private fun dataInterface(device: UsbDevice): UsbInterface? =
        (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA }

    private fun endpoint(
        target: UsbInterface,
        direction: Int,
    ): UsbEndpoint? =
        (0 until target.endpointCount)
            .map { target.getEndpoint(it) }
            .firstOrNull {
                it.direction == direction && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
            }

    private fun readLoop(
        connection: UsbDeviceConnection,
        input: UsbEndpoint,
    ) {
        val buffer = ByteArray(256)
        // 시리얼은 줄 단위로 오지 않는다. 조각을 모아 완성된 줄만 꺼낸다.
        val pending = StringBuilder()
        var idle = 0
        Log.i(TAG, "읽기 시작 (엔드포인트 ${input.address}, 최대 ${input.maxPacketSize}바이트)")

        while (running) {
            val read = connection.bulkTransfer(input, buffer, buffer.size, 200)
            if (read <= 0) {
                // 아무것도 안 올 때 조용히 도는 것과 통로가 막힌 것을 구분해야 한다.
                if (++idle % 50 == 0) Log.d(TAG, "읽기 대기 중 (마지막 결과 $read)")
                continue
            }
            idle = 0
            Log.d(TAG, "받음 ${read}바이트: ${String(buffer, 0, read, Charsets.US_ASCII).trim()}")

            pending.append(String(buffer, 0, read, Charsets.US_ASCII))
            while (true) {
                val at = pending.indexOf("\n")
                if (at < 0) break
                val line = pending.substring(0, at).trim()
                pending.delete(0, at + 1)
                dispatch(line)
            }
        }

        onEvent(Event.Disconnected)
    }

    private fun dispatch(line: String) {
        when (line) {
            "P" -> onEvent(Event.Press)
            "R" -> onEvent(Event.Release)
            IDENT -> Log.i(TAG, "우리 보드가 맞습니다")
            // 아두이노는 리셋 직후 잡음을 뱉는다. 모르는 줄에 반응해 눌린 것으로
            // 치면 사용자는 누르지도 않은 동작을 겪는다.
            else -> if (line.isNotEmpty()) Log.d(TAG, "모르는 줄: $line")
        }
    }

    private companion object {
        const val TAG = "한번"
        const val ACTION_PERMISSION = "kr.devfive.hanbeon.USB_PERMISSION"
        const val ARDUINO_VENDOR = 0x2341
        const val IDENT = "HANBEON_UNO_V1"
        val HELLO = "HELLO\n".toByteArray(Charsets.US_ASCII)
    }
}
