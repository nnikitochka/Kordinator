package su.nnedition.kord.model

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** A release date with exactly the precision present in the YEAR tag. */
data class ReleaseDate(
    val year: Int,
    val month: Int? = null,
    val day: Int? = null
) {
    init {
        require(year in 1..9999) { "Year must be between 0001 and 9999" }
        require(day == null || month != null) { "A day requires a month" }
        if (month != null) YearMonth.of(year, month)
        if (day != null) LocalDate.of(year, requireNotNull(month), day)
    }

    override fun toString(): String = buildString {
        append(String.format(Locale.ROOT, "%04d", year))
        month?.let { append(String.format(Locale.ROOT, "-%02d", it)) }
        day?.let { append(String.format(Locale.ROOT, "-%02d", it)) }
    }

    companion object {
        private val FORMAT = Regex("[0-9]{4}(?:([-\\.])[0-9]{2}(?:\\1[0-9]{2})?)?")

        fun parse(value: String?): ReleaseDate? {
            val text = value?.trim() ?: return null
            if (!FORMAT.matches(text)) return null
            val parts = text.split('-', '.').map(String::toInt)
            return try {
                ReleaseDate(parts[0], parts.getOrNull(1), parts.getOrNull(2))
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: DateTimeException) {
                null
            }
        }

        fun fromYear(year: Int?): ReleaseDate? = year?.takeIf { it in 1..9999 }?.let(::ReleaseDate)

        /** Vote for each component within the winning parent's group; ties stop precision. */
        fun mostCommon(values: Iterable<ReleaseDate?>): ReleaseDate? {
            val dates = values.filterNotNull()
            val year = winner(dates.map { it.year }) ?: return null
            val inYear = dates.filter { it.year == year }
            val month = winner(inYear.mapNotNull { it.month }) ?: return ReleaseDate(year)
            val day = winner(inYear.filter { it.month == month }.mapNotNull { it.day })
            return ReleaseDate(year, month, day)
        }

        private fun winner(values: List<Int>): Int? {
            val counts = values.groupingBy { it }.eachCount()
            val highest = counts.values.maxOrNull() ?: return null
            return counts.filterValues { it == highest }.keys.singleOrNull()
        }
    }
}
