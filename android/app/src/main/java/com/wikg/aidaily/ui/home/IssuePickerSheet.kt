package com.wikg.aidaily.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wikg.aidaily.data.model.DailyIndex
import com.wikg.aidaily.ui.components.Pill
import com.wikg.aidaily.ui.theme.AppTheme
import com.wikg.aidaily.util.parseDate
import com.wikg.aidaily.util.weekdayCn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IssuePickerSheet(
    index: DailyIndex,
    selected: String?,
    cachedDates: Set<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("往期日报", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Text("共 ${index.issues.size} 期", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp)) {
            items(index.issues, key = { it.date }) { s ->
                val d = parseDate(s.date)
                val sel = s.date == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onSelect(s.date) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        Modifier.width(52.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else AppTheme.extra.card)
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val fg = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        Text(d?.dayOfMonth?.toString() ?: "", style = MaterialTheme.typography.headlineSmall, color = fg)
                        Text(d?.let { "${it.monthValue}月" } ?: "", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (d?.let { "${it.year}年 " + weekdayCn(it.dayOfWeek) } ?: s.date) + " · " + s.title,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.width(6.dp))
                            if (s.date == index.latest) Pill("最新", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.weight(1f))
                            if (s.date in cachedDates) Icon(Icons.Outlined.OfflinePin, "已离线缓存", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.highlights,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (s.itemCount > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text("${s.itemCount} 条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Box(Modifier.height(1.dp))
    }
}
