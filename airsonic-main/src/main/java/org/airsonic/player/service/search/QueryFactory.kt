package org.airsonic.player.service.search

import org.airsonic.player.domain.MediaFile.MediaType
import org.airsonic.player.domain.MusicFolder
import org.airsonic.player.domain.RandomSearchCriteria
import org.airsonic.player.domain.SearchCriteria
import org.apache.commons.lang3.StringUtils.isEmpty
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.IntPoint
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.WildcardQuery
import java.io.IOException
import java.util.ArrayList
import java.util.function.BiFunction
import java.util.function.Function

/** Builds Lucene queries for searching the indexed media library. */
class QueryFactory(private val analyzerFactory: AnalyzerFactory) {
    private val toFolderIdQuery = Function<MusicFolder, Query> { folder ->
        TermQuery(Term(FieldNames.FOLDER_ID, folder.id.toString()))
    }

    private val toFolderPathQuery = Function<MusicFolder, Query> { folder ->
        TermQuery(Term(FieldNames.FOLDER, folder.path.toString()))
    }

    private val toFolderQuery = BiFunction<Boolean, List<MusicFolder>, Query> { isId3, folders ->
        val builder = BooleanQuery.Builder()
        folders.stream()
            .map(if (isId3) toFolderIdQuery else toFolderPathQuery)
            .forEach { builder.add(it, Occur.SHOULD) }
        builder.build()
    }

    private fun createMultiFieldWildQuery(fieldNames: Array<String>, queryString: String, indexType: IndexType): Query {
        val mainQuery = BooleanQuery.Builder()
        val fieldsQueries = ArrayList<List<Query>>()
        val analyzer: Analyzer = analyzerFactory.queryAnalyzer

        for (fieldName in fieldNames) {
            analyzer.tokenStream(fieldName, queryString).use { stream ->
                stream.reset()
                val fieldQueries = ArrayList<Query>()
                while (stream.incrementToken()) {
                    val token = stream.getAttribute(CharTermAttribute::class.java).toString()
                    val wildcardQuery = WildcardQuery(Term(fieldName, token + ASTERISK))
                    val boost = indexType.getBoosts()[fieldName]
                    fieldQueries.add(if (boost != null) BoostQuery(wildcardQuery, boost) else wildcardQuery)
                }
                fieldsQueries.add(fieldQueries)
            }
        }

        val maxTermLength = fieldsQueries.map { it.size }.maxOrNull()!!
        if (fieldsQueries.isNotEmpty()) {
            for (index in 0 until maxTermLength) {
                val fieldsQuery = BooleanQuery.Builder()
                for (fieldQueries in fieldsQueries) {
                    if (index < fieldQueries.size) fieldsQuery.add(fieldQueries[index], Occur.SHOULD)
                }
                mainQuery.add(fieldsQuery.build(), Occur.SHOULD)
            }
        }
        return mainQuery.build()
    }

    private val toYearRangeQuery = BiFunction<Int?, Int?, Query> { from, to ->
        IntPoint.newRangeQuery(FieldNames.YEAR, from ?: Int.MIN_VALUE, to ?: Int.MAX_VALUE)
    }

    @Throws(IOException::class)
    fun search(criteria: SearchCriteria, musicFolders: List<MusicFolder>, indexType: IndexType): Query {
        val mainQuery = BooleanQuery.Builder()
        mainQuery.add(createMultiFieldWildQuery(indexType.getFields(), criteria.query!!, indexType), Occur.MUST)
        val isId3 = indexType == IndexType.ALBUM_ID3 || indexType == IndexType.ARTIST_ID3
        mainQuery.add(toFolderQuery.apply(isId3, musicFolders), Occur.MUST)
        return mainQuery.build()
    }

    @Throws(IOException::class)
    fun getRandomSongs(criteria: RandomSearchCriteria): Query {
        val query = BooleanQuery.Builder()
        val analyzer = analyzerFactory.queryAnalyzer
        query.add(TermQuery(Term(FieldNames.MEDIA_TYPE, MediaType.MUSIC.name)), Occur.MUST)

        if (!isEmpty(criteria.genre)) {
            analyzer.tokenStream(FieldNames.GENRE, criteria.genre).use { stream ->
                stream.reset()
                if (stream.incrementToken()) {
                    val token = stream.getAttribute(CharTermAttribute::class.java).toString()
                    query.add(TermQuery(Term(FieldNames.GENRE, token)), Occur.MUST)
                }
            }
        }
        if (criteria.fromYear != null || criteria.toYear != null) {
            query.add(toYearRangeQuery.apply(criteria.fromYear, criteria.toYear), Occur.MUST)
        }
        query.add(toFolderQuery.apply(false, criteria.musicFolders!!), Occur.MUST)
        return query.build()
    }

    @Throws(IOException::class)
    fun searchByName(fieldName: String, name: String): Query {
        val mainQuery = BooleanQuery.Builder()
        val analyzer = analyzerFactory.queryAnalyzer
        analyzer.tokenStream(fieldName, name).use { stream ->
            stream.reset()
            stream.incrementToken()
            while (true) {
                val token = stream.getAttribute(CharTermAttribute::class.java).toString()
                if (stream.incrementToken()) {
                    mainQuery.add(TermQuery(Term(fieldName, token)), Occur.SHOULD)
                } else {
                    mainQuery.add(WildcardQuery(Term(fieldName, token + ASTERISK)), Occur.SHOULD)
                    break
                }
            }
        }
        return mainQuery.build()
    }

    fun getRandomAlbums(musicFolders: List<MusicFolder>): Query = BooleanQuery.Builder()
        .add(toFolderQuery.apply(false, musicFolders), Occur.SHOULD)
        .build()

    fun getRandomAlbumsId3(musicFolders: List<MusicFolder>): Query = BooleanQuery.Builder()
        .add(toFolderQuery.apply(true, musicFolders), Occur.SHOULD)
        .build()

    companion object {
        private const val ASTERISK = "*"
    }
}
