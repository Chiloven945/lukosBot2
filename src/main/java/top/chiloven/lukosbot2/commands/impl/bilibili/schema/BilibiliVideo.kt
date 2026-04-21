package top.chiloven.lukosbot2.commands.impl.bilibili.schema

import tools.jackson.databind.node.ObjectNode
import top.chiloven.lukosbot2.util.JsonUtils.arr
import top.chiloven.lukosbot2.util.JsonUtils.int
import top.chiloven.lukosbot2.util.JsonUtils.long
import top.chiloven.lukosbot2.util.JsonUtils.obj
import top.chiloven.lukosbot2.util.JsonUtils.str
import top.chiloven.lukosbot2.util.StringUtils.firstNonBlank
import top.chiloven.lukosbot2.util.StringUtils.fmtNum
import top.chiloven.lukosbot2.util.StringUtils.fmtTime
import top.chiloven.lukosbot2.util.StringUtils.formatTime
import top.chiloven.lukosbot2.util.StringUtils.truncate

data class BilibiliVideo(
    val bvid: String,
    val title: String,
    val tname: String?,
    val desc: String?,
    val cover: String?,
    val pubDateMs: Long,
    val ownerName: String?,
    val ownerMid: Long,
    val fans: Long,
    val view: Long,
    val danmaku: Long,
    val reply: Long,
    val favorite: Long,
    val coin: Long,
    val share: Long,
    val like: Long,
    val pageCount: Int,
) {

    val link: String
        get() = "https://www.bilibili.com/video/$bvid"

    fun toReplyText(detailed: Boolean): String =
        if (detailed) toDetailedText() else toSimpleText()

    fun toSimpleText(): String =
        """
        $link
        标题：$title
        类型：${tname.orUnknown()}
        UP 主：${ownerName.orUnknown()}
        日期：${pubDateMs.fmtTime()}

        追加 -i 以查看更多信息。
        """.trimIndent()

    fun toDetailedText(): String = buildString(512) {
        append(link).append('\n')
        append("标题：").append(title)
        if (pageCount > 1) append("（").append(pageCount).append("P）")
        append(" | 类型：").append(tname.orUnknown()).append('\n')

        append("UP主：").append(ownerName.orUnknown())
            .append(" | 粉丝：").append(fans.fmtNum()).append('\n')

        desc?.takeIf { it.isNotBlank() }?.let {
            append("简介：").append(it.truncate(160)).append('\n')
        }

        append("观看：").append(view.fmtNum())
            .append(" | 弹幕：").append(danmaku.fmtNum())
            .append(" | 评论：").append(reply.fmtNum()).append('\n')

        append("喜欢：").append(like.fmtNum())
            .append(" | 投币：").append(coin.fmtNum())
            .append(" | 收藏：").append(favorite.fmtNum())
            .append(" | 分享：").append(share.fmtNum()).append('\n')

        append("日期：").append(formatTime(pubDateMs))
    }

    companion object {

        fun ownerMid(data: ObjectNode): Long? = data.obj("owner")?.long("mid")

        fun fromViewData(data: ObjectNode, fallbackId: VideoId, fans: Long): BilibiliVideo? {
            val owner = data.obj("owner")
            val stat = data.obj("stat")
            val bvid = firstNonBlank(data.str("bvid"), (fallbackId as? VideoId.Bv)?.bvid).ifBlank { return null }
            val ownerMid = owner?.long("mid") ?: 0L

            return BilibiliVideo(
                bvid = bvid,
                title = data.str("title").orEmpty(),
                tname = data.str("tname"),
                desc = data.str("desc"),
                cover = data.str("pic"),
                pubDateMs = publishDateMs(data),
                ownerName = owner?.str("name"),
                ownerMid = ownerMid,
                fans = fans,
                view = stat?.long("view") ?: 0L,
                danmaku = stat?.long("danmaku") ?: 0L,
                reply = stat?.long("reply") ?: 0L,
                favorite = stat?.long("favorite") ?: 0L,
                coin = stat?.long("coin") ?: 0L,
                share = stat?.long("share") ?: 0L,
                like = stat?.long("like") ?: 0L,
                pageCount = pageCount(data),
            )
        }

        private fun publishDateMs(data: ObjectNode): Long {
            val sec = data.long("pubdate") ?: 0L
            return if (sec <= 0L) 0L else sec * 1000L
        }

        private fun pageCount(data: ObjectNode): Int {
            val byArray = data.arr("pages")?.size() ?: 0
            val byField = data.int("videos") ?: 0
            return maxOf(1, byArray, byField)
        }
    }

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "未知"

}
