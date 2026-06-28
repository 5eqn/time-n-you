package com.seqn.timewatcher.ui.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UsageScreen() {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text("用法介绍", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Section("核心理念") {
            Bullet("在自己计划做一件事情「之前」手动切换到对应的状态，并在切换后立刻开始专心做这件事，不得分心。")
            Bullet("番茄钟会管着你专注——只有真正需要它管的人，才会从中受益。")
        }
        Section("状态与统计") {
            Bullet("休息：睡觉、吃饭（不计入「可用时间」）。")
            Bullet("有效：主业、爱好（计入「实际有效时间」）。")
            Bullet("无效：运动、摆烂（占用可用时间，但不计入有效）。")
            Bullet("利用率 = 有效时间 ÷（总时间 − 休息时间）；达到 50% 且有效超过 7 小时为「优秀」。")
            Bullet("注意：运动被归为无效，因为它太容易被「刷」而虚高利用率。")
        }
        Section("推荐工作流") {
            Bullet("停止在床上玩手机的前一刻（而非上床前）切换为「睡觉」。")
            Bullet("起床碰到手机后立刻切换为「摆烂」，直到进入下一个有意义的状态。")
            Bullet("仅在做饭、吃饭时进入「吃饭」；洗漱、洗澡、如厕等必要活动一律算「摆烂」，以减少切换负担。")
            Bullet("开始主业或爱好时进入对应状态并立刻开始；番茄钟响铃后，建议立刻切换为「运动」适度起身活动、避免久坐；准备好后再切回。")
        }
        Section("关于后台计时") {
            Bullet("应用被关闭期间，时间依然会被正确累计——所有切换都会被永久记录。")
            Bullet("主业/爱好的番茄钟即便在应用关闭时到点，也会自动切换状态并响铃提醒。")
        }
        Spacer()
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun ColumnScope.Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Spacer() {
    androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
}
