package su.nnedition.kord.domain

/**
 * The outcome of a search.
 */
class ParamSearchResult<T> {
    val items: MutableList<T> = ArrayList()

    var offset: Int = 0
    var totalHits: Int = 0
}
