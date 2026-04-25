package top.chiloven.lukosbot2.commands

import top.chiloven.lukosbot2.util.ImageTextUtils
import top.chiloven.lukosbot2.util.ModernImageDraw
import top.chiloven.lukosbot2.util.PathUtils
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

object UsageImageUtils {

    private fun configuredDefaultStyle(): ImageStyle = ImageStyle(
        palette = ModernImageDraw.defaultPalette()
    )

    @JvmStatic
    fun renderUsagePng(
        filenameBase: String,
        node: UsageNode,
        options: UsageTextRenderer.Options,
        style: ImageStyle
    ): RenderedImage {
        Objects.requireNonNull(style, "style")
        val result = UsageTextRenderer.render(node, options)
        return renderLinesPng(filenameBase, result.lines(), style)
    }

    @JvmStatic
    fun renderLinesPng(
        filenameBase: String,
        lines: List<UsageTextRenderer.RenderedLine>?,
        style0: ImageStyle
    ): RenderedImage {
        val style = style0.resolveFontFallbacks()
        val palette = style.palette
        val safeBase = PathUtils.sanitizeFileName(filenameBase, fallback = "usage", maxLength = 64)
        val filename = "$safeBase.png"

        val model = parse(lines ?: emptyList())
        val width = max(style.minWidth, style.maxWidth)
        val contentWidth = width - style.padding * 2

        val probe = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
        val g0 = probe.createGraphics()
        ModernImageDraw.quality(g0)
        val cache = ImageTextUtils.GlyphRunCache()

        val headerLayout = measureHeader(g0, model.header, contentWidth, cache, style)
        val flowSectionsLayout = layoutSections(g0, model.sections, contentWidth, headerLayout.height, cache, style)
        val singleColumnSectionsLayout = layoutSingleColumnSections(
            g0,
            model.sections,
            contentWidth,
            headerLayout.height,
            cache,
            style
        )
        val sectionsLayout = chooseSectionsLayout(
            model.sections,
            contentWidth,
            flowSectionsLayout,
            singleColumnSectionsLayout,
            style
        )
        val height = max(style.minHeight, sectionsLayout.totalHeight)
        g0.dispose()

        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        ModernImageDraw.quality(g)
        ModernImageDraw.background(g, width, height, palette)

        drawHeader(g, model.header, headerLayout, style.padding, style.padding, contentWidth, cache, style)
        for (placement in sectionsLayout.placements) {
            drawSection(g, placement.layout, placement.x, placement.y, cache, style)
        }

        g.dispose()

        return try {
            val bos = ByteArrayOutputStream()
            ImageIO.write(img, "png", bos)
            RenderedImage(filename, bos.toByteArray(), "image/png")
        } catch (e: Exception) {
            throw RuntimeException("Render usage PNG failed: ${e.message}", e)
        }
    }

    private fun parse(lines: List<UsageTextRenderer.RenderedLine>): PageModel {
        val header = HeaderModel(title = "命令帮助")
        val sections = ArrayList<SectionModel>()
        var currentSection: SectionModel? = null

        for (line in lines) {
            val text = line.plain()?.trim().orEmpty()
            if (line.kind() == UsageTextRenderer.LineKind.BLANK || text.isBlank()) continue

            when (line.kind()) {
                UsageTextRenderer.LineKind.TITLE -> header.title = text
                UsageTextRenderer.LineKind.SUBTITLE -> {
                    if (looksLikeAliasLine(text)) header.aliases.addAll(parseAliases(text))
                    else header.descriptionLines.add(text)
                }

                UsageTextRenderer.LineKind.HEADING -> {
                    currentSection = SectionModel(cleanHeading(text))
                    sections.add(currentSection)
                }

                UsageTextRenderer.LineKind.LABEL,
                UsageTextRenderer.LineKind.CODE,
                UsageTextRenderer.LineKind.TEXT,
                UsageTextRenderer.LineKind.BULLET -> {
                    if (currentSection == null) {
                        if (line.kind() == UsageTextRenderer.LineKind.TEXT) header.descriptionLines.add(text)
                    } else {
                        currentSection.items.add(ItemModel(line.kind(), text))
                    }
                }

                UsageTextRenderer.LineKind.BLANK -> Unit
            }
        }

        return PageModel(header, sections)
    }

