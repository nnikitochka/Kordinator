package org.airsonic.player.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Function

class LambdaUtilsTest {
    @Test
    fun adaptersReturnValuesAndWrapCheckedAndRuntimeExceptions() {
        val checked = IOException("broken")
        val supplier = LambdaUtils.uncheckSupplier(LambdaUtils.ThrowingSupplier<String, IOException> { throw checked })
        assertSame(checked, assertThrows(RuntimeException::class.java) { supplier.get() }.cause)
        val runtime = IllegalArgumentException("invalid")
        val consumer = LambdaUtils.uncheckConsumer(LambdaUtils.ThrowingConsumer<Int, Exception> { throw runtime })
        assertSame(runtime, assertThrows(RuntimeException::class.java) { consumer.accept(1) }.cause)
        val function = LambdaUtils.uncheckFunction(LambdaUtils.ThrowingFunction<Int, String, Exception> { "value=$it" })
        assertEquals("value=3", function.apply(3))
        val biFunction = LambdaUtils.uncheckBiFunction(LambdaUtils.ThrowingBiFunction<Int, Int, Int, Exception> { a, b -> a + b })
        assertEquals(5, biFunction.apply(2, 3))
        var result = 0
        LambdaUtils.uncheckBiConsumer(LambdaUtils.ThrowingBiConsumer<Int, Int, Exception> { a, b -> result = a + b }).accept(2, 4)
        assertEquals(6, result)
    }

    @Test
    fun distinctKeysRemainUniqueAcrossConcurrentCallers() {
        val predicate = LambdaUtils.distinctByKey(Function<Int, Int> { it % 10 })
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 100).map { value -> executor.submit<Boolean> { predicate.test(value) } }
            assertEquals(10, tasks.count { it.get(5, TimeUnit.SECONDS) })
        } finally {
            executor.shutdownNow()
        }
        val nullKey = LambdaUtils.distinctByKey(Function<Int, Any?> { null })
        assertThrows(NullPointerException::class.java) { nullKey.test(1) }
    }
}
