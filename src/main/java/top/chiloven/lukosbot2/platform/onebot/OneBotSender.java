package top.chiloven.lukosbot2.platform.onebot;

import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import top.chiloven.lukosbot2.model.message.media.BytesRef;
import top.chiloven.lukosbot2.model.message.media.MediaRef;
import top.chiloven.lukosbot2.model.message.media.PlatformFileRef;
import top.chiloven.lukosbot2.model.message.media.UrlRef;
import top.chiloven.lukosbot2.model.message.outbound.*;
import top.chiloven.lukosbot2.platform.ISender;
import top.chiloven.lukosbot2.util.PathUtils;
import top.chiloven.lukosbot2.util.message.OutboundPartUtils;

import java.util.*;

/**
 * OneBot sender for {@link OutboundMessage}.
 *
 * <p>OneBot supports mixed CQ segments (text + image) in a single message. For files,
 * this implementation uses the standard upload APIs when the file is referenced by URL.</p>
 *
 * <p>Notes:</p>
 * <ul>
 *   <li>Image bytes ({@link BytesRef}) are sent using base64 CQ format: {@code base64://...}.</li>
 *   <li>File bytes upload is not supported here (requires platform-specific hosting); falls back to text.</li>
 * </ul>
 */
public final class OneBotSender implements ISender {

    private final BotContainer botContainer;

    public OneBotSender(BotContainer botContainer) {
        this.botContainer = botContainer;
    }

    @Override
    public void send(OutboundMessage out) {
        Bot bot = pickBot();
        if (bot == null || out == null) return;

        List<OutPart> parts = out.parts() == null ? List.of() : out.parts();
        if (parts.isEmpty()) return;

        MsgUtils builder = MsgUtils.builder();
        List<OutFile> fileUploads = new ArrayList<>();

        for (OutPart p : parts) {
            switch (p) {
                case OutText(String text1) -> {
                    String text = OutboundPartUtils.safeText(text1);
                    if (!text.isBlank()) builder.text(text);
                }
                case OutImage img -> {
                    String caption = OutboundPartUtils.safeText(img.caption());
                    if (!caption.isBlank()) builder.text(caption);

                    String imgRef = toOneBotImageRef(img.ref());
                    if (imgRef != null) builder.img(imgRef);
                    else builder.text("[image unsupported]");
                }
                case OutFile f -> {
                    String caption = OutboundPartUtils.safeText(f.caption());
                    if (!caption.isBlank()) builder.text(caption);

                    if (f.ref() instanceof UrlRef) {
                        fileUploads.add(f);
                        builder.text("[file] " + ((UrlRef) f.ref()).url());
                    } else if (f.ref() instanceof BytesRef) {
                        // Not supported: OneBot upload APIs generally expect URL.
                        builder.text("[file bytes unsupported]");
                    } else if (f.ref() instanceof PlatformFileRef(String platform, String fileId)) {
                        builder.text("[file ref] " + platform + ":" + fileId);
                    }
                }
                case null, default -> {
                }
            }

        }

        String message = builder.build();
        if (out.addr().group()) {
            if (!message.isBlank()) bot.sendGroupMsg(out.addr().chatId(), message, false);
        } else {
            if (!message.isBlank()) bot.sendPrivateMsg(out.addr().chatId(), message, false);
        }

        // Upload files referenced by URL (optional)
        for (OutFile f : fileUploads) {
            UrlRef u = (UrlRef) f.ref();
            Map<String, Object> params = new HashMap<>();
            params.put("name", OutboundPartUtils.pickMediaName(f.name(), PathUtils.inferFileNameFromUrl(u.url()), null, false));
            params.put("url", u.url());

            if (out.addr().group()) {
                params.put("group_id", out.addr().chatId());
                bot.customRequest(() -> "upload_group_file", params);
            } else {
                params.put("user_id", out.addr().chatId());
                bot.customRequest(() -> "upload_private_file", params);
            }
        }
    }

    /**
     * Pick first bot from container.
     */
    private Bot pickBot() {
        if (botContainer == null || botContainer.robots == null || botContainer.robots.isEmpty()) {
            return null;
        }
        return botContainer.robots.values().stream().findFirst().orElse(null);
    }

    private static String toOneBotImageRef(MediaRef ref) {
        switch (ref) {
            case null -> {
                return null;
            }
            case UrlRef(String url) -> {
                return url;
            }
            case BytesRef b -> {
                String base64 = Base64.getEncoder().encodeToString(b.bytes());
                return "base64://" + base64;
            }
            case PlatformFileRef p -> {
                // OneBot image segment file=... may support local fileId on some implementations,
                // but there is no universal mapping; fall back to text.
                return null;
            }
            default -> {
            }
        }

        return null;
    }

}