    private fun measureHeader(
        g: Graphics2D,
        header: HeaderModel,
        width: Int,
        cache: ImageTextUtils.GlyphRunCache,
        style: ImageStyle
    ): HeaderLayout {
        val rows = ArrayList<List<String>>()
        val titleWidth = width - style.cardPadding * 2
        val descWidth = width - style.cardPadding * 2

        var contentHeight = 0
        contentHeight += pillHeight(g, style.labelFont)
        contentHeight += style.headerBadgeGap

        val titleLines = ImageTextUtils.wrapTextRunAware(
            g,
            header.title,
            style.titleFont,
            style.bodyFont,
            cache,
            titleWidth
        ).ifEmpty { listOf(header.title) }
        val titleLineHeight = textLineHeight(g, style.titleFont, 1.10f)
        contentHeight += titleLines.size * titleLineHeight

        if (header.descriptionLines.isNotEmpty()) {
            contentHeight += style.headerTextGap
            for (desc in header.descriptionLines) {
                val wrapped = ImageTextUtils.wrapTextRunAware(
                    g,
                    desc,
                    style.subtitleFont,
                    style.bodyFont,
                    cache,
                    descWidth
                ).ifEmpty { listOf(desc) }
                rows.add(wrapped)
                contentHeight += wrapped.size * textLineHeight(g, style.subtitleFont, 1.30f)
                contentHeight += style.paragraphGap
            }
            contentHeight -= style.paragraphGap
        }

        val pillRows = layoutPills(g, header.aliases, style.labelFont, descWidth, style)
        if (pillRows.isNotEmpty()) {
            contentHeight += style.aliasGap
            contentHeight += pillRows.size * pillHeight(g, style.labelFont)
            if (pillRows.size > 1) contentHeight += (pillRows.size - 1) * style.pillRowGap
        }

        return HeaderLayout(
            titleLines = titleLines,
            descriptionRows = rows,
            aliasRows = pillRows,
            height = contentHeight + style.cardPadding * 2
        )
    }

    private fun layoutSections(
        g: Graphics2D,
        sections: List<SectionModel>,
        contentWidth: Int,
        headerHeight: Int,
        cache: ImageTextUtils.GlyphRunCache,
        style: ImageStyle
    ): SectionsLayout {
        if (sections.isEmpty()) {
            return SectionsLayout(emptyList(), style.padding + headerHeight + style.padding)
        }

        val placements = ArrayList<SectionPlacement>()
        val startY = style.padding + headerHeight + style.sectionGap
        val halfWidth = (contentWidth - style.sectionGap) / 2
        val allowTwoColumns = contentWidth >= style.multiColumnSectionMinWidth

        var leftY = startY
        var rightY = startY
        val leftX = style.padding
        val rightX = style.padding + halfWidth + style.sectionGap

        for (section in sections) {
            val narrowLayout = measureSection(g, section, halfWidth, cache, style)
            val wideLayout = if (allowTwoColumns) {
                measureSection(g, section, contentWidth, cache, style)
            } else {
                narrowLayout
            }
            val fullWidth = !allowTwoColumns || shouldSpanFullWidth(section, narrowLayout, wideLayout, style)
            val layout = if (fullWidth) wideLayout else narrowLayout

            if (fullWidth) {
                val y = max(leftY, rightY)
                placements.add(SectionPlacement(layout, style.padding, y))
                leftY = y + layout.height + style.sectionGap
                rightY = leftY
            } else if (leftY <= rightY) {
                placements.add(SectionPlacement(layout, leftX, leftY))
                leftY += layout.height + style.sectionGap
            } else {
                placements.add(SectionPlacement(layout, rightX, rightY))
                rightY += layout.height + style.sectionGap
            }
        }

        val totalHeight = max(leftY, rightY) - style.sectionGap + style.padding
        return SectionsLayout(placements, totalHeight)
    }

