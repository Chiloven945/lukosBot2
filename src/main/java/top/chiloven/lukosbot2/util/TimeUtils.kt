package top.chiloven.lukosbot2.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Date and time related utils.
 *
 * @author Chiloven945
 */
object TimeUtils {

    /** `yyyy/MM/dd HH:mm:ss` */
    fun dtf(): DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")

    /** `yyyy/MM/dd` */
    fun dtfDate(): DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    /** `HH:mm:ss` */
    fun dtfTime(): DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /**
     * Convert [OffsetDateTime] to [LocalDateTime]. This will use the system [ZoneId] by default.
     *
     * @param zoneId the ZoneId will be used for converting
     * @return converted LocalDateTime
     *
     * @see [OffsetDateTime.atZoneSameInstant]
     * @see [java.time.ZonedDateTime.toLocalDateTime]
     */
    fun OffsetDateTime.toLDT(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime {
        return this.atZoneSameInstant(zoneId).toLocalDateTime()
    }

    /**
     * Format current string to [LocalDateTime]. This is another way to call [LocalDateTime.parse].
     *
     * @return parsed [LocalDateTime] using the string
     */
    fun String.toLDT(): LocalDateTime =
        LocalDateTime.parse(this)

    /**
     * Format current [LocalDateTime] using a [DateTimeFormatter]. Will use [TimeUtils.dtf]'s DateTimeFormatter by default.
     *
     * @param dtf the DateTimeFormatter to use for formatting
     * @return formatted LocalDateTime in String
     */
    fun LocalDateTime.fmt(dtf: DateTimeFormatter = dtf()): String = this.format(dtf)

    /**
     * Format current [LocalDateTime] using default [TimeUtils.dtfDate]
     *
     * @return formatted LocalDateTime in String
     */
    fun LocalDateTime.fmtDate(): String = this.format(dtfDate())

    /**
     * Format current [LocalDateTime] using default [TimeUtils.dtfTime]
     *
     * @return formatted LocalDateTime in String
     */
    fun LocalDateTime.fmtTime(): String = this.format(dtfTime())

    /**
     * Format uptime in seconds to d:hh:mm:ss
     *
     * @param seconds uptime in seconds
     * @return formatted uptime string
     */
    fun formatUptime(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "%d:%02d:%02d:%02d".format(days, hours, minutes, secs)
    }

}
