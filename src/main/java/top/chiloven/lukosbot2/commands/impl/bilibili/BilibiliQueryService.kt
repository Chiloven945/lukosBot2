package top.chiloven.lukosbot2.commands.impl.bilibili

import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.impl.bilibili.schema.BilibiliVideo

@Service
class BilibiliQueryService(
    private val bilibiliApi: BilibiliApi,
) {

    fun query(target: String): BilibiliVideo? {
        val id = bilibiliApi.resolveVideoId(target) ?: return null
        val viewData = bilibiliApi.getViewData(id) ?: return null
        val ownerMid = BilibiliVideo.ownerMid(viewData)
        val fans = ownerMid?.takeIf { it > 0 }?.let(bilibiliApi::getFollowerCount) ?: 0L
        return BilibiliVideo.fromViewData(viewData, id, fans)
    }

}