    private fun layoutSingleColumnSections(
        g: Graphics2D,
        sections: List<SectionModel>,
        contentWidth: Int,
        headerHeight: Int,
        cache: ImageTextUtils.GlyphRunCache,
        style: ImageStyle
    ): SectionsLayout {
        if (sections.isEmpty()) {
            return SectionsLayout(emptyList(), style.padding + headerHeight + style.padding)
        }

        val placements = ArrayList<SectionPlacement>()
        var cursorY = style.padding + headerHeight + style.sectionGap
        for (section in sections) {
            val layout = measureSection(g, section, contentWidth, cache, style)
            placements.add(SectionPlacement(layout, style.padding, cursorY))
            cursorY += layout.height + style.sectionGap
        }

        val totalHeight = cursorY - style.sectionGap + style.padding
        return SectionsLayout(placements, totalHeight)
    }

    private fun chooseSectionsLayout(
        sections: List<SectionModel>,
        contentWidth: Int,
        flowLayout: SectionsLayout,
        singleColumnLayout: SectionsLayout,
        style: ImageStyle
    ): SectionsLayout {
        if (sections.isEmpty()) return flowLayout
        val flowAspectRatio = flowLayout.totalHeight.toDouble() / contentWidth.toDouble()
        val preferSingleColumn =
            sections.size <= style.preferredSingleColumnMaxSections &&
                    flowAspectRatio <= style.preferredSingleColumnAspectRatioThreshold
        return if (preferSingleColumn) singleColumnLayout else flowLayout
    }

    private fun measureSection(
        g: Graphics2D,
        section: SectionModel,
        width: Int,
        cache: ImageTextUtils.GlyphRunCache,
        style: ImageStyle
    ): SectionLayout {
        val innerWidth = width - style.cardPadding * 2
        val titleHeight = textLineHeight(g, style.headingFont, 1.0f)
        val useItemGrid = shouldUseItemGrid(section, innerWidth, style)
        val columnWidth = if (useItemGrid) (innerWidth - style.gridGap) / 2 else innerWidth

        val placedItems = ArrayList<PlacedItem>()
        var cursorY = style.cardPadding + titleHeight
        if (section.items.isNotEmpty()) cursorY += style.sectionTitleGap

        var index = 0
        while (index < section.items.size) {
            val current = section.items[index]
            when {
                !useItemGrid || current.kind != UsageTextRenderer.LineKind.BULLET -> {
                    val measured = measureItem(g, current, innerWidth, cache, style)
                    placedItems.add(PlacedItem(measured, 0, cursorY, measured.boxWidth, measured.height))
                    cursorY += measured.height + style.itemGap
                    index++
                }

                index + 1 < section.items.size && section.items[index + 1].kind == UsageTextRenderer.LineKind.BULLET -> {
                    val left = measureItem(g, current, columnWidth, cache, style)
                    val right = measureItem(g, section.items[index + 1], columnWidth, cache, style)
                    val rowHeight = max(left.height, right.height)
                    placedItems.add(PlacedItem(left, 0, cursorY, columnWidth, rowHeight))
                    placedItems.add(PlacedItem(right, columnWidth + style.gridGap, cursorY, columnWidth, rowHeight))
                    cursorY += rowHeight + style.itemGap
                    index += 2
                }

                else -> {
                    val measured = measureItem(g, current, innerWidth, cache, style)
                    placedItems.add(PlacedItem(measured, 0, cursorY, measured.boxWidth, measured.height))
                    cursorY += measured.height + style.itemGap
                    index++
                }
            }
        }

        if (placedItems.isNotEmpty()) cursorY -= style.itemGap
        val totalHeight = cursorY + style.cardPadding
        return SectionLayout(section.title, width, totalHeight, placedItems)
    }

