package app.carouselmaker.data

import kotlinx.serialization.Serializable

/** Every slide is this many canvas units wide; heights follow the ratio. */
const val SLIDE_W = 1080f

@Serializable
enum class Ratio(val label: String, val title: String, val px: String, val aw: Int, val ah: Int) {
    R4_5("4:5", "Portrait", "1080×1350", 4, 5),
    R1_1("1:1", "Square", "1080×1080", 1, 1),
    R3_4("3:4", "Tall", "1080×1440", 3, 4);

    val slideH: Float get() = SLIDE_W * ah / aw
}

@Serializable
enum class ItemType { PHOTO, VIDEO, TEXT, STICKER, TAPE, PAPER }

@Serializable
enum class EdgeType { NONE, TORN, DECKLE, WAVE, ZIGZAG, FADE }

@Serializable
enum class StickerLook { WASH, INK, PENCIL }

@Serializable
data class Edge(
    val type: EdgeType = EdgeType.NONE,
    val left: Boolean = false,
    val top: Boolean = false,
    val right: Boolean = true,
    val bottom: Boolean = false,
    val roughness: Int = 55,
    val seed: Int = 7,
    val rim: Boolean = true,
)

@Serializable
data class Slide(val background: Int = 0xFFF5F3EE.toInt())

@Serializable
data class LayoutBlock(
    val id: String,
    val start: Int,
    val span: Int,
    val templateId: String,
    val margin: Float = 60f,
    val gap: Float = 24f,
    val radius: Float = 0f,
    val seamGap: Boolean = true,
)

@Serializable
data class Item(
    val id: String,
    val type: ItemType,
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 100f,
    val h: Float = 100f,
    val rotation: Float = 0f,
    val flipH: Boolean = false,
    val opacity: Float = 1f,
    // media
    val media: String? = null,
    val mw: Int = 0,
    val mh: Int = 0,
    val cropL: Float = 0f,
    val cropT: Float = 0f,
    val cropR: Float = 1f,
    val cropB: Float = 1f,
    // layout cell binding
    val blockId: String? = null,
    val cell: Int = -1,
    val radius: Float = 0f,
    val edge: Edge = Edge(),
    // slide-to-slide decoration (index of the slide left of the seam), -1 = none
    val seam: Int = -1,
    // text
    val text: String = "",
    val font: String = "Permanent Marker",
    val fontSize: Float = 72f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val align: String = "left",
    val spacing: Float = 0f,
    val effect: String = "shadow",
    val caps: Boolean = false,
    // sticker
    val stickerId: String? = null,
    val tint: Int? = null,
    val outline: Boolean = false,
    val shadow: Boolean = false,
    // tape style / paper kind
    val style: String = "",
    // video
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,
    val durationMs: Long = 0,
    val mute: Boolean = false,
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val ratio: Ratio = Ratio.R4_5,
    val slides: List<Slide> = listOf(Slide()),
    val blocks: List<LayoutBlock> = emptyList(),
    val items: List<Item> = emptyList(),
    val look: StickerLook = StickerLook.WASH,
    val updatedAt: Long = 0,
) {
    val slideCount: Int get() = slides.size
    val canvasW: Float get() = slides.size * SLIDE_W
    val canvasH: Float get() = ratio.slideH
}

/** A photo or video that has been copied into the project media folder. */
data class ImportedMedia(
    val name: String,
    val width: Int,
    val height: Int,
    val isVideo: Boolean,
    val durationMs: Long = 0,
)
