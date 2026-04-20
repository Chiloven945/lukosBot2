package top.chiloven.lukosbot2.commands.impl.kemono

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import okhttp3.Request
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import top.chiloven.lukosbot2.Constants
import top.chiloven.lukosbot2.commands.IBotCommand
import top.chiloven.lukosbot2.commands.UsageNode
import top.chiloven.lukosbot2.commands.impl.kemono.schema.Creator
import top.chiloven.lukosbot2.commands.impl.kemono.schema.HashSearchFile
import top.chiloven.lukosbot2.commands.impl.kemono.schema.Post
import top.chiloven.lukosbot2.commands.impl.kemono.schema.Service
import top.chiloven.lukosbot2.config.AppProperties
import top.chiloven.lukosbot2.config.ProxyConfigProp
import top.chiloven.lukosbot2.core.command.CommandSource
import top.chiloven.lukosbot2.model.message.inbound.InFile
import top.chiloven.lukosbot2.model.message.inbound.InImage
import top.chiloven.lukosbot2.model.message.media.BytesRef
import top.chiloven.lukosbot2.model.message.media.MediaRef
import top.chiloven.lukosbot2.model.message.media.PlatformFileRef
import top.chiloven.lukosbot2.model.message.media.UrlRef
import top.chiloven.lukosbot2.util.*
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.JsonUtils.str
import top.chiloven.lukosbot2.util.PathUtils.sanitizeFileName
import top.chiloven.lukosbot2.util.PathUtils.sanitizePathSegment
import top.chiloven.lukosbot2.util.PathUtils.withTempDirectory
import top.chiloven.lukosbot2.util.StringUtils.isUrl
import top.chiloven.lukosbot2.util.brigadier.builder.CommandLAB.literal
import top.chiloven.lukosbot2.util.brigadier.builder.CommandRAB.argument
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Command for fetching information from kemono.cr.
 *
 * @author Chiloven945
 */