    private fun measureItem(
        g: Graphics2D,
        item: ItemModel,
        width: Int,
        cache: ImageTextUtils.GlyphRunCache,
        style: ImageStyle
    ): MeasuredItem {
        return when (item.kind) {
            UsageTextRenderer.LineKind.CODE -> {
                val lineHeight = textLineHeight(g, style.codeFont, 1.12f)
                val maxBoxWidth = min(width, style.codeBlockMaxWidth)
                val maxTextWidth = max(1, maxBoxWidth - style.codePaddingX * 2)
                val lines = ImageTextUtils.wrapTextRunAware(
                    g,
                    item.text,
                    style.codeFont,
                    style.bodyFont,
                    cache,
                    maxTextWidth
                ).ifEmpty { listOf(item.text) }
                var measuredTextWidth = 0
                for (line in lines) {
                    measuredTextWidth = max(
                        measuredTextWidth,
                        ImageTextUtils.measureTextRunAware(g, line, style.codeFont, style.bodyFont, cache)
                    )
                }
                val naturalBoxWidth = min(maxBoxWidth, measuredTextWidth + style.codePaddingX * 2)
                val h = style.codePaddingY * 2 + lines.size * lineHeight
                MeasuredItem(item.kind, item.text, lines, null, h, naturalBoxWidth)
            }

            UsageTextRenderer.LineKind.LABEL -> {
                val h = pillHeight(g, style.labelFont)
                MeasuredItem(item.kind, sanitizeChipText(item.text), listOf(item.text), null, h, width)
            }

            UsageTextRenderer.LineKind.BULLET -> {
                val raw = item.text.removePrefix("•").trim()
                val split = splitBullet(raw)
                if (split != null) {
                    val bodyLines = if (split.second.isBlank()) {
                        emptyList()
                    } else {
                        ImageTextUtils.wrapTextRunAware(
                            g,
                            split.second,
                            style.bodyFont,
                            style.bodyFont,
                            cache,
                            width - style.bulletBoxPadding * 2
                        ).ifEmpty { listOf(split.second) }
                    }
                    val bodyLineHeight = textLineHeight(g, style.bodyFont, 1.26f)
                    val top = style.bulletBoxPadding * 2 + pillHeight(g, style.labelFont)
                    val body = if (bodyLines.isEmpty()) 0 else style.bulletBodyGap + bodyLines.size * bodyLineHeight
                    MeasuredItem(item.kind, raw, bodyLines, sanitizeChipText(split.first), top + body, width)
                } else {
                    val lineHeight = textLineHeight(g, style.bodyFont, 1.26f)
                    val lines = ImageTextUtils.wrapTextRunAware(
                        g,
                        sanitizeInlineBulletText(raw),
                        style.bodyFont,
                        style.bodyFont,
                        cache,
                        width - style.bulletBoxPadding * 2 - style.bulletDotGap - style.bulletDotSize
                    ).ifEmpty { listOf(sanitizeInlineBulletText(raw)) }
                    val h = style.bulletBoxPadding * 2 + lines.size * lineHeight
                    MeasuredItem(item.kind, raw, lines, null, h, width)
                }
            }

            else -> {
                val lineHeight = textLineHeight(g, style.bodyFont, 1.26f)
                val lines = ImageTextUtils.wrapTextRunAware(
                    g,
                    item.text,
                    style.bodyFont,
                    style.bodyFont,
                    cache,
                    width
                ).ifEmpty { listOf(item.text) }
                val h = lines.size * lineHeight
                MeasuredItem(item.kind, item.text, lines, null, h, width)
            }
        }
    }

