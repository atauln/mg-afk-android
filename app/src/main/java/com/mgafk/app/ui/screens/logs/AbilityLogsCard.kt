package com.mgafk.app.ui.screens.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.AbilityLog
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AbilityLogsCard(logs: List<AbilityLog>, modifier: Modifier = Modifier) {
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE) }

    AppCard(
        modifier = modifier,
        title = "Ability Logs",
        trailing = {
            if (logs.isNotEmpty()) {
                Text("${logs.size}", fontSize = 11.sp, color = TextMuted)
            }
        },
    ) {
        if (logs.isEmpty()) {
            Text("No abilities logged yet.", fontSize = 12.sp, color = TextMuted)
        } else {
            Column {
                logs.forEach { log ->
                    LogRow(log, dateFormat)
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: AbilityLog, dateFormat: SimpleDateFormat) {
    val abilityName = MgApi.abilityDisplayName(log.action)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceBorder.copy(alpha = 0.3f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (log.petSpecies.isNotBlank()) {
            SpriteImage(category = "pets", name = log.petSpecies, size = 24.dp, contentDescription = log.petSpecies)
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(abilityName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Accent)
            if (log.petName.isNotBlank()) {
                Text("${log.petName} · ${log.petSpecies}", fontSize = 11.sp, color = TextMuted)
            }
        }

        Text(
            text = dateFormat.format(Date(log.timestamp)),
            fontSize = 10.sp,
            color = TextMuted,
        )
    }
}
