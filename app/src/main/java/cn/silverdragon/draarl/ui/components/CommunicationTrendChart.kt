package cn.silverdragon.draarl.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.silverdragon.draarl.data.DailyCommunicationStats
import java.util.Locale
import kotlin.math.roundToInt

private val CountLineColor = Color(0xFF1976D2)
private val DurationLineColor = Color(0xFF2E7D32)

@Composable
fun CommunicationTrendChart(data: List<DailyCommunicationStats>) {
    Card(shape = MaterialTheme.shapes.small) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("近30天通信趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (data.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无通信记录数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ChartLegend("通信次数", CountLineColor)
                    ChartLegend("通信时长", DurationLineColor)
                }
                TrendCanvas(data)
            }
        }
    }
}

@Composable
private fun ChartLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(width = 18.dp, height = 3.dp).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrendCanvas(data: List<DailyCommunicationStats>) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val description = buildString {
        append("近30天通信趋势，")
        append("通信次数共${data.sumOf(DailyCommunicationStats::count)}次，")
        append("通信时长${formatChartDuration(data.sumOf(DailyCommunicationStats::durationMs))}")
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .semantics { contentDescription = description },
    ) {
        val plotLeft = 36.dp.toPx()
        val plotRight = size.width - 42.dp.toPx()
        val plotTop = 6.dp.toPx()
        val plotBottom = size.height - 28.dp.toPx()
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        val maxCount = data.maxOf(DailyCommunicationStats::count).coerceAtLeast(1)
        val maxDuration = data.maxOf(DailyCommunicationStats::durationMs).coerceAtLeast(60_000L)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
        }

        repeat(GRID_LINE_COUNT) { index ->
            val ratio = index.toFloat() / (GRID_LINE_COUNT - 1)
            val y = plotTop + plotHeight * ratio
            drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1.dp.toPx())
            val countLabel = (maxCount * (1f - ratio)).roundToInt().toString()
            val durationLabel = "${(maxDuration * (1f - ratio) / 60_000f).roundToInt()}分"
            drawContext.canvas.nativeCanvas.apply {
                textPaint.textAlign = Paint.Align.RIGHT
                drawText(countLabel, plotLeft - 5.dp.toPx(), y + 3.dp.toPx(), textPaint)
                textPaint.textAlign = Paint.Align.LEFT
                drawText(durationLabel, plotRight + 5.dp.toPx(), y + 3.dp.toPx(), textPaint)
            }
        }

        val tickIndices = listOf(0, data.lastIndex / 4, data.lastIndex / 2, data.lastIndex * 3 / 4, data.lastIndex)
            .distinct()
        tickIndices.forEach { index ->
            val x = pointX(index, data.size, plotLeft, plotWidth)
            textPaint.textAlign = Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(
                data[index].date.takeLast(5),
                x,
                size.height - 5.dp.toPx(),
                textPaint,
            )
        }

        drawSeries(
            data = data,
            plotLeft = plotLeft,
            plotTop = plotTop,
            plotWidth = plotWidth,
            plotHeight = plotHeight,
            maxValue = maxCount.toLong(),
            value = { it.count.toLong() },
            color = CountLineColor,
        )
        drawSeries(
            data = data,
            plotLeft = plotLeft,
            plotTop = plotTop,
            plotWidth = plotWidth,
            plotHeight = plotHeight,
            maxValue = maxDuration,
            value = DailyCommunicationStats::durationMs,
            color = DurationLineColor,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    data: List<DailyCommunicationStats>,
    plotLeft: Float,
    plotTop: Float,
    plotWidth: Float,
    plotHeight: Float,
    maxValue: Long,
    value: (DailyCommunicationStats) -> Long,
    color: Color,
) {
    val path = Path()
    data.forEachIndexed { index, item ->
        val x = pointX(index, data.size, plotLeft, plotWidth)
        val ratio = (value(item).toFloat() / maxValue).coerceIn(0f, 1f)
        val y = plotTop + plotHeight * (1f - ratio)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    if (data.size == 1) {
        val ratio = (value(data.first()).toFloat() / maxValue).coerceIn(0f, 1f)
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(plotLeft, plotTop + plotHeight * (1f - ratio)))
    }
}

private fun pointX(index: Int, size: Int, plotLeft: Float, plotWidth: Float): Float =
    if (size <= 1) plotLeft else plotLeft + plotWidth * index / (size - 1)

private fun formatChartDuration(durationMs: Long): String = when {
    durationMs >= 3_600_000L -> "%.1f小时".format(Locale.CHINA, durationMs / 3_600_000.0)
    durationMs >= 60_000L -> "%.1f分钟".format(Locale.CHINA, durationMs / 60_000.0)
    else -> "${durationMs / 1_000}秒"
}

private const val GRID_LINE_COUNT = 5
