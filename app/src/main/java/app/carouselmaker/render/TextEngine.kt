@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.carouselmaker.render

import android.content.Context
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText as drawTextLayout
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import app.carouselmaker.R
import app.carouselmaker.data.Item

class FontEntry(val name: String, val category: String, val family: FontFamily, val sizeScale: Float = 1f)

object FontCatalog {
    private fun f(res: Int, w: Int, italic: Boolean = false) = FontFamily(
        Font(
            res, FontWeight(w), if (italic) FontStyle.Italic else FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        ),
    )

    val categories = listOf("All", "Handwritten", "Bold", "Serif", "Typewriter", "Clean")

    val all: List<FontEntry> = listOf(
        FontEntry("Caveat", "Handwritten", f(R.font.caveat, 700), 1.25f),
        FontEntry("Permanent Marker", "Handwritten", f(R.font.permanent_marker, 400)),
        FontEntry("Shadows Into Light", "Handwritten", f(R.font.shadows_into_light, 400), 1.1f),
        FontEntry("Reenie Beanie", "Handwritten", f(R.font.reenie_beanie, 400), 1.45f),
        FontEntry("Bebas Neue", "Bold", f(R.font.bebas_neue, 400), 1.2f),
        FontEntry("Anton", "Bold", f(R.font.anton, 400)),
        FontEntry("Archivo Black", "Bold", f(R.font.archivo_black, 400), 0.9f),
        FontEntry("Oswald", "Bold", f(R.font.oswald, 600)),
        FontEntry("Playfair Display", "Serif", f(R.font.playfair_display, 700, italic = true)),
        FontEntry("DM Serif Display", "Serif", f(R.font.dm_serif_display, 400), 1.05f),
        FontEntry("Fraunces", "Serif", f(R.font.fraunces, 600)),
        FontEntry("Special Elite", "Typewriter", f(R.font.special_elite, 400), 0.95f),
        FontEntry("Courier Prime", "Typewriter", f(R.font.courier_prime, 400), 0.95f),
        FontEntry("Inter", "Clean", f(R.font.inter, 600), 0.95f),
        FontEntry("Space Grotesk", "Clean", f(R.font.space_grotesk, 600), 0.95f),
    )

    fun byName(name: String): FontEntry = all.firstOrNull { it.name == name } ?: all[1]
}

/** Lays out and draws text items in canvas units (1 sp == 1 unit). */
class TextEngine(context: Context) : TextRenderer {
    private val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f, 1f), LayoutDirection.Ltr, 64)
    private val cache = object : LinkedHashMap<String, TextLayoutResult>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TextLayoutResult>?) = size > 48
    }

    private fun layout(item: Item): TextLayoutResult {
        val key = "${item.text}|${item.font}|${item.fontSize}|${item.w}|${item.align}|${item.spacing}|${item.caps}|${item.color}"
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val entry = FontCatalog.byName(item.font)
        val style = TextStyle(
            color = Color(item.color),
            fontSize = item.fontSize.sp,
            fontFamily = entry.family,
            letterSpacing = item.spacing.sp,
            lineHeight = (item.fontSize * 1.12f).sp,
            textAlign = when (item.align) { "center" -> TextAlign.Center; "right" -> TextAlign.Right; else -> TextAlign.Left },
        )
        val text = (if (item.text.isEmpty()) " " else item.text).let { if (item.caps) it.uppercase() else it }
        val w = item.w.toInt().coerceAtLeast(40)
        val r = measurer.measure(text, style, constraints = Constraints(minWidth = w, maxWidth = w))
        synchronized(cache) { cache[key] = r }
        return r
    }

    override fun height(item: Item): Float = layout(item).size.height.toFloat()

    override fun DrawScope.drawText(item: Item, rect: Rect) {
        val layout = layout(item)
        val top = rect.topLeft
        val textColor = Color(item.color)
        val dark = (textColor.red * 0.299f + textColor.green * 0.587f + textColor.blue * 0.114f) < 0.5f
        when (item.effect) {
            "label" -> {
                val pad = item.fontSize * 0.18f
                val bg = if (dark) Color(0xFFF5F3EE) else Color(0xFF141414)
                drawRoundRect(bg, top - Offset(pad, pad * 0.6f), Size(rect.width + 2 * pad, layout.size.height + pad * 1.2f), CornerRadius(pad * 0.7f))
                drawTextLayout(layout, topLeft = top)
            }
            "outline" -> {
                val oc = if (dark) Color.White else Color(0xFF141414)
                drawTextLayout(
                    layout, color = oc, topLeft = top,
                    drawStyle = Stroke(width = item.fontSize * 0.12f, join = StrokeJoin.Round),
                )
                drawTextLayout(layout, topLeft = top)
            }
            "shadow" -> drawTextLayout(
                layout, topLeft = top,
                shadow = Shadow(Color(0x99000000), Offset(0f, item.fontSize * 0.04f), item.fontSize * 0.12f),
            )
            else -> drawTextLayout(layout, topLeft = top)
        }
    }
}
