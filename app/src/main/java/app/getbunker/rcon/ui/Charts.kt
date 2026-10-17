package app.getbunker.rcon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/** A small line chart with a filled area, a title and the latest value. Time runs left to right. */
@Composable
fun ChartCard(
    title: String,
    valueText: String,
    times: List<Long>,
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Int = 110,
    format: (Double) -> String = { String.format(Locale.US, "%.0f", it) },
    startAtZero: Boolean = true,
) {
    Panel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = B.c.muted, modifier = Modifier.weight(1f))
                Text(valueText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
            }
            val max = values.maxOrNull() ?: 0.0
            val min = values.minOrNull() ?: 0.0
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(height.dp)) {
                if (values.size < 2) {
                    Text("Collecting data...", color = B.c.dim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.Center))
                } else {
                    val grid = B.c.border
                    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
                        val lo0 = if (startAtZero) 0.0 else min
                        var hi = max
                        var lo = lo0
                        if (hi - lo < 1e-6) { hi = lo + 1.0 }
                        val pad = (hi - lo) * 0.08
                        hi += pad
                        if (!startAtZero) lo -= pad

                        for (i in 0..2) {
                            val y = size.height * i / 2f
                            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        }
                        val t0 = times.first()
                        val span = (times.last() - t0).coerceAtLeast(1L).toFloat()
                        fun px(i: Int) = (times[i] - t0) / span * size.width
                        fun py(v: Double) = (size.height - ((v - lo) / (hi - lo)).toFloat() * size.height)

                        val line = Path().apply {
                            moveTo(px(0), py(values[0]))
                            for (i in 1 until values.size) lineTo(px(i), py(values[i]))
                        }
                        val fill = Path().apply {
                            addPath(line)
                            lineTo(px(values.size - 1), size.height)
                            lineTo(px(0), size.height)
                            close()
                        }
                        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f))))
                        drawPath(line, color, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    Text(format(max), style = MaterialTheme.typography.labelSmall, color = B.c.dim, modifier = Modifier.align(Alignment.TopStart))
                    Text(format(if (startAtZero) 0.0 else min), style = MaterialTheme.typography.labelSmall, color = B.c.dim, modifier = Modifier.align(Alignment.BottomStart))
                }
            }
        }
    }
}
