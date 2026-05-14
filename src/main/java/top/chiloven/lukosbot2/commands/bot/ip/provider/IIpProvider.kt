package top.chiloven.lukosbot2.commands.bot.ip.provider

import top.chiloven.lukosbot2.commands.bot.ip.IpData

interface IIpProvider {

    fun id(): String

    fun aliases(): Set<String> = emptySet()

    fun priority(): Int

    @Throws(Exception::class)
    fun query(ip: String): IpData

}