    private fun drawHeader(
        g: Graphics2D,
        header: HeaderModel,
        layout: HeaderLayout,
        x: Int,
        y: Int,
        width: Int,
        cache: ImageTextUtils.GlyphRunCache,
        style: ImageStyle
    ) {
        val palette = style.palette
        ModernImageDraw.card(g, x, y, width, layout.height, style.cardRadius, palette)

        var cursorY = y + style.cardPadding
        val contentX = x + style.cardPadding

        ModernImageDraw.pill(
            g,
            "COMMAND HELP",
            contentX,
            cursorY,
            style.labelFont,
            style.palette.secondaryText,
            style.palette.pillBg
        )
        cursorY += pillHeight(g, style.labelFont) + style.headerBadgeGap

        val titleLineHeight = textLineHeight(g, style.titleFont, 1.10f)
        g.color = palette.text
        for (line in layout.titleLines) {
            val baseline = cursorY + ImageTextUtils.ascent(g, style.titleFont)
            ImageTextUtils.drawStringWithFallback(g, line, contentX, baseline, style.titleFont, style.bodyFont, cache)
            cursorY += titleLineHeight
        }

        if (layout.descriptionRows.isNotEmpty()) {
            cursorY += style.headerTextGap
            val descLineHeight = textLineHeight(g, style.subtitleFont, 1.30f)
            g.color = style.palette.secondaryText
            for (row in layout.descriptionRows) {
                for (line in row) {
                    val baseline = cursorY + ImageTextUtils.ascent(g, style.subtitleFont)
                    ImageTextUtils.drawStringWithFallback(
                        g,
                        line,
                        contentX,
                        baseline,
                        style.subtitleFont,
                        style.bodyFont,
                        cache
                    )
                    cursorY += descLineHeight
                }
                cursorY += style.paragraphGap
            }
            cursorY -= style.paragraphGap
        }

        if (layout.aliasRows.isNotEmpty()) {
            cursorY += style.aliasGap
            for (row in layout.aliasRows) {
                var cursorX = contentX
                for (alias in row) {
                    val used = ModernImageDraw.pill(
                        g,
                        alias,
                        cursorX,
                        cursorY,
                        style.labelFont,
                        style.palette.secondaryText,
                        style.palette.chipBg
                    )
                    cursorX += used + style.pillGap
                }
                cursorY += pillHeight(g, style.labelFont) + style.pillRowGap
            }
        }
    }

    private fun drawSection(
        g: Graphics2D,
        layout: SectionLayout,
        x: Int,
        y: Int,
        cache: ImageTextUtils.GlyphRunCache,
        style: ImageStyle
    ) {
        val palette = style.palette
        ModernImageDraw.card(g, x, y, layout.width, layout.height, style.cardRadius, palette)

        val titleX = x + style.cardPadding
        val titleY = y + style.cardPadding
        val barH = textLineHeight(g, style.headingFont, 1.0f) - 2

        g.color = palette.accent
        g.fillRoundRect(titleX, titleY + 1, style.sectionBarWidth, barH, style.sectionBarWidth, style.sectionBarWidth)

        g.color = palette.text
        ImageTextUtils.drawStringWithFallback(
            g,
            layout.title,
            titleX + style.sectionBarWidth + style.sectionBarGap,
            titleY + ImageTextUtils.ascent(g, style.headingFont),
            style.headingFont,
            style.bodyFont,
            cache
        )

        for (placed in layout.items) {
            drawMeasuredItem(
                g,
                placed.item,
                titleX + placed.x,
                y + placed.y,
                placed.width,
                placed.boxHeight,
                cache,
                style
            )
        }
    }

