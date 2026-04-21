package top.chiloven.lukosbot2.commands.impl.bilibili.schema

sealed class VideoId {

    abstract fun normalized(): String

    data class Bv(
        val bvid: String
    ) : VideoId() {

        override fun normalized(): String = bvid

    }

    data class Av(
        val aid: Long
    ) : VideoId() {

        override fun normalized(): String = "av$aid"

    }

    companion object {

        private val bvPattern = Regex("""(?i)\bBV([0-9A-Za-z]{10})\b""")
        private val avPattern = Regex("""(?i)\bAV?(\d+)\b""")

        fun parse(input: String): VideoId? {
            bvPattern.find(input)?.groupValues?.getOrNull(1)?.let { return Bv("BV$it") }
            avPattern.find(input)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return Av(it) }
            return null
        }

    }

}
