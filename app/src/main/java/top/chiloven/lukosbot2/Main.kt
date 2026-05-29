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
package top.chiloven.lukosbot2

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import top.chiloven.lukosbot2.core.IApplicationControl
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

        internal fun getArgs(): Array<String> = startupArgs.copyOf()
    }

    @Bean
    fun applicationControl(): IApplicationControl = object : IApplicationControl {

        override fun restart() {
            log.info("Restarting lukosBot2...")
            context.close()
            Thread.sleep(750)
            context = SpringApplication.run(Main::class.java, *getArgs())
            log.info("lukosBot2 restarted successfully.")
        }

        override fun shutdown() {
            log.info("Started to shut down lukosBot2...")
            log.info("Shutting down SpringBoot...")
            val exitCode = SpringApplication.exit(context, { 0 })

            log.info("Finished shutting down. Now exiting lukosBot2 and JVM... Goodbye!")
            exitProcess(exitCode)
        }

    }

}