    private fun drawMeasuredItem(
        g: Graphics2D,
        item: MeasuredItem,
        x: Int,
        y: Int,
        width: Int,
        boxHeight: Int,
        cache: ImageTextUtils.GlyphRunCache,
        style: ImageStyle
    ) {
        val palette = style.palette
        when (item.kind) {
            UsageTextRenderer.LineKind.CODE -> {
                g.color = style.palette.codeBg
                g.fillRoundRect(x, y, width, boxHeight, style.codeRadius, style.codeRadius)
                var lineY = y + style.codePaddingY
                val lineHeight = textLineHeight(g, style.codeFont, 1.12f)
                g.color = style.palette.codeText
                for (line in item.lines) {
                    val baseline = lineY + ImageTextUtils.ascent(g, style.codeFont)
                    ImageTextUtils.drawStringWithFallback(
                        g,
                        line,
                        x + style.codePaddingX,
                        baseline,
                        style.codeFont,
                        style.bodyFont,
                        cache
                    )
                    lineY += lineHeight
                }
            }

            UsageTextRenderer.LineKind.LABEL -> {
                drawChip(g, item.text, x, y, style)
            }

            UsageTextRenderer.LineKind.BULLET -> {
                g.color = style.palette.itemBg
                g.fillRoundRect(x, y, width, boxHeight, style.itemRadius, style.itemRadius)
                ModernImageDraw.roundedBorder(g, x, y, width, boxHeight, style.itemRadius, style.palette.itemBorder)

                if (item.head != null) {
                    val pillY = y + style.bulletBoxPadding
                    drawChip(g, item.head, x + style.bulletBoxPadding, pillY, style)

                    var lineY = pillY + pillHeight(g, style.labelFont) + style.bulletBodyGap
                    val lineHeight = textLineHeight(g, style.bodyFont, 1.26f)
                    g.color = style.palette.secondaryText
                    for (line in item.lines) {
                        val baseline = lineY + ImageTextUtils.ascent(g, style.bodyFont)
                        ImageTextUtils.drawStringWithFallback(
                            g,
                            line,
                            x + style.bulletBoxPadding,
                            baseline,
                            style.bodyFont,
                            style.bodyFont,
                            cache
                        )
                        lineY += lineHeight
                    }
                } else {
                    val dotX = x + style.bulletBoxPadding
                    val dotY = y + style.bulletBoxPadding + 7
                    g.color = palette.accent
                    g.fillOval(dotX, dotY, style.bulletDotSize, style.bulletDotSize)

                    var lineY = y + style.bulletBoxPadding
                    val textX = dotX + style.bulletDotSize + style.bulletDotGap
                    val lineHeight = textLineHeight(g, style.bodyFont, 1.26f)
                    g.color = style.palette.secondaryText
                    for (line in item.lines) {
                        val baseline = lineY + ImageTextUtils.ascent(g, style.bodyFont)
                        ImageTextUtils.drawStringWithFallback(
                            g,
                            line,
                            textX,
                            baseline,
                            style.bodyFont,
                            style.bodyFont,
                            cache
                        )
                        lineY += lineHeight
                    }
                }
            }

            else -> {
                var lineY = y
                val lineHeight = textLineHeight(g, style.bodyFont, 1.26f)
                g.color = style.palette.secondaryText
                for (line in item.lines) {
                    val baseline = lineY + ImageTextUtils.ascent(g, style.bodyFont)
                    ImageTextUtils.drawStringWithFallback(
                        g,
                        line,
                        x,
                        baseline,
                        style.bodyFont,
                        style.bodyFont,
                        cache
                    )
                    lineY += lineHeight
                }
            }
        }
    }

    private fun drawChip(g: Graphics2D, rawText: String, x: Int, y: Int, style: ImageStyle): Int {
        val text = sanitizeChipText(rawText)
        return if (isCodeChip(text)) {
            ModernImageDraw.pill(g, text, x, y, style.labelFont, style.palette.codeText, style.palette.codeChipBg)
        } else {
            ModernImageDraw.pill(g, text, x, y, style.labelFont, style.palette.accent, style.palette.accentChipBg)
        }
    }

    private fun sanitizeChipText(rawText: String): String {
        var t = rawText.trim()
        if (t.startsWith("•")) t = t.removePrefix("•").trim()
        if (t.startsWith("- ")) t = t.substring(2).trim()
        if (t.startsWith("`") && t.endsWith("`") && t.length >= 2) {
            t = t.substring(1, t.length - 1)
        }
        return t.trim()
    }

    private fun sanitizeInlineBulletText(rawText: String): String {
        return rawText.replace("`", "").trim()
    }

    private fun isCodeChip(text: String): Boolean {
        return text.startsWith("/")
    }

