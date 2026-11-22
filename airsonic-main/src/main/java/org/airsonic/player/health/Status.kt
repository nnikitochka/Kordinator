package org.airsonic.player.health

import java.util.Locale
import java.util.Objects

/** Framework-neutral health status descriptor. */
class Status @JvmOverloads constructor(code: String, description: String? = "") : Comparable<Status> {
    val code: String = Objects.requireNonNull(code, "code").uppercase(Locale.getDefault())
    val description: String = description ?: ""

    override fun compareTo(other: Status): Int = code.compareTo(other.code)

    override fun equals(other: Any?): Boolean = other is Status && code == other.code

    override fun hashCode(): Int = code.hashCode()

    override fun toString(): String = code

    companion object {
        @JvmField
        val UNKNOWN = Status("UNKNOWN")

        @JvmField
        val UP = Status("UP")

        @JvmField
        val DOWN = Status("DOWN")

        @JvmField
        val OUT_OF_SERVICE = Status("OUT_OF_SERVICE")
    }
}
