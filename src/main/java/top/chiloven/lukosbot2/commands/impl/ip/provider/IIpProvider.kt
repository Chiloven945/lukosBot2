package top.chiloven.lukosbot2.commands.impl.ip.provider

import top.chiloven.lukosbot2.commands.impl.ip.IpData

interface IIpProvider {

    fun id(): String

    fun aliases(): Set<String> = emptySet()

    fun priority(): Int

    @Throws(Exception::class)
    fun query(ip: String): IpData

}
