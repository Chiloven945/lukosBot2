package top.chiloven.lukosbot2

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class Main {
    companion object {
        private val log: Logger = LogManager.getLogger(Main::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            log.info(
                """
                Starting lukosBot2 {} ...
                 __       __  __  __  __   _____   ____    ____     _____   ______    ___     
                /\ \     /\ \/\ \/\ \/\ \ /\  __`\/\  _`\ /\  _`\  /\  __`\/\__  _\ /'___`\   
                \ \ \    \ \ \ \ \ \ \/'/'\ \ \/\ \ \,\L\_\ \ \L\ \\ \ \/\ \/_/\ \//\_\ /\ \  
                 \ \ \  __\ \ \ \ \ \ , <  \ \ \ \ \/_\__ \\ \  _ <'\ \ \ \ \ \ \ \\/_/// /__ 
                  \ \ \L\ \\ \ \_\ \ \ \\`\ \ \ \_\ \/\ \L\ \ \ \L\ \\ \ \_\ \ \ \ \  // /_\ \
                   \ \____/ \ \_____\ \_\ \_\\ \_____\ `\____\ \____/ \ \_____\ \ \_\/\______/
                    \/___/   \/_____/\/_/\/_/ \/_____/\/_____/\/___/   \/_____/  \/_/\/_____/ 
                """.trimIndent(), Constants.VERSION
            )
            SpringApplication.run(Main::class.java, *args)
        }
    }
}
