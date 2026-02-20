package top.chiloven.lukosbot2.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeUtils {
    fun dtf(): DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")

    fun OffsetDateTime.toLDT(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime {
        return this.atZoneSameInstant(zoneId).toLocalDateTime()
    }
}