/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package top.chiloven.lukosbot2.commands

import top.chiloven.lukosbot2.util.ImageTextUtils
import top.chiloven.lukosbot2.util.ModernImageDraw
import top.chiloven.lukosbot2.util.PathUtils
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object UsageImageUtils {

    private val bulletSeparators = listOf(" — ", " – ", " - ", ": ", "：")

    @JvmStatic
    fun renderUsagePng(
        filenameBase: String,
        node: UsageNode,
        options: UsageTextRenderer.Options,
        style: ImageStyle
    ): RenderedImage = renderLinesPng(
        filenameBase = filenameBase,
        lines = UsageTextRenderer.render(node, options).lines(),
        style0 = style
    )

    @JvmStatic
    fun renderLinesPng(
        filenameBase: String,
        lines: List<UsageTextRenderer.RenderedLine>?,
        style0: ImageStyle
    ): RenderedImage {
        val style = style0.resolveFontFallbacks()
        val palette = style.palette

        val filename = PathUtils
                .sanitizeFileName(
                    filenameBase,
                    fallback = "usage",
                    maxLength = 64
                )
                .let { "$it.png" }

        val model = parse(lines.orEmpty())

        /*
        There is currently no external target width such as requestedWidth, therefore, the actual
        rendered width is maxWidth.

        The original max(minWidth, maxWidth) is always equal to maxWidth under normal configuration,
        and cannot truly reflect the constraint relationship between min/max.
        */
        val width = style.maxWidth
        val contentWidth = width - style.padding * 2

        val cache = ImageTextUtils.GlyphRunCache()

        val probe = BufferedImage(
            10,
            10,
            BufferedImage.TYPE_INT_ARGB
        )

        val probeGraphics = probe.createGraphics()

        val headerLayout: HeaderLayout
        val sectionsLayout: SectionsLayout

        try {
            ModernImageDraw.quality(probeGraphics)

            headerLayout = measureHeader(
                g = probeGraphics,
                header = model.header,
                width = contentWidth,
                cache = cache,
                style = style
            )

            val flowLayout = layoutSections(
                g = probeGraphics,
                sections = model.sections,
                contentWidth = contentWidth,
                headerHeight = headerLayout.height,
                cache = cache,
                style = style
            )

            val singleColumnLayout = layoutSingleColumnSections(
                g = probeGraphics,
                sections = model.sections,
                contentWidth = contentWidth,
                headerHeight = headerLayout.height,
                cache = cache,
                style = style
            )

            sectionsLayout = chooseSectionsLayout(
                sections = model.sections,
                contentWidth = contentWidth,
                flowLayout = flowLayout,
                singleColumnLayout = singleColumnLayout,
                style = style
            )
        } finally {
            probeGraphics.dispose()
        }

        val height = sectionsLayout.totalHeight
                .coerceAtLeast(style.minHeight)

        val image = BufferedImage(
            width,
            height,
            BufferedImage.TYPE_INT_ARGB
        )

        val g = image.createGraphics()

        try {
            ModernImageDraw.quality(g)
            ModernImageDraw.background(
                g,
                width,
                height,
                palette
            )

            drawHeader(
                g = g,
                header = model.header,
                layout = headerLayout,
                x = style.padding,
                y = style.padding,
                width = contentWidth,
                cache = cache,
                style = style
            )

            for ((layout, x, y) in sectionsLayout.placements) {
                drawSection(
                    g = g,
                    layout = layout,
                    x = x,
                    y = y,
                    cache = cache,
                    style = style
                )
            }
        } finally {
            g.dispose()
        }

        return try {
            ByteArrayOutputStream().use { output ->
                ImageIO.write(image, "png", output)

                RenderedImage(
                    filename = filename,
                    bytes = output.toByteArray(),
                    mime = "image/png"
                )
            }
        } catch (e: Exception) {
            throw RuntimeException(
                "Render usage PNG failed: ${e.message}",
                e
            )
        }
    }

    private fun parse(
        lines: List<UsageTextRenderer.RenderedLine>
    ): PageModel {
        val header = HeaderModel(title = "命令帮助")
        val sections = mutableListOf<SectionModel>()

        var currentSection: SectionModel? = null

        for ((kind, _, plain) in lines) {
            val text = plain.trim()

            if (
                kind == UsageTextRenderer.LineKind.BLANK ||
                text.isBlank()
            ) {
                continue
            }

            when (kind) {
                UsageTextRenderer.LineKind.TITLE -> {
                    header.title = text
                }

                UsageTextRenderer.LineKind.SUBTITLE -> {
                    if (looksLikeAliasLine(text)) {
                        header.aliases += parseAliases(text)
                    } else {
                        header.descriptionLines += text
                    }
                }

                UsageTextRenderer.LineKind.HEADING -> {
                    currentSection = SectionModel(
                        title = cleanHeading(text)
                    ).also {
                        sections += it
                    }
                }

                UsageTextRenderer.LineKind.TEXT -> {
                    val section = currentSection

                    if (section == null) {
                        header.descriptionLines += text
                    } else {
                        section.items += ItemModel(
                            kind = kind,
                            text = text
                        )
                    }
                }

                UsageTextRenderer.LineKind.LABEL,
                UsageTextRenderer.LineKind.CODE,
                UsageTextRenderer.LineKind.BULLET -> {
                    currentSection?.items?.add(
                        ItemModel(
                            kind = kind,
                            text = text
                        )
                    )
                }

                UsageTextRenderer.LineKind.BLANK -> Unit
            }
        }

        return PageModel(
            header = header,
            sections = sections
        )
    }

    private fun measureHeader(
        g: Graphics2D,
        header: HeaderModel,
        width: Int,
        cache: ImageTextUtils.GlyphRunCache,
        style: ImageStyle
    ): HeaderLayout {
        val descriptionRows = mutableListOf<List<String>>()

        val innerWidth = width - style.cardPadding * 2

        var contentHeight = 0

        contentHeight += pillHeight(
            g,
            style.labelFont
        )

        contentHeight += style.headerBadgeGap

        val titleLines = ImageTextUtils.wrapTextRunAware(
            g,
            header.title,
            style.titleFont,
            style.bodyFont,
            cache,
            innerWidth
        ).ifEmpty {
            listOf(header.title)
        }

        val titleLineHeight = textLineHeight(
            g,
            style.titleFont,
            1.10f
        )

        contentHeight += titleLines.size * titleLineHeight

        if (header.descriptionLines.isNotEmpty()) {
            contentHeight += style.headerTextGap

            val descriptionLineHeight = textLineHeight(
                g,
                style.subtitleFont,
                1.30f
            )

            for (description in header.descriptionLines) {
                val wrapped = ImageTextUtils.wrapTextRunAware(
                    g,
                    description,
                    style.subtitleFont,
                    style.bodyFont,
                    cache,
                    innerWidth
                ).ifEmpty {
                    listOf(description)
                }

                descriptionRows += wrapped

                contentHeight += wrapped.size * descriptionLineHeight
                contentHeight += style.paragraphGap
            }

            contentHeight -= style.paragraphGap
        }

        val aliasRows = layoutPills(
            g = g,
            pills = header.aliases,
            font = style.labelFont,
            maxWidth = innerWidth,
            style = style
        )

        if (aliasRows.isNotEmpty()) {
            contentHeight += style.aliasGap

            contentHeight += aliasRows.size *
                    pillHeight(g, style.labelFont)

            if (aliasRows.size > 1) {
                contentHeight +=
                    (aliasRows.size - 1) * style.pillRowGap
            }
        }

        return HeaderLayout(
            titleLines = titleLines,
            descriptionRows = descriptionRows,
            aliasRows = aliasRows,
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
            return SectionsLayout(
                placements = emptyList(),
                totalHeight =
                    style.padding +
                            headerHeight +
                            style.padding
            )
        }

        val placements = mutableListOf<SectionPlacement>()

        val startY =
            style.padding +
                    headerHeight +
                    style.sectionGap

        val allowTwoColumns =
            contentWidth >= style.multiColumnSectionMinWidth

        /*
         * 不允许双栏时直接按照完整 contentWidth 布局。
         *
         * 原实现会先按照 halfWidth 测量，再作为 fullWidth section
         * 放进去，导致窄画布下卡片实际只有半宽。
         */
        if (!allowTwoColumns) {
            var cursorY = startY

            for (section in sections) {
                val layout = measureSection(
                    g = g,
                    section = section,
                    width = contentWidth,
                    cache = cache,
                    style = style
                )

                placements += SectionPlacement(
                    layout = layout,
                    x = style.padding,
                    y = cursorY
                )

                cursorY +=
                    layout.height +
                            style.sectionGap
            }

            return SectionsLayout(
                placements = placements,
                totalHeight =
                    cursorY -
                            style.sectionGap +
                            style.padding
            )
        }

        val halfWidth =
            (contentWidth - style.sectionGap) / 2

        val leftX = style.padding

        val rightX =
            style.padding +
                    halfWidth +
                    style.sectionGap

        var leftY = startY
        var rightY = startY

        for (section in sections) {
            val narrowLayout = measureSection(
                g = g,
                section = section,
                width = halfWidth,
                cache = cache,
                style = style
            )

            val wideLayout = measureSection(
                g = g,
                section = section,
                width = contentWidth,
                cache = cache,
                style = style
            )

            val fullWidth = shouldSpanFullWidth(
                section = section,
                narrowLayout = narrowLayout,
                wideLayout = wideLayout,
                style = style
            )

            if (fullWidth) {
                val y = maxOf(leftY, rightY)

                placements += SectionPlacement(
                    layout = wideLayout,
                    x = style.padding,
                    y = y
                )

                leftY =
                    y +
                            wideLayout.height +
                            style.sectionGap

                rightY = leftY
            } else if (leftY <= rightY) {
                placements += SectionPlacement(
                    layout = narrowLayout,
                    x = leftX,
                    y = leftY
                )

                leftY +=
                    narrowLayout.height +
                            style.sectionGap
            } else {
                placements += SectionPlacement(
                    layout = narrowLayout,
                    x = rightX,
                    y = rightY
                )

                rightY +=
                    narrowLayout.height +
                            style.sectionGap
            }
        }

        return SectionsLayout(
            placements = placements,
            totalHeight =
                maxOf(leftY, rightY) -
                        style.sectionGap +
                        style.padding
        )
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
            return SectionsLayout(
                placements = emptyList(),
                totalHeight =
                    style.padding +
                            headerHeight +
                            style.padding
            )
        }

        val placements = mutableListOf<SectionPlacement>()

        var cursorY =
            style.padding +
                    headerHeight +
                    style.sectionGap

        for (section in sections) {
            val layout = measureSection(
                g = g,
                section = section,
                width = contentWidth,
                cache = cache,
                style = style
            )

            placements += SectionPlacement(
                layout = layout,
                x = style.padding,
                y = cursorY
            )

            cursorY +=
                layout.height +
                        style.sectionGap
        }

        return SectionsLayout(
            placements = placements,
            totalHeight =
                cursorY -
                        style.sectionGap +
                        style.padding
        )
    }

    private fun chooseSectionsLayout(
        sections: List<SectionModel>,
        contentWidth: Int,
        flowLayout: SectionsLayout,
        singleColumnLayout: SectionsLayout,
        style: ImageStyle
    ): SectionsLayout {
        if (sections.isEmpty()) {
            return flowLayout
        }

        val flowAspectRatio =
            flowLayout.totalHeight.toDouble() /
                    contentWidth

        val preferSingleColumn =
            sections.size <=
                    style.preferredSingleColumnMaxSections &&
                    flowAspectRatio <=
                    style.preferredSingleColumnAspectRatioThreshold

        return if (preferSingleColumn) {
            singleColumnLayout
        } else {
            flowLayout
        }
    }

    private fun measureSection(
        g: Graphics2D,
        section: SectionModel,
        width: Int,
        cache: ImageTextUtils.GlyphRunCache,
        style: ImageStyle
    ): SectionLayout {
        val innerWidth =
            width - style.cardPadding * 2

        val titleHeight = textLineHeight(
            g,
            style.headingFont,
            1.0f
        )

        val useItemGrid = shouldUseItemGrid(
            section = section,
            innerWidth = innerWidth,
            style = style
        )

        val columnWidth =
            if (useItemGrid) {
                (innerWidth - style.gridGap) / 2
            } else {
                innerWidth
            }

        val placedItems = mutableListOf<PlacedItem>()

        var cursorY =
            style.cardPadding +
                    titleHeight

        if (section.items.isNotEmpty()) {
            cursorY += style.sectionTitleGap
        }

        var index = 0

        while (index < section.items.size) {
            val current = section.items[index]

            when {
                !useItemGrid ||
                        current.kind != UsageTextRenderer.LineKind.BULLET -> {
                    val measured = measureItem(
                        g = g,
                        item = current,
                        width = innerWidth,
                        cache = cache,
                        style = style
                    )

                    placedItems += PlacedItem(
                        item = measured,
                        x = 0,
                        y = cursorY,
                        width = measured.boxWidth,
                        boxHeight = measured.height
                    )

                    cursorY +=
                        measured.height +
                                style.itemGap

                    index++
                }

                index + 1 < section.items.size &&
                        section.items[index + 1].kind ==
                        UsageTextRenderer.LineKind.BULLET -> {
                    val left = measureItem(
                        g = g,
                        item = current,
                        width = columnWidth,
                        cache = cache,
                        style = style
                    )

                    val right = measureItem(
                        g = g,
                        item = section.items[index + 1],
                        width = columnWidth,
                        cache = cache,
                        style = style
                    )

                    val rowHeight = maxOf(
                        left.height,
                        right.height
                    )

                    placedItems += PlacedItem(
                        item = left,
                        x = 0,
                        y = cursorY,
                        width = columnWidth,
                        boxHeight = rowHeight
                    )

                    placedItems += PlacedItem(
                        item = right,
                        x = columnWidth + style.gridGap,
                        y = cursorY,
                        width = columnWidth,
                        boxHeight = rowHeight
                    )

                    cursorY +=
                        rowHeight +
                                style.itemGap

                    index += 2
                }

                else -> {
                    val measured = measureItem(
                        g = g,
                        item = current,
                        width = innerWidth,
                        cache = cache,
                        style = style
                    )

                    placedItems += PlacedItem(
                        item = measured,
                        x = 0,
                        y = cursorY,
                        width = measured.boxWidth,
                        boxHeight = measured.height
                    )

                    cursorY +=
                        measured.height +
                                style.itemGap

                    index++
                }
            }
        }

        if (placedItems.isNotEmpty()) {
            cursorY -= style.itemGap
        }

        return SectionLayout(
            title = section.title,
            width = width,
            height = cursorY + style.cardPadding,
            items = placedItems
        )
    }

    private fun measureItem(
        g: Graphics2D,
        item: ItemModel,
        width: Int,
        cache: ImageTextUtils.GlyphRunCache,
        style: ImageStyle
    ): MeasuredItem =
        when (item.kind) {
            UsageTextRenderer.LineKind.CODE -> {
                val lineHeight = textLineHeight(
                    g,
                    style.codeFont,
                    1.12f
                )

                val maxBoxWidth =
                    width.coerceAtMost(
                        style.codeBlockMaxWidth
                    )

                val maxTextWidth =
                    (maxBoxWidth - style.codePaddingX * 2)
                            .coerceAtLeast(1)

                val lines = ImageTextUtils.wrapTextRunAware(
                    g,
                    item.text,
                    style.codeFont,
                    style.bodyFont,
                    cache,
                    maxTextWidth
                ).ifEmpty {
                    listOf(item.text)
                }

                val measuredTextWidth =
                    lines.maxOfOrNull { line ->
                        ImageTextUtils.measureTextRunAware(
                            g,
                            line,
                            style.codeFont,
                            style.bodyFont,
                            cache
                        )
                    } ?: 0

                val naturalBoxWidth =
                    (measuredTextWidth +
                            style.codePaddingX * 2)
                            .coerceAtMost(maxBoxWidth)

                MeasuredItem(
                    kind = item.kind,
                    text = item.text,
                    lines = lines,
                    head = null,
                    height =
                        style.codePaddingY * 2 +
                                lines.size * lineHeight,
                    boxWidth = naturalBoxWidth
                )
            }

            UsageTextRenderer.LineKind.LABEL -> {
                MeasuredItem(
                    kind = item.kind,
                    text = sanitizeChipText(item.text),
                    lines = listOf(item.text),
                    head = null,
                    height = pillHeight(
                        g,
                        style.labelFont
                    ),
                    boxWidth = width
                )
            }

            UsageTextRenderer.LineKind.BULLET -> {
                val raw = item.text
                        .removePrefix("•")
                        .trim()

                val split = splitBullet(raw)

                if (split != null) {
                    val (head, body) = split

                    val bodyLines =
                        if (body.isBlank()) {
                            emptyList()
                        } else {
                            ImageTextUtils.wrapTextRunAware(
                                g,
                                body,
                                style.bodyFont,
                                style.bodyFont,
                                cache,
                                (
                                        width -
                                                style.bulletBoxPadding * 2
                                        ).coerceAtLeast(1)
                            ).ifEmpty {
                                listOf(body)
                            }
                        }

                    val bodyLineHeight =
                        textLineHeight(
                            g,
                            style.bodyFont,
                            1.26f
                        )

                    val top =
                        style.bulletBoxPadding * 2 +
                                pillHeight(
                                    g,
                                    style.labelFont
                                )

                    val bodyHeight =
                        if (bodyLines.isEmpty()) {
                            0
                        } else {
                            style.bulletBodyGap +
                                    bodyLines.size *
                                    bodyLineHeight
                        }

                    MeasuredItem(
                        kind = item.kind,
                        text = raw,
                        lines = bodyLines,
                        head = sanitizeChipText(head),
                        height = top + bodyHeight,
                        boxWidth = width
                    )
                } else {
                    val lineHeight =
                        textLineHeight(
                            g,
                            style.bodyFont,
                            1.26f
                        )

                    val sanitized =
                        sanitizeInlineBulletText(raw)

                    val textWidth =
                        (
                                width -
                                        style.bulletBoxPadding * 2 -
                                        style.bulletDotGap -
                                        style.bulletDotSize
                                ).coerceAtLeast(1)

                    val lines =
                        ImageTextUtils.wrapTextRunAware(
                            g,
                            sanitized,
                            style.bodyFont,
                            style.bodyFont,
                            cache,
                            textWidth
                        ).ifEmpty {
                            listOf(sanitized)
                        }

                    MeasuredItem(
                        kind = item.kind,
                        text = raw,
                        lines = lines,
                        head = null,
                        height =
                            style.bulletBoxPadding * 2 +
                                    lines.size * lineHeight,
                        boxWidth = width
                    )
                }
            }

            else -> {
                val lineHeight =
                    textLineHeight(
                        g,
                        style.bodyFont,
                        1.26f
                    )

                val lines =
                    ImageTextUtils.wrapTextRunAware(
                        g,
                        item.text,
                        style.bodyFont,
                        style.bodyFont,
                        cache,
                        width.coerceAtLeast(1)
                    ).ifEmpty {
                        listOf(item.text)
                    }

                MeasuredItem(
                    kind = item.kind,
                    text = item.text,
                    lines = lines,
                    head = null,
                    height = lines.size * lineHeight,
                    boxWidth = width
                )
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

        ModernImageDraw.card(
            g,
            x,
            y,
            width,
            layout.height,
            style.cardRadius,
            palette
        )

        var cursorY =
            y +
                    style.cardPadding

        val contentX =
            x +
                    style.cardPadding

        ModernImageDraw.pill(
            g,
            "COMMAND HELP",
            contentX,
            cursorY,
            style.labelFont,
            palette.secondaryText,
            palette.pillBg
        )

        cursorY +=
            pillHeight(
                g,
                style.labelFont
            ) +
                    style.headerBadgeGap

        val titleLineHeight =
            textLineHeight(
                g,
                style.titleFont,
                1.10f
            )

        g.color = palette.text

        for (line in layout.titleLines) {
            val baseline =
                cursorY +
                        ImageTextUtils.ascent(
                            g,
                            style.titleFont
                        )

            ImageTextUtils.drawStringWithFallback(
                g,
                line,
                contentX,
                baseline,
                style.titleFont,
                style.bodyFont,
                cache
            )

            cursorY += titleLineHeight
        }

        if (layout.descriptionRows.isNotEmpty()) {
            cursorY += style.headerTextGap

            val descriptionLineHeight =
                textLineHeight(
                    g,
                    style.subtitleFont,
                    1.30f
                )

            g.color = palette.secondaryText

            for (row in layout.descriptionRows) {
                for (line in row) {
                    val baseline =
                        cursorY +
                                ImageTextUtils.ascent(
                                    g,
                                    style.subtitleFont
                                )

                    ImageTextUtils.drawStringWithFallback(
                        g,
                        line,
                        contentX,
                        baseline,
                        style.subtitleFont,
                        style.bodyFont,
                        cache
                    )

                    cursorY += descriptionLineHeight
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
                    val usedWidth =
                        ModernImageDraw.pill(
                            g,
                            alias,
                            cursorX,
                            cursorY,
                            style.labelFont,
                            palette.secondaryText,
                            palette.chipBg
                        )

                    cursorX +=
                        usedWidth +
                                style.pillGap
                }

                cursorY +=
                    pillHeight(
                        g,
                        style.labelFont
                    ) +
                            style.pillRowGap
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

        ModernImageDraw.card(
            g,
            x,
            y,
            layout.width,
            layout.height,
            style.cardRadius,
            palette
        )

        val titleX =
            x +
                    style.cardPadding

        val titleY =
            y +
                    style.cardPadding

        val barHeight =
            (
                    textLineHeight(
                        g,
                        style.headingFont,
                        1.0f
                    ) - 2
                    ).coerceAtLeast(1)

        g.color = palette.accent

        g.fillRoundRect(
            titleX,
            titleY + 1,
            style.sectionBarWidth,
            barHeight,
            style.sectionBarWidth,
            style.sectionBarWidth
        )

        g.color = palette.text

        ImageTextUtils.drawStringWithFallback(
            g,
            layout.title,
            titleX +
                    style.sectionBarWidth +
                    style.sectionBarGap,
            titleY +
                    ImageTextUtils.ascent(
                        g,
                        style.headingFont
                    ),
            style.headingFont,
            style.bodyFont,
            cache
        )

        for ((item, itemX, itemY, width, boxHeight) in layout.items) {
            drawMeasuredItem(
                g = g,
                item = item,
                x = titleX + itemX,
                y = y + itemY,
                width = width,
                boxHeight = boxHeight,
                cache = cache,
                style = style
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
                g.color = palette.codeBg

                g.fillRoundRect(
                    x,
                    y,
                    width,
                    boxHeight,
                    style.codeRadius,
                    style.codeRadius
                )

                var lineY =
                    y +
                            style.codePaddingY

                val lineHeight =
                    textLineHeight(
                        g,
                        style.codeFont,
                        1.12f
                    )

                g.color = palette.codeText

                for (line in item.lines) {
                    val baseline =
                        lineY +
                                ImageTextUtils.ascent(
                                    g,
                                    style.codeFont
                                )

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
                drawChip(
                    g = g,
                    rawText = item.text,
                    x = x,
                    y = y,
                    style = style
                )
            }

            UsageTextRenderer.LineKind.BULLET -> {
                g.color = palette.itemBg

                g.fillRoundRect(
                    x,
                    y,
                    width,
                    boxHeight,
                    style.itemRadius,
                    style.itemRadius
                )

                ModernImageDraw.roundedBorder(
                    g,
                    x,
                    y,
                    width,
                    boxHeight,
                    style.itemRadius,
                    palette.itemBorder
                )

                val head = item.head

                if (head != null) {
                    val pillY =
                        y +
                                style.bulletBoxPadding

                    drawChip(
                        g = g,
                        rawText = head,
                        x = x + style.bulletBoxPadding,
                        y = pillY,
                        style = style
                    )

                    var lineY =
                        pillY +
                                pillHeight(
                                    g,
                                    style.labelFont
                                ) +
                                style.bulletBodyGap

                    val lineHeight =
                        textLineHeight(
                            g,
                            style.bodyFont,
                            1.26f
                        )

                    g.color = palette.secondaryText

                    for (line in item.lines) {
                        val baseline =
                            lineY +
                                    ImageTextUtils.ascent(
                                        g,
                                        style.bodyFont
                                    )

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
                    val dotX =
                        x +
                                style.bulletBoxPadding

                    val dotY =
                        y +
                                style.bulletBoxPadding +
                                7

                    g.color = palette.accent

                    g.fillOval(
                        dotX,
                        dotY,
                        style.bulletDotSize,
                        style.bulletDotSize
                    )

                    var lineY =
                        y +
                                style.bulletBoxPadding

                    val textX =
                        dotX +
                                style.bulletDotSize +
                                style.bulletDotGap

                    val lineHeight =
                        textLineHeight(
                            g,
                            style.bodyFont,
                            1.26f
                        )

                    g.color = palette.secondaryText

                    for (line in item.lines) {
                        val baseline =
                            lineY +
                                    ImageTextUtils.ascent(
                                        g,
                                        style.bodyFont
                                    )

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

                val lineHeight =
                    textLineHeight(
                        g,
                        style.bodyFont,
                        1.26f
                    )

                g.color = palette.secondaryText

                for (line in item.lines) {
                    val baseline =
                        lineY +
                                ImageTextUtils.ascent(
                                    g,
                                    style.bodyFont
                                )

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

    private fun drawChip(
        g: Graphics2D,
        rawText: String,
        x: Int,
        y: Int,
        style: ImageStyle
    ): Int {
        val text = sanitizeChipText(rawText)

        return if (isCodeChip(text)) {
            ModernImageDraw.pill(
                g,
                text,
                x,
                y,
                style.labelFont,
                style.palette.codeText,
                style.palette.codeChipBg
            )
        } else {
            ModernImageDraw.pill(
                g,
                text,
                x,
                y,
                style.labelFont,
                style.palette.accent,
                style.palette.accentChipBg
            )
        }
    }

    private fun sanitizeChipText(
        rawText: String
    ): String =
        rawText
                .trim()
                .removePrefix("•")
                .trim()
                .removePrefix("- ")
                .trim()
                .removeSurrounding("`")
                .trim()

    private fun sanitizeInlineBulletText(
        rawText: String
    ): String =
        rawText
                .replace("`", "")
                .trim()

    private fun isCodeChip(
        text: String
    ): Boolean =
        text.startsWith("/")

    private fun shouldSpanFullWidth(
        section: SectionModel,
        narrowLayout: SectionLayout,
        wideLayout: SectionLayout,
        style: ImageStyle
    ): Boolean {
        val codeCount =
            section.items.count {
                it.kind == UsageTextRenderer.LineKind.CODE
            }

        val heightGain =
            narrowLayout.height -
                    wideLayout.height

        return when (section.title) {
            "参数与选项",
            "提示",
            "子命令" -> false

            "快速使用",
            "示例" -> {
                val narrowFitsWell =
                    narrowLayout.height <=
                            style.preferredColumnSectionMaxHeight &&
                            codeCount <=
                            style.preferredColumnCodeCount

                !narrowFitsWell &&
                        heightGain >=
                        style.fullWidthHeightGainThreshold
            }

            else -> {
                codeCount >= 5 &&
                        heightGain >=
                        style.fullWidthHeightGainThreshold
            }
        }
    }

    private fun shouldUseItemGrid(
        section: SectionModel,
        innerWidth: Int,
        style: ImageStyle
    ): Boolean =
        innerWidth >= style.multiColumnItemMinWidth &&
                section.title in setOf(
            "参数与选项",
            "提示",
            "子命令"
        )

    private fun looksLikeAliasLine(
        text: String
    ): Boolean {
        val trimmed = text.trim()

        return trimmed.startsWith("别名") ||
                trimmed.startsWith(
                    "aliases",
                    ignoreCase = true
                )
    }

    private fun parseAliases(
        text: String
    ): List<String> {
        val raw =
            when {
                "：" in text ->
                    text.substringAfter("：")

                ":" in text ->
                    text.substringAfter(":")

                else ->
                    text.removePrefix("别名")
            }.trim()

        if (raw.isBlank()) {
            return emptyList()
        }

        return raw
                .split(
                    Regex(
                        """\s*[·、,，/|]\s*"""
                    )
                )
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
    }

    private fun cleanHeading(
        text: String
    ): String =
        text
                .trim()
                .removeSuffix("：")
                .removeSuffix(":")

    private fun splitBullet(
        text: String
    ): Pair<String, String>? =
        bulletSeparators.firstNotNullOfOrNull { separator ->
            val index = text.indexOf(separator)

            if (index <= 0) {
                return@firstNotNullOfOrNull null
            }

            val head =
                text
                        .substring(0, index)
                        .trim()

            val body =
                text
                        .substring(index + separator.length)
                        .trim()

            if (head.isBlank()) {
                null
            } else {
                head to body
            }
        }

    private fun layoutPills(
        g: Graphics2D,
        pills: List<String>,
        font: Font,
        maxWidth: Int,
        style: ImageStyle
    ): List<List<String>> {
        if (pills.isEmpty()) {
            return emptyList()
        }

        val rows = mutableListOf<List<String>>()

        var currentRow = mutableListOf<String>()
        var currentWidth = 0

        for (pill in pills) {
            val width =
                pillWidth(
                    g,
                    font,
                    pill
                )

            val requiredWidth =
                width +
                        if (currentRow.isEmpty()) {
                            0
                        } else {
                            style.pillGap
                        }

            if (
                currentRow.isNotEmpty() &&
                currentWidth + requiredWidth > maxWidth
            ) {
                rows += currentRow.toList()

                currentRow = mutableListOf()
                currentWidth = 0
            }

            if (currentRow.isNotEmpty()) {
                currentWidth += style.pillGap
            }

            currentRow += pill
            currentWidth += width
        }

        if (currentRow.isNotEmpty()) {
            rows += currentRow.toList()
        }

        return rows
    }

    private fun textLineHeight(
        g: Graphics2D,
        font: Font,
        multiplier: Float
    ): Int =
        (
                ImageTextUtils.height(
                    g,
                    font
                ) * multiplier
                )
                .toInt()
                .coerceAtLeast(1)

    private fun pillWidth(
        g: Graphics2D,
        font: Font,
        text: String
    ): Int =
        g
                .getFontMetrics(font)
                .stringWidth(text) + 24

    private fun pillHeight(
        g: Graphics2D,
        font: Font
    ): Int =
        g
                .getFontMetrics(font)
                .height + 6

    data class ImageStyle(
        val maxWidth: Int = 1380,
        val minWidth: Int = 1180,
        val minHeight: Int = 260,

        val padding: Int = 28,

        val cardPadding: Int = 26,
        val cardRadius: Int = 24,

        val palette: ModernImageDraw.Palette =
            ModernImageDraw.defaultPalette(),

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

        val preferredSingleColumnAspectRatioThreshold: Double =
            0.75,

        val preferredColumnSectionMaxHeight: Int = 620,
        val preferredColumnCodeCount: Int = 8,
        val fullWidthHeightGainThreshold: Int = 120,

        val titleFont: Font =
            Font(
                "Microsoft Yahei UI",
                Font.BOLD,
                32
            ),

        val headingFont: Font =
            Font(
                "Microsoft Yahei UI",
                Font.BOLD,
                20
            ),

        val subtitleFont: Font =
            Font(
                "Microsoft Yahei UI",
                Font.PLAIN,
                17
            ),

        val bodyFont: Font =
            Font(
                "Microsoft Yahei UI",
                Font.PLAIN,
                16
            ),

        val labelFont: Font =
            Font(
                "Microsoft Yahei UI",
                Font.BOLD,
                13
            ),

        val codeFont: Font =
            Font(
                "Cascadia Code",
                Font.PLAIN,
                15
            )
    ) {

        companion object {

            @JvmStatic
            fun defaults(): ImageStyle =
                ImageStyle()

            @JvmStatic
            fun forTheme(
                mode: ModernImageDraw.ThemeMode
            ): ImageStyle =
                ImageStyle(
                    palette = ModernImageDraw.paletteFor(mode)
                )

            @JvmStatic
            fun forPalette(
                palette: ModernImageDraw.Palette
            ): ImageStyle =
                ImageStyle(
                    palette = palette
                )
        }

        fun resolveFontFallbacks(): ImageStyle {
            fun Font.resolveNormal(): Font =
                ImageTextUtils.FontFallback.resolveNormal(
                    style,
                    size,
                    this
                )

            fun Font.resolveCode(): Font =
                ImageTextUtils.FontFallback.resolveCode(
                    style,
                    size,
                    this
                )

            return copy(
                titleFont = titleFont.resolveNormal(),
                headingFont = headingFont.resolveNormal(),
                subtitleFont = subtitleFont.resolveNormal(),
                bodyFont = bodyFont.resolveNormal(),
                labelFont = labelFont.resolveNormal(),
                codeFont = codeFont.resolveCode()
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
        val descriptionLines: MutableList<String> =
            mutableListOf(),
        val aliases: MutableList<String> =
            mutableListOf()
    )

    private data class SectionModel(
        val title: String,
        val items: MutableList<ItemModel> =
            mutableListOf()
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
