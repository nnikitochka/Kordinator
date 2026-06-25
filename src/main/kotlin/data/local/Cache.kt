package su.nnedition.kord.data.local

import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

class Cache<T> {
    private val cache: MutableMap<Long, T> = ConcurrentHashMap()

    fun put(id: Long, data: Flow<T>) {

    }
}