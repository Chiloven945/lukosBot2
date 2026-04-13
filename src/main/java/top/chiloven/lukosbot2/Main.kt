package top.chiloven.lukosbot2

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ConfigurableApplicationContext
import kotlin.system.exitProcess

@SpringBootApplication
class Main {

    companion object {

        private val log: Logger = LogManager.getLogger(Main::class.java)

        lateinit var context: ConfigurableApplicationContext
        private var startupArgs: Array<String> = emptyArray()

        @JvmStatic
        fun main(args: Array<String>) {
            startupArgs = args.copyOf()

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

            context = SpringApplication.run(Main::class.java, *startupArgs)
        }

        @JvmStatic
        fun restart() {
            log.info("Restarting lukosBot2...")
            context.close()
            Thread.sleep(750)
            context = SpringApplication.run(Main::class.java, *startupArgs)
            log.info("lukosBot2 restarted successfully.")
        }

        @JvmStatic
        fun shutdown() {
            log.info("Started to shut down lukosBot2...")
            log.info("Shutting down SpringBoot...")
            val exitCode = SpringApplication.exit(context, { 0 })

            log.info("Finished shutting down. Now exiting lukosBot2 and JVM... Goodbye!")
            exitProcess(exitCode)
        }

    }

}
