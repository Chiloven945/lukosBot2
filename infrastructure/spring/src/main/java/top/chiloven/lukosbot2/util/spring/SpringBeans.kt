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
package top.chiloven.lukosbot2.util.spring

import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

/**
 * Utility class to access Spring beans statically.
 */
@Component
object SpringBeans : ApplicationContextAware {

    @Volatile
    private var ctx: ApplicationContext? = null

    /**
     * Set the ApplicationContext for this object.
     *
     * @param applicationContext the ApplicationContext object to be used by this object
     * @throws BeansException if the context could not be set
     */
    @Throws(BeansException::class)
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        ctx = applicationContext
    }

    /**
     * Get a Spring bean by its class type.
     *
     * @param type   the class type of the bean
     * @param T the type of the bean
     * @return the Spring bean instance
     */
    @JvmStatic
    fun <T : Any> getBean(type: Class<T>): T =
        requireNotNull(ctx) { "Spring ApplicationContext has not been initialized yet." }
            .getBean(type)

    /**
     * Get a Spring bean by its class type.
     *
     * @param T the type of the bean
     * @return the Spring bean instance
     */
    inline fun <reified T : Any> getBean(): T = getBean(T::class.java)

}