@org.springframework.stereotype.Service
@ConditionalOnProperty(
    prefix = "lukos.commands.control",
    name = ["kemono"],
    havingValue = "true",
    matchIfMissing = true
)
class KemonoCommand(
    private val appProperties: AppProperties,
    private val proxyConfigProp: ProxyConfigProp,
) : IBotCommand {

    private companion object {

        private const val ARCHIVE_DOWNLOAD_TIMEOUT_MS: Int = 3_000_000
        private const val ARCHIVE_MIME_TYPE: String = "application/zip"
        private val ARCHIVE_HEADERS: Map<String, String> = mapOf("User-Agent" to Constants.UA)

    }

    private val log = LogManager.getLogger(KemonoCommand::class.java)

    private val clientCache = OkHttpUtils.ProxyAwareOkHttpClientCache(
        connectTimeoutMs = TimeUnit.SECONDS.toMillis(20),
        readTimeoutMs = TimeUnit.MINUTES.toMillis(5),
        callTimeoutMs = TimeUnit.MINUTES.toMillis(10),
        proxyProvider = { proxyConfigProp },
    )

    private val okHttp
        get() = clientCache.client

    override fun name(): String = "kemono"

    override fun description(): String = "从 kemono.cr 获取 post / creator 信息并支持打包下载"

    override fun usage(): UsageNode {
        return UsageNode.root(name())
            .description(description())
            .subcommand("post", "查询 post 信息，支持 sha256 与上传文件反查") { b ->
                b.syntax(
                    "通过 post URL 查询",
                    UsageNode.arg("post_url"),
                    UsageNode.opt(UsageNode.lit("-t")),
                    UsageNode.opt(UsageNode.lit("-a")),
                )
                    .syntax(
                        "通过 kemono 上的 service / creator_id / post_id 查询",
                        UsageNode.arg("service"),
                        UsageNode.arg("creator_id"),
                        UsageNode.arg("post_id"),
                        UsageNode.opt(UsageNode.lit("-t")),
                        UsageNode.opt(UsageNode.lit("-a")),
                    )
                    .syntax(
                        "通过原站 post id 解析到 kemono post",
                        UsageNode.arg("service"),
                        UsageNode.arg("platform_post_id"),
                        UsageNode.opt(UsageNode.lit("-t")),
                        UsageNode.opt(UsageNode.lit("-a")),
                    )
                    .syntax(
                        "通过文件 sha256 查询帖子",
                        UsageNode.arg("sha_256"),
                        UsageNode.opt(UsageNode.lit("-t")),
                        UsageNode.opt(UsageNode.lit("-a")),
                    )
                    .syntax(
                        "发送命令时直接附带文件 / 图片，自动计算 sha256 后查询",
                        UsageNode.opt(UsageNode.lit("-t")),
                        UsageNode.opt(UsageNode.lit("-a")),
                    )
                    .param("post_url", "kemono post 链接，或原站 post 链接")
                    .param("service", "平台名，如 patreon / fanbox / fantia / afdian / boosty")
                    .param("creator_id", "kemono creator id")
                    .param("post_id", "kemono post id")
                    .param("platform_post_id", "原站 post id")
                    .param("sha_256", "文件的 SHA-256 十六进制值")
                    .option("-t", "展示全部附件")
                    .option("-a", "直接打包下载附件")
                    .example(
                        "kemono post https://kemono.cr/patreon/user/123456/post/654321",
                        "kemono post patreon 123456 654321 -t",
                        "kemono post fanbox 9876543 -a",
                        "kemono post 15be29bad5f6010cc16af84731f60a2812fdda0f861fd623f4539a0c61b97d48",
                        "kemono post -a  # 发送命令时同时上传文件或图片"
                    )
            }
            .subcommand("creator", "查询 creator 信息或打包其全部附件") { b ->
                b.syntax(
                    "通过 creator URL 查询",
                    UsageNode.arg("creator_url"),
                    UsageNode.opt(UsageNode.lit("-a")),
                )
                    .syntax(
                        "通过 kemono 上的 service / creator_id 查询",
                        UsageNode.arg("service"),
                        UsageNode.arg("creator_id"),
                        UsageNode.opt(UsageNode.lit("-a")),
                    )
                    .param("creator_url", "kemono creator 链接")
                    .param("service", "平台名，如 patreon / fanbox / fantia / afdian / boosty")
                    .param("creator_id", "kemono creator id")
                    .option("-a", "打包下载该 creator 下所有帖子附件")
                    .example(
                        "kemono creator https://kemono.cr/patreon/user/123456",
                        "kemono creator patreon 123456 -a"
                    )
            }
            .note(
                "`post` 子命令支持直接附带文件 / 图片；若当前平台能读取上传内容，将自动计算 SHA-256 后反查。",
                "`-t` 仅对 `post` 子命令生效，用于展开全部附件；`-a` 会直接开始下载并发送 zip。"
            )
            .build()
    }

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name())
                .executes { ctx ->
                    sendUsage(ctx.source)
                    1
                }
                .then(
                    literal("post")
                        .executes { ctx ->
                            executeSafely(ctx.source) { handlePost("", ctx.source) }
                        }
                        .then(
                            argument("args", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    executeSafely(ctx.source) {
                                        handlePost(StringArgumentType.getString(ctx, "args"), ctx.source)
                                    }
                                }
                        )
                )
                .then(
                    literal("creator")
                        .executes { ctx ->
                            sendUsage(ctx.source)
                            1
                        }
                        .then(
                            argument("args", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    executeSafely(ctx.source) {
                                        handleCreator(StringArgumentType.getString(ctx, "args"), ctx.source)
                                    }
                                }
                        )
                )
        )
    }

    override fun isVisible(): Boolean = false

    private inline fun executeSafely(src: CommandSource, block: () -> Unit): Int {
        return runCatching {
            block()
            1
        }.getOrElse { e ->
            log.warn("Kemono command failed: {}", e.message, e)
            src.reply(friendlyError(e))
            0
        }
    }

    private fun friendlyError(e: Throwable): String {
        val msg = e.message?.trim() ?: ""
        return when {
            msg.startsWith("HTTP 404") -> "资源未找到，请检查链接、ID、SHA-256 或平台参数是否正确。"
            msg.isNotEmpty() -> "发生错误：$msg"
            else -> "发生未知错误。"
        }
    }

    private fun handlePost(rawArgs: String, src: CommandSource) {
        val opts = parseOptions(rawArgs)
        val target = parsePostTarget(opts.positionals, src)

        if (target is PostTarget.ByHash) {
            val hit = HashSearchFile.fromJsonObject(KemonoAPI.getFileFromHash(target.sha256))

            val text = buildString {
                if (target.fromUpload) {
                    appendLine("已从上传文件计算 SHA-256：${target.sha256}")
                    target.uploadName?.let { appendLine("文件名：$it") }
                    appendLine()
                }
                append(hit.getString())
            }.trim()

            src.reply(text)
            return
        }

        val resolved = resolvePostTarget(target)
        val text = buildString {
            resolved.hashHit?.let {
                appendLine(it.getString())
                appendLine()
            }
            resolved.uploadSha?.let { uploadSha ->
                appendLine("已从上传文件计算 SHA-256：$uploadSha")
                resolved.uploadName?.let { appendLine("文件名：$it") }
                appendLine()
            }
            append(resolved.post.getSpecific())
            if (opts.showAllAttachments) {
                appendLine()
                appendLine()
                append(resolved.post.getAttachments())
            }
        }.trim()

        src.reply(text)
    }

    private fun handleCreator(rawArgs: String, src: CommandSource) {
        val opts = parseOptions(rawArgs)
        require(!opts.showAllAttachments) { "`-t` 仅支持 `post` 子命令。" }

        val parsed = parseCreatorTarget(opts.positionals)
        val creator = Creator.fromProfileAndPosts(
            KemonoAPI.getCreatorProfile(parsed.service, parsed.creatorId),
            KemonoAPI.getCreatorPosts(parsed.service, parsed.creatorId)
        )

        if (opts.archive) {
            src.reply(archiveCreator(creator, src))
            return
        }

        src.reply(creator.getString())
    }

    private fun parseOptions(rawArgs: String): ParsedOptions {
        val trimmed = rawArgs.trim()
        val tokens = if (trimmed.isEmpty()) emptyList() else trimmed.split(Regex("\\s+"))

        var showAll = false
        var archive = false
        val positionals = mutableListOf<String>()

        for (token in tokens) {
            when (token) {
                "-t" -> showAll = true
                "-a" -> archive = true
                else -> {
                    require(!token.startsWith("-")) { "未知参数：$token" }
                    positionals += token
                }
            }
        }

        return ParsedOptions(positionals, showAll, archive)
    }

    private fun parsePostTarget(positionals: List<String>, src: CommandSource): PostTarget {
        if (positionals.isEmpty()) {
            val upload = resolveUploadedHash(src)
                ?: throw IllegalArgumentException("参数为空。请提供 post 链接 / ID / SHA-256，或在发送命令时附带一个文件。")
            return PostTarget.ByHash(upload.sha256, upload.name, fromUpload = true)
        }

        return when (positionals.size) {
            1 -> {
                val only = positionals[0]
                when {
                    isSha256(only) -> PostTarget.ByHash(only.lowercase(Locale.ROOT))
                    only.isUrl() -> parsePostUrl(only)
                    else -> throw IllegalArgumentException(
                        "参数格式错误：post <post_url> [-t] [-a] | post <service> <creator_id> <post_id> [-t] [-a] | post <service> <platform_post_id> [-t] [-a] | post <sha_256> [-t] [-a]"
                    )
                }
            }

            2 -> PostTarget.ByServicePost(parseService(positionals[0]), positionals[1])
            3 -> PostTarget.Direct(parseService(positionals[0]), positionals[1], positionals[2])
            else -> throw IllegalArgumentException(
                "参数格式错误：post <post_url> [-t] [-a] | post <service> <creator_id> <post_id> [-t] [-a] | post <service> <platform_post_id> [-t] [-a] | post <sha_256> [-t] [-a]"
            )
        }
    }

    private fun parseCreatorTarget(positionals: List<String>): CreatorTarget {
        require(positionals.isNotEmpty()) { "参数为空。格式：creator <creator_url> [-a] | creator <service> <creator_id> [-a]" }

        return when (positionals.size) {
            1 -> {
                val only = positionals[0]
                require(only.isUrl()) { "参数格式错误：creator <creator_url> [-a] | creator <service> <creator_id> [-a]" }
                parseCreatorUrl(only)
            }

            2 -> CreatorTarget(parseService(positionals[0]), positionals[1])
            else -> throw IllegalArgumentException("参数格式错误：creator <creator_url> [-a] | creator <service> <creator_id> [-a]")
        }
    }

    private fun parsePostUrl(url: String): PostTarget {
        val uri = URI.create(url)
        val host = (uri.host ?: "").lowercase(Locale.ROOT)
        val segments = PathUtils.splitPathSegments(uri.path)

        if (host.endsWith("kemono.cr")) {
            if (segments.size >= 5 && segments[1] == "user" && segments[3] == "post") {
                return PostTarget.Direct(parseService(segments[0]), segments[2], segments[4])
            }
            throw IllegalArgumentException("无法识别的 kemono post 链接：$url")
        }

        val parsed = Service.parseServicePostUrl(uri)
        return PostTarget.ByServicePost(parsed.service, parsed.servicePostId)
    }

    private fun parseCreatorUrl(url: String): CreatorTarget {
        val uri = URI.create(url)
        val host = (uri.host ?: "").lowercase(Locale.ROOT)
        require(host.endsWith("kemono.cr")) { "无法识别的 creator 链接：$url" }

        val segments = PathUtils.splitPathSegments(uri.path)
        if (segments.size >= 3 && segments[1] == "user") {
            return CreatorTarget(parseService(segments[0]), segments[2])
        }

        throw IllegalArgumentException("无法识别的 creator 链接：$url")
    }

    private fun parseService(raw: String): Service {
        return runCatching { Service.getService(raw) }
            .getOrElse { throw IllegalArgumentException("未知的 service：$raw") }
    }

    private fun resolvePostTarget(target: PostTarget): ResolvedPost {
        return when (target) {
            is PostTarget.Direct -> {
                val post = Post.fromSpecificPost(
                    KemonoAPI.getSpecificPost(target.service, target.creatorId, target.postId)
                )
                ResolvedPost(target.service, target.creatorId, target.postId, post)
            }

            is PostTarget.ByServicePost -> {
                val mapping = KemonoAPI.getPostFromServicePost(target.service, target.servicePostId)
                val creatorId = mapping.str("artist_id")!!
                val postId = mapping.str("post_id")!!
                val post = Post.fromSpecificPost(
                    KemonoAPI.getSpecificPost(target.service, creatorId, postId)
                )
                ResolvedPost(target.service, creatorId, postId, post)
            }

            is PostTarget.ByHash -> {
                val hit = HashSearchFile.fromJsonObject(KemonoAPI.getFileFromHash(target.sha256))
                val matchedPost = hit.posts.firstOrNull()
                    ?: throw IllegalArgumentException("这个文件没有关联到任何帖子。")
                val fullPost = Post.fromSpecificPost(
                    KemonoAPI.getSpecificPost(matchedPost.service, matchedPost.user, matchedPost.id)
                )
                ResolvedPost(
                    service = fullPost.service,
                    creatorId = fullPost.user,
                    postId = fullPost.id,
                    post = fullPost,
                    hashHit = hit,
                    uploadSha = if (target.fromUpload) target.sha256 else null,
                    uploadName = target.uploadName
                )
            }
        }
    }

    private fun resolveUploadedHash(src: CommandSource): UploadedHash? {
        val part = src.parts().firstOrNull { it is InFile || it is InImage } ?: return null

        return when (part) {
            is InFile -> UploadedHash(part.name(), sha256OfMedia(part.ref()))
            is InImage -> UploadedHash(part.name(), sha256OfMedia(part.ref()))
            else -> null
        }
    }

    private fun sha256OfMedia(ref: MediaRef): String {
        val sha256 = when (ref) {
            is BytesRef -> ShaUtils.hashSha256ToHex(ref.bytes())
            is UrlRef -> sha256OfRemote(ref.url())
            is PlatformFileRef -> when (ref.platform().lowercase(Locale.ROOT)) {
                "telegram" -> sha256OfRemote(resolveTelegramFileUrl(ref.fileId()))
                "onebot" -> sha256OfOneBotFile(ref.fileId())
                else -> throw IllegalArgumentException("当前平台的上传文件暂不支持自动读取，请直接提供 SHA-256。")
            }
        }

        if (sha256.isEmpty()) {
            throw IllegalArgumentException("无法读取上传文件内容，请直接提供 SHA-256。")
        }
        return sha256
    }

    private fun sha256OfOneBotFile(fileId: String): String {
        val candidate: Path? = runCatching { Path.of(fileId) }.getOrNull()
        if (candidate != null && Files.exists(candidate) && Files.isRegularFile(candidate)) {
            return ShaUtils.hashSha256ToHex(candidate.toString())
        }
        throw IllegalArgumentException("当前 OneBot 附件无法直接读取，请直接提供 SHA-256。")
    }

    private fun resolveTelegramFileUrl(fileId: String): String {
        val token = appProperties.telegram.botToken.trim()
        require(token.isNotEmpty()) { "Telegram 未配置 bot token，无法读取上传文件。" }

        val root = HttpJson.getObject(
            URI.create("https://api.telegram.org/bot$token/getFile"),
            mapOf("file_id" to fileId),
            null
        )
        val filePath = root.obj("result")?.str("file_path")
            ?: throw IOException("Telegram getFile 未返回 file_path")
        return "https://api.telegram.org/file/bot$token/$filePath"
    }

    private fun sha256OfRemote(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", Constants.UA)
            .build()

        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while downloading uploaded file")
            }
            response.body.byteStream().use { input ->
                return ShaUtils.hashSha256ToHex(input.readAllBytes())
            }
        }
    }

    private fun archivePost(resolved: ResolvedPost, src: CommandSource): String {
        val items = resolved.toArchiveItems()
        require(items.isNotEmpty()) { "这个 post 没有可下载的附件。" }

        return createArchiveAndSend(
            archiveNameHint = "post_${resolved.service.id}_${resolved.creatorId}_${resolved.postId}",
            items = items,
            src = src
        )
    }

    private fun archiveCreator(creator: Creator, src: CommandSource): String {
        val items = creator.toArchiveItems()
        require(items.isNotEmpty()) { "这个 creator 没有可下载的附件。" }

        return createArchiveAndSend(
            archiveNameHint = "creator_${creator.service.id}_${creator.id}",
            items = items,
            src = src
        )
    }

    private fun createArchiveAndSend(
        archiveNameHint: String,
        items: List<DownloadUtils.NamedUrl>,
        src: CommandSource,
    ): String = withTempDirectory("kemono-archive-") { workBase ->
        val downloadDir = Files.createDirectories(workBase.resolve("payload"))
        val zipPath = workBase.resolve(buildArchiveFileName(archiveNameHint))

        src.reply("解析完成，正在下载 ${items.size} 个文件……")
        val report = downloadArchiveItems(items, downloadDir)

        src.reply("文件已下载完成，正在打包……")
        CompressUtils.zipDirectory(downloadDir, zipPath)

        val archiveSize = sendArchiveFile(src, zipPath)

        buildString {
            append("打包下载完成：成功下载 ${report.ok()} 个文件，共 ${items.size} 个。")
            append("\n压缩包：${zipPath.fileName}（${StringUtils.fmtBytes(archiveSize)}）")
            if (report.failed().isNotEmpty()) {
                append("\n下载失败：")
                append(report.failed().joinToString("；"))
            }
        }
    }

    private fun ResolvedPost.toArchiveItems(): List<DownloadUtils.NamedUrl> =
        post.attachments.map { item ->
            DownloadUtils.NamedUrl(
                buildArchiveEntryName(service.id, creatorId, postId, item.name),
                URI.create(item.resolvedUrl)
            )
        }

    private fun Creator.toArchiveItems(): List<DownloadUtils.NamedUrl> =
        posts.flatMap { post ->
            post.attachments.map { item ->
                DownloadUtils.NamedUrl(
                    buildArchiveEntryName(service.id, id, post.id, item.name),
                    URI.create(item.resolvedUrl)
                )
            }
        }

    private fun downloadArchiveItems(
        items: List<DownloadUtils.NamedUrl>,
        downloadDir: Path,
    ): DownloadUtils.BatchResult = DownloadUtils.downloadNamedUrlsToDirConcurrent(
        items,
        downloadDir,
        ARCHIVE_HEADERS,
        ARCHIVE_DOWNLOAD_TIMEOUT_MS
    )

    private fun sendArchiveFile(src: CommandSource, zipPath: Path): Long {
        val bytes = Files.readAllBytes(zipPath)
        src.replyFile(
            BytesRef(zipPath.fileName.toString(), bytes, ARCHIVE_MIME_TYPE),
            zipPath.fileName.toString(),
            null
        )
        return bytes.size.toLong()
    }

    private fun buildArchiveEntryName(
        serviceId: String,
        creatorId: String,
        postId: String,
        fileName: String
    ): String {
        return listOf(
            sanitizePathSegment(serviceId),
            sanitizePathSegment(creatorId),
            sanitizePathSegment(postId),
            sanitizeFileName(fileName)
        ).joinToString("/")
    }

    private fun buildArchiveFileName(hint: String): String {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        return "${sanitizeFileName(hint)}_${ts}_$suffix.zip"
    }

    private fun isSha256(value: String): Boolean {
        return value.matches(Regex("^[0-9a-fA-F]{64}$"))
    }

    private data class ParsedOptions(
        val positionals: List<String>,
        val showAllAttachments: Boolean,
        val archive: Boolean,
    )

    private data class CreatorTarget(
        val service: Service,
        val creatorId: String,
    )

    private sealed interface PostTarget {

        data class Direct(
            val service: Service,
            val creatorId: String,
            val postId: String,
        ) : PostTarget

        data class ByServicePost(
            val service: Service,
            val servicePostId: String,
        ) : PostTarget

        data class ByHash(
            val sha256: String,
            val uploadName: String? = null,
            val fromUpload: Boolean = false,
        ) : PostTarget

    }

    private data class UploadedHash(
        val name: String?,
        val sha256: String,
    )

    private data class ResolvedPost(
        val service: Service,
        val creatorId: String,
        val postId: String,
        val post: Post,
        val hashHit: HashSearchFile? = null,
        val uploadSha: String? = null,
        val uploadName: String? = null,
    )

}