    private fun shouldSpanFullWidth(
        section: SectionModel,
        narrowLayout: SectionLayout,
        wideLayout: SectionLayout,
        style: ImageStyle
    ): Boolean {
        val codeCount = section.items.count { it.kind == UsageTextRenderer.LineKind.CODE }
        return when (section.title) {
            "参数与选项", "提示", "子命令" -> false
            "快速使用", "示例" -> {
                if (narrowLayout.height <= style.preferredColumnSectionMaxHeight && codeCount <= style.preferredColumnCodeCount) {
                    false
                } else {
                    narrowLayout.height - wideLayout.height >= style.fullWidthHeightGainThreshold
                }
            }

            else -> codeCount >= 5 && (narrowLayout.height - wideLayout.height) >= style.fullWidthHeightGainThreshold
        }
    }

    private fun shouldUseItemGrid(section: SectionModel, innerWidth: Int, style: ImageStyle): Boolean {
        if (innerWidth < style.multiColumnItemMinWidth) return false
        return when (section.title) {
            "参数与选项", "提示", "子命令" -> true
            else -> false
        }
    }

    private fun looksLikeAliasLine(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("别名") || t.startsWith("aliases", ignoreCase = true)
    }

    private fun parseAliases(text: String): List<String> {
        val raw = when {
            text.contains("：") -> text.substringAfter("：")
            text.contains(":") -> text.substringAfter(":")
            else -> text.removePrefix("别名")
        }.trim()
        if (raw.isBlank()) return emptyList()
        return raw.split(Regex("\\s*[·、,，/|]\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun cleanHeading(text: String): String = text.trim().removeSuffix("：").removeSuffix(":")

    private fun splitBullet(text: String): Pair<String, String>? {
        val separators = listOf(" — ", " – ", " - ", ": ", "：")
        for (sep in separators) {
            val idx = text.indexOf(sep)
            if (idx > 0) {
                val left = text.substring(0, idx).trim()
                val right = text.substring(idx + sep.length).trim()
                if (left.isNotBlank()) return left to right
            }
        }
        return null
    }

    private fun layoutPills(
        g: Graphics2D,
        pills: List<String>,
        font: Font,
        maxWidth: Int,
        style: ImageStyle
    ): List<List<String>> {
        if (pills.isEmpty()) return emptyList()
        val rows = ArrayList<MutableList<String>>()
        var current = ArrayList<String>()
        var rowWidth = 0

        for (pill in pills) {
            val w = pillWidth(g, font, pill)
            if (current.isNotEmpty() && rowWidth + style.pillGap + w > maxWidth) {
                rows.add(current)
                current = ArrayList()
                rowWidth = 0
            }
            if (current.isNotEmpty()) rowWidth += style.pillGap
            current.add(pill)
            rowWidth += w
        }
        if (current.isNotEmpty()) rows.add(current)
        return rows
    }

    private fun textLineHeight(g: Graphics2D, font: Font, multiplier: Float): Int {
        return max(1, (ImageTextUtils.height(g, font) * multiplier).toInt())
    }

    private fun pillWidth(g: Graphics2D, font: Font, text: String): Int {
        val fm = g.getFontMetrics(font)
        return fm.stringWidth(text) + 24
    }

    private fun pillHeight(g: Graphics2D, font: Font): Int {
        val fm = g.getFontMetrics(font)
        return fm.height + 6
    }

    data class ImageStyle(
        val maxWidth: Int = 1380,
        val minWidth: Int = 1180,
        val minHeight: Int = 260,
        val padding: Int = 28,
        val cardPadding: Int = 26,
        val cardRadius: Int = 24,
        val palette: ModernImageDraw.Palette = ModernImageDraw.defaultPalette(),
        val codePaddingX: Int = 18,
        val codePaddingY: Int = 12,
        val codeRadius: Int = 14,
        val codeBlockMaxWidth: Int = 880,
        val sectionGap: Int = 18,
        val sectionTitleGap: Int = 14,
        val itemGap: Int = 10,
        val gridGap: Int = 12,
        val itemRadius: Int = 16,
        val bulletBoxPadding: Int = 14,
        val bulletBodyGap: Int = 8,
        val bulletDotSize: Int = 8,
        val bulletDotGap: Int = 10,
        val sectionBarWidth: Int = 5,
        val sectionBarGap: Int = 12,
        val headerBadgeGap: Int = 14,
        val headerTextGap: Int = 12,
        val paragraphGap: Int = 8,
        val aliasGap: Int = 14,
        val pillGap: Int = 8,
        val pillRowGap: Int = 8,
        val multiColumnSectionMinWidth: Int = 1050,
        val multiColumnItemMinWidth: Int = 420,
        val preferredSingleColumnMaxSections: Int = 4,
        val preferredSingleColumnAspectRatioThreshold: Double = 0.75,
        val preferredColumnSectionMaxHeight: Int = 620,
        val preferredColumnCodeCount: Int = 8,
        val fullWidthHeightGainThreshold: Int = 120,
        val titleFont: Font = Font("Microsoft Yahei UI", Font.BOLD, 32),
        val headingFont: Font = Font("Microsoft Yahei UI", Font.BOLD, 20),
        val subtitleFont: Font = Font("Microsoft Yahei UI", Font.PLAIN, 17),
        val bodyFont: Font = Font("Microsoft Yahei UI", Font.PLAIN, 16),
        val labelFont: Font = Font("Microsoft Yahei UI", Font.BOLD, 13),
        val codeFont: Font = Font("Cascadia Code", Font.PLAIN, 15),
    ) {

        companion object {

            @JvmStatic
            fun defaults(): ImageStyle = configuredDefaultStyle()

        }

        fun resolveFontFallbacks(): ImageStyle {
            val resolvedTitle = ImageTextUtils.FontFallback.resolveNormal(titleFont.style, titleFont.size, titleFont)
            val resolvedHeading =
                ImageTextUtils.FontFallback.resolveNormal(headingFont.style, headingFont.size, headingFont)
            val resolvedSubtitle =
                ImageTextUtils.FontFallback.resolveNormal(subtitleFont.style, subtitleFont.size, subtitleFont)
            val resolvedBody = ImageTextUtils.FontFallback.resolveNormal(bodyFont.style, bodyFont.size, bodyFont)
            val resolvedLabel = ImageTextUtils.FontFallback.resolveNormal(labelFont.style, labelFont.size, labelFont)
            val resolvedCode = ImageTextUtils.FontFallback.resolveCode(codeFont.style, codeFont.size, codeFont)
            return copy(
                titleFont = resolvedTitle,
                headingFont = resolvedHeading,
                subtitleFont = resolvedSubtitle,
                bodyFont = resolvedBody,
                labelFont = resolvedLabel,
                codeFont = resolvedCode
            )
        }
    }

    data class RenderedImage(
        val filename: String,
        val bytes: ByteArray,
        val mime: String
    )

    private data class PageModel(
        val header: HeaderModel,
        val sections: List<SectionModel>
    )

    private data class HeaderModel(
        var title: String,
        val descriptionLines: MutableList<String> = ArrayList(),
        val aliases: MutableList<String> = ArrayList()
    )

    private data class SectionModel(
        val title: String,
        val items: MutableList<ItemModel> = ArrayList()
    )

    private data class ItemModel(
        val kind: UsageTextRenderer.LineKind,
        val text: String
    )

    private data class HeaderLayout(
        val titleLines: List<String>,
        val descriptionRows: List<List<String>>,
        val aliasRows: List<List<String>>,
        val height: Int
    )

    private data class SectionsLayout(
        val placements: List<SectionPlacement>,
        val totalHeight: Int
    )

    private data class SectionPlacement(
        val layout: SectionLayout,
        val x: Int,
        val y: Int
    )

    private data class SectionLayout(
        val title: String,
        val width: Int,
        val height: Int,
        val items: List<PlacedItem>
    )

    private data class PlacedItem(
        val item: MeasuredItem,
        val x: Int,
        val y: Int,
        val width: Int,
        val boxHeight: Int
    )

    private data class MeasuredItem(
        val kind: UsageTextRenderer.LineKind,
        val text: String,
        val lines: List<String>,
        val head: String?,
        val height: Int,
        val boxWidth: Int
    )

}
