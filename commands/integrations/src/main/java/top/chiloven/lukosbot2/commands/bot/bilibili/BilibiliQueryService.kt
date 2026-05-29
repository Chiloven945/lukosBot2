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
package top.chiloven.lukosbot2.commands.bot.bilibili

import org.springframework.stereotype.Service
import top.chiloven.lukosbot2.commands.bot.bilibili.schema.BilibiliVideo

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
