package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.AmateurBand
import cn.silverdragon.draarl.tools.ChinaAmateurFrequencyPlan

@Composable
@Suppress("LongMethod")
internal fun FrequencyPlanScreen(onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val numericQuery = query.toDoubleOrNull()
    val visibleBands = remember(query, filter) {
        ChinaAmateurFrequencyPlan.bands.filter { band ->
            val matchesFilter = when (filter) {
                "primary" -> !band.secondary
                "secondary" -> band.secondary
                else -> true
            }
            val matchesQuery = if (query.isBlank()) {
                true
            } else if (numericQuery != null) {
                band.contains(numericQuery)
            } else {
                query.trim().let { it in band.range || it in band.status }
            }
            matchesFilter && matchesQuery
        }
    }

    Column(Modifier.fillMaxSize()) {
        ToolHeader("中国业余频率划分", onBack)
        LazyColumn(
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.filter { character -> character.isDigit() || character == '.' } },
                        label = { Text("查询频率 MHz") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        supportingText = { Text("输入工作频率，或留空浏览全部 41 个频段") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("all" to "全部", "primary" to "主要", "secondary" to "次要").forEach { (key, label) ->
                            FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                        }
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            "次要业务不得干扰主要业务，也不得要求主要业务提供保护。实际操作须同时遵守电台执照及当地管理要求。",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (visibleBands.isEmpty()) {
                item {
                    ToolListFeedback(
                        loading = false,
                        title = "未找到对应频段",
                        detail = "该频率不在当前中国内地业余业务工作频段表中",
                        modifier = Modifier.fillParentMaxSize()
                    )
                }
            } else {
                items(visibleBands, key = AmateurBand::range) { band ->
                    FrequencyBandRow(band)
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                }
            }
            item {
                Text(
                    "依据：2023 年版《中华人民共和国无线电频率划分规定》及无线电管理部门公布的业余业务工作频段表。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FrequencyBandRow(band: AmateurBand) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(band.range, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${band.lowerMHz.formatNumber()}-${band.upperMHz.formatNumber()} MHz",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            band.status,
            style = MaterialTheme.typography.labelLarge,
            color = if (band.secondary) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
        )
    }
}
