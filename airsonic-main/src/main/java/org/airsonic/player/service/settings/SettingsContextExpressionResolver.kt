package org.airsonic.player.service.settings

import java.util.function.Supplier
import java.util.regex.Pattern
import org.apache.commons.lang3.StringUtils

/** Resolves indexed context expressions embedded in configured strings. */
internal object SettingsContextExpressionResolver {
    private val expression = Pattern.compile("^(?:\\[['\"]([A-Za-z0-9_]+)['\"]]|([A-Za-z0-9_]+))(?:\\[(\\d+)])?$")

    @JvmStatic
    fun resolve(value: String, contextSupplier: Supplier<Map<String, Any>>): String {
        val expressions = StringUtils.substringsBetween(value, "%{", "}") ?: return value
        if (expressions.isEmpty()) return value

        val context = contextSupplier.get()
        val searchList = expressions.map { "%{$it}" }.toTypedArray()
        val replacements = expressions.map { resolveExpression(it, context) }.toTypedArray()
        return StringUtils.replaceEach(value, searchList, replacements)
    }

    private fun resolveExpression(expressionText: String, context: Map<String, Any>): String {
        val matcher = expression.matcher(expressionText.trim())
        if (!matcher.matches()) {
            throw IllegalArgumentException("Unsupported contextual setting expression: $expressionText")
        }
        val key = matcher.group(1) ?: matcher.group(2)
        var value = context[key]
        val indexText = matcher.group(3)
        if (indexText != null) {
            val index = indexText.toInt()
            value = when (value) {
                is List<*> -> {
                    if (index >= value.size) missingItem(key, index)
                    value[index]
                }
                is Array<*> -> {
                    if (index >= value.size) missingItem(key, index)
                    value[index]
                }
                else -> missingItem(key, index)
            }
        }
        return value?.toString() ?: throw IllegalArgumentException("Context value is missing: $key")
    }

    private fun missingItem(key: String, index: Int): Nothing =
        throw IllegalArgumentException("Context value '$key' has no item at index $index")
}
