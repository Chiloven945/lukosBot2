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
package top.chiloven.lukosbot2.commands.bot.bilibili.schema

import top.chiloven.lukosbot2.util.StringUtils.firstNonBlank
import top.chiloven.lukosbot2.util.StringUtils.fmtNum
import top.chiloven.lukosbot2.util.StringUtils.truncate
import top.chiloven.lukosbot2.util.TimeUtils.fmtTime
import top.chiloven.lukosbot2.util.TimeUtils.formatTime

data class BilibiliViewDataDto(
    val bvid: String? = null,
    val aid: Long? = null,
    val title: String? = null,
    val tname: String? = null,
    val desc: String? = null,
    val pic: String? = null,
    val pubdate: Long? = null,
    val owner: OwnerDto? = null,
    val stat: StatDto? = null,
    val pages: List<Any>? = null,
    val videos: Int? = null,
) {

    data class OwnerDto(
        val mid: Long? = null,
        val name: String? = null,
    )

    data class StatDto(
        val view: Long? = null,
        val danmaku: Long? = null,
        val reply: Long? = null,
        val favorite: Long? = null,
        val coin: Long? = null,
        val share: Long? = null,
        val like: Long? = null,
    )

}

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

        添加 `-i` 可查看更多信息。
        """.trimIndent()

    fun toDetailedText(): String = buildString(512) {
        append(link).append('\n')
        append("标题：").append(title)
        if (pageCount > 1) append("（").append(pageCount).append("P）")
        append(" | 类型：").append(tname.orUnknown()).append('\n')

        append("UP 主：").append(ownerName.orUnknown())
                .append(" | 粉丝：").append(fans.fmtNum())
                .append('\n')

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

        fun ownerMid(data: BilibiliViewDataDto): Long? = data.owner?.mid

        fun fromViewData(
            data: BilibiliViewDataDto,
            fallbackId: VideoId,
            fans: Long
        ): BilibiliVideo? {
            val owner = data.owner
            val stat = data.stat
            val bvid = firstNonBlank(
                data.bvid,
                (fallbackId as? VideoId.Bv)?.bvid
            ).ifBlank {
                return null
            }

            val ownerMid = owner?.mid ?: 0L

            val sec = data.pubdate ?: 0L
            val pubDateMs = if (sec <= 0L) 0L else sec * 1000L
            val pageCount = maxOf(
                1,
                data.pages?.size ?: 0,
                data.videos ?: 0
            )

            return BilibiliVideo(
                bvid = bvid,
                title = data.title.orEmpty(),
                tname = data.tname,
                desc = data.desc,
                cover = data.pic,
                pubDateMs = pubDateMs,
                ownerName = owner?.name,
                ownerMid = ownerMid,
                fans = fans,
                view = stat?.view ?: 0L,
                danmaku = stat?.danmaku ?: 0L,
                reply = stat?.reply ?: 0L,
                favorite = stat?.favorite ?: 0L,
                coin = stat?.coin ?: 0L,
                share = stat?.share ?: 0L,
                like = stat?.like ?: 0L,
                pageCount = pageCount,
            )
        }

    }

    private fun String?.orUnknown(): String = this?.takeIf {
        it.isNotBlank()
    } ?: "未知"

}
