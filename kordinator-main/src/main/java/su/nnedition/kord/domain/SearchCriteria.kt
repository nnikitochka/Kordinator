package su.nnedition.kord.domain

import com.google.common.base.MoreObjects

/** Defines criteria used when searching. */
class SearchCriteria {
    var query: String? = null
    var offset: Int = 0
    var count: Int = 0

    override fun toString(): String = MoreObjects.toStringHelper(this)
        .add("query", query)
        .add("offset", offset)
        .add("count", count)
        .toString()
}
