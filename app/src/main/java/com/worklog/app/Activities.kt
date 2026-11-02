package com.worklog.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clear = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(clear, clear),
            navigationBarStyle = SystemBarStyle.light(clear, clear),
        )
        if (savedInstanceState == null) Work.restore(this) else Repo.init(this)
        setContent { WorkTheme { App() } }
    }
}

/** 다른 앱을 쓰고 있어도 화면 가운데에 뜨는 출근/퇴근 팝업 */
class PopupActivity : ComponentActivity() {
    companion object {
        const val MODE_ARRIVE = "arrive"
        const val MODE_END = "end"
        const val MODE_TEST = "test"
        const val EX_MODE = "mode"
        const val EX_WP = "wp"

        fun intent(ctx: Context, mode: String, wpId: String): Intent =
            Intent(ctx, PopupActivity::class.java)
                .putExtra(EX_MODE, mode)
                .putExtra(EX_WP, wpId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** '다른 앱 위에 표시' 권한이 있으면 백그라운드에서도 바로 띄울 수 있다 */
        fun show(ctx: Context, mode: String, wpId: String) {
            if (!Settings.canDrawOverlays(ctx)) return
            try {
                ctx.startActivity(intent(ctx, mode, wpId))
            } catch (_: Exception) {
            }
        }
    }

    private var mode by mutableStateOf<String?>(null)
    private var wpId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        Repo.init(this)
        read(intent)
        setContent {
            WorkTheme {
                val data by Repo.data.collectAsState()
                val m = mode
                val wp = wpId?.let { data.wp(it) }
                    ?: if (m == MODE_TEST) Workplace("test", name = "테스트 직장", color = 5) else null
                if (m != null && wp != null) {
                    PopupScreen(
                        mode = m,
                        wp = wp,
                        onPrimary = { primary(m, wp) },
                        onSecondary = { secondary(m, wp) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(i: Intent) {
        mode = i.getStringExtra(EX_MODE)
        wpId = i.getStringExtra(EX_WP)
        if (mode == null) finish()
    }

    private fun primary(m: String, wp: Workplace) {
        when (m) {
            MODE_ARRIVE -> Work.clockIn(this, wp.id)
            MODE_END -> Work.clockOut(this, wp.id)
        }
        finish()
    }

    private fun secondary(m: String, wp: Workplace) {
        when (m) {
            MODE_ARRIVE -> Notifier.cancel(this, wp.id, Notifier.T_ARRIVE)
            MODE_END -> Work.startOvertime(this, wp.id)
        }
        finish()
    }
}

@androidx.compose.runtime.Composable
private fun PopupScreen(mode: String, wp: Workplace, onPrimary: () -> Unit, onSecondary: () -> Unit) {
    val sw = swatch(wp.color)
    val title = when (mode) {
        PopupActivity.MODE_END -> "퇴근시간이 되었습니다.\n퇴근하시겠습니까?"
        PopupActivity.MODE_TEST -> "팝업 테스트예요!\n다른 앱 위에도 이렇게 떠요"
        else -> "직장에 도착하였습니다.\n근무하시겠습니까?"
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(sw.a.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Buddy(sw.b, wp.color, Modifier.size(84.dp), happy = mode != PopupActivity.MODE_END)
            }
            Spacer(Modifier.height(16.dp))
            Text(wp.name, color = Sub, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                color = Ink,
                fontSize = 21.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            if (mode == PopupActivity.MODE_END) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("연장 근무", Modifier.weight(1f), color = Lavender, textColor = Purple, onClick = onSecondary)
                    PrimaryButton("퇴근", Modifier.weight(1f), onClick = onPrimary)
                }
            } else {
                PrimaryButton("출근", Modifier.fillMaxWidth().height(60.dp), onClick = onPrimary)
                Spacer(Modifier.height(4.dp))
                PrimaryButton("나중에", Modifier.fillMaxWidth(), color = Color.White, textColor = Sub, onClick = onSecondary)
            }
        }
    }
}
