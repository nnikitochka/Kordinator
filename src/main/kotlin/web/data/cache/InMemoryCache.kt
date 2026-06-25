package su.nnedition.kord.web.data.cache

import su.nnedition.kord.web.User

object InMemoryCache {
    val users: MutableList<User> = mutableListOf()
    val tokens: MutableMap<Long, MutableList<String>> = mutableMapOf()

    fun createId(): Long = users.size + 1.toLong()
}