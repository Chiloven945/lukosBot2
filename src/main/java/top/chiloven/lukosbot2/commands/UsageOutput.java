package top.chiloven.lukosbot2.commands;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import top.chiloven.lukosbot2.core.command.CommandSource;
import top.chiloven.lukosbot2.model.message.media.BytesRef;
import top.chiloven.lukosbot2.model.message.outbound.OutImage;
import top.chiloven.lukosbot2.model.message.outbound.OutboundMessage;

import java.util.List;

/**
 * Shared output strategy for rendering and sending command usage/help content.
 *
 * <p>This utility centralizes the behavior previously embedded in {@code HelpCommand}:
 * <ul>
 *   <li>Render usage as markdown text.</li>
 *   <li>Decide whether to send text or image based on mode and heuristics.</li>
 *   <li>When using image, render a PNG and send it as an image part (caption used as title when supported).</li>
 *   <li>If image rendering fails, fall back to text output.</li>
 * </ul>
 */
@Log4j2
public final class UsageOutput {

    public static final int AUTO_IMAGE_MAX_CHARS = 1400;
    public static final int AUTO_IMAGE_MAX_LINES = 32;

    private UsageOutput() {
    }

    /**
     * Parse user-specified mode strings.
     *
     * <p>Matches HelpCommand behavior:
     * <ul>
     *   <li>{@code img/image/pic/png/图片/图} -> IMAGE</li>
     *   <li>{@code text/txt/raw/文字} -> TEXT</li>
     *   <li>otherwise -> AUTO</li>
     * </ul>
     *
     * @param modeRaw raw input
     * @return parsed mode
     */
    public static UseMode parseMode(String modeRaw) {
        if (modeRaw == null || modeRaw.isBlank()) return UseMode.AUTO;
        String m = modeRaw.trim().toLowerCase();

        return switch (m) {
            case "img", "image", "pic", "png", "图片", "图" -> UseMode.IMAGE;
            case "text", "txt", "raw", "文字" -> UseMode.TEXT;
            default -> UseMode.AUTO;
        };
    }

    /**
     * Render a {@link UsageNode} and send it to the user as text or image according to the mode.
     */
    public static void sendUsage(
            @NonNull CommandSource src,
            String prefix,
            String cmdNameForTitle,
            @NonNull UsageNode node,
            @NonNull UsageTextRenderer.Options opt,
            @NonNull UsageImageUtils.ImageStyle style,
            UseMode mode
    ) {
        String p = (prefix == null || prefix.isBlank()) ? "/" : prefix.trim();
        String cmdName = (cmdNameForTitle == null) ? "" : cmdNameForTitle.trim();

        UsageTextRenderer.Result rendered = UsageTextRenderer.render(node, opt);

        boolean useImage = switch (mode == null ? UseMode.AUTO : mode) {
            case IMAGE -> true;
            case TEXT -> false;
            case AUTO -> shouldAutoUseImage(rendered);
        };

        if (!useImage) {
            src.reply(rendered.markdownText());
            return;
        }

        try {
            UsageImageUtils.RenderedImage img = UsageImageUtils.renderUsagePng(
                    "usage-" + (cmdName.isEmpty() ? node.getName() : cmdName),
                    node,
                    opt,
                    style
            );

            String title = "命令用法：%s%s".formatted(p, cmdName);
            BytesRef ref = new BytesRef(img.getFilename(), img.getBytes(), img.getMime());

            OutImage part = new OutImage(ref, title, img.getFilename(), img.getMime());
            OutboundMessage out = new OutboundMessage(src.addr(), List.of(part));

            src.reply(out);
        } catch (Exception e) {
            log.warn("Failed to render usage image: {}", e.getMessage(), e);
            src.reply(rendered.markdownText() + "\n\n（图片渲染失败，已回退到文字版：" + e.getMessage() + "）");
        }
    }

    /**
     * Decide whether AUTO mode should use image for the given rendered output.
     *
     * <p>Matches the original heuristic:
     * <ul>
     *   <li>chars > {@link #AUTO_IMAGE_MAX_CHARS}</li>
     *   <li>lines > {@link #AUTO_IMAGE_MAX_LINES}</li>
     * </ul>
     */
    public static boolean shouldAutoUseImage(UsageTextRenderer.Result rendered) {
        if (rendered == null) return false;
        String md = rendered.markdownText();
        int lines = rendered.lines() == null ? 0 : rendered.lines().size();
        int chars = md == null ? 0 : md.length();
        return chars > AUTO_IMAGE_MAX_CHARS || lines > AUTO_IMAGE_MAX_LINES;
    }

    /**
     * Output mode for usage rendering.
     */
    public enum UseMode {
        AUTO,
        TEXT,
        IMAGE
    }

}
