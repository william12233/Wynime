@file:Suppress("RemoveRedundantBackticks")

package com.wynime.utils.coroutines

import com.wynime.test.Sample

@Sample
fun `ExceptionCollector_collectOps`() {
    fun method1(): String = error("1")
    fun method2(): String = error("2")

    val result: String = withExceptionCollector {
        try {
            return@withExceptionCollector method1()
        } catch (e: Exception) {
            collect(e)
        }

        try {
            return@withExceptionCollector method2()
        } catch (e: Exception) {
            collect(e)
        }

        throwLast()
    }
}
