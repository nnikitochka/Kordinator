package su.nnedition.kord.util

import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier

/** Adapts operations that throw checked exceptions to Java functional interfaces. */
object LambdaUtils {
    fun interface ThrowingConsumer<T, E : Exception> { @Throws(Exception::class) fun accept(value: T) }
    fun interface ThrowingFunction<T, S, E : Exception> { @Throws(Exception::class) fun apply(value: T): S }
    fun interface ThrowingBiFunction<T, R, S, E : Exception> { @Throws(Exception::class) fun apply(first: T, second: R): S }
    fun interface ThrowingBiConsumer<T, R, E : Exception> { @Throws(Exception::class) fun accept(first: T, second: R) }
    fun interface ThrowingSupplier<T, E : Exception> { @Throws(Exception::class) fun get(): T }

    @JvmStatic fun <T, E : Exception> uncheckSupplier(supplier: ThrowingSupplier<T, E>): Supplier<T> = Supplier {
        try { supplier.get() } catch (failure: Exception) { throw RuntimeException(failure) }
    }

    @JvmStatic fun <T, E : Exception> uncheckConsumer(consumer: ThrowingConsumer<T, E>): Consumer<T> = Consumer { value ->
        try { consumer.accept(value) } catch (failure: Exception) { throw RuntimeException(failure) }
    }

    @JvmStatic fun <T, R, E : Exception> uncheckBiConsumer(consumer: ThrowingBiConsumer<T, R, E>): BiConsumer<T, R> = BiConsumer { first, second ->
        try { consumer.accept(first, second) } catch (failure: Exception) { throw RuntimeException(failure) }
    }

    @JvmStatic fun <T, S, E : Exception> uncheckFunction(function: ThrowingFunction<T, S, E>): Function<T, S> = Function { value ->
        try { function.apply(value) } catch (failure: Exception) { throw RuntimeException(failure) }
    }

    @JvmStatic fun <T, R, S, E : Exception> uncheckBiFunction(function: ThrowingBiFunction<T, R, S, E>): BiFunction<T, R, S> = BiFunction { first, second ->
        try { function.apply(first, second) } catch (failure: Exception) { throw RuntimeException(failure) }
    }

    @JvmStatic fun <T> distinctByKey(keyExtractor: Function<in T, *>): Predicate<T> {
        val seen = ConcurrentHashMap.newKeySet<Any>()
        return Predicate { value -> seen.add(keyExtractor.apply(value)!!) }
    }
}
