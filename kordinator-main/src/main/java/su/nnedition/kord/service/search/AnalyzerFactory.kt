package su.nnedition.kord.service.search

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.cjk.CJKWidthFilterFactory
import org.apache.lucene.analysis.core.KeywordTokenizerFactory
import org.apache.lucene.analysis.core.LowerCaseFilterFactory
import org.apache.lucene.analysis.core.StopFilterFactory
import org.apache.lucene.analysis.custom.CustomAnalyzer
import org.apache.lucene.analysis.custom.CustomAnalyzer.Builder
import org.apache.lucene.analysis.en.EnglishPossessiveFilterFactory
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.pattern.PatternReplaceFilterFactory
import org.apache.lucene.analysis.standard.StandardTokenizerFactory
import java.io.IOException

/** Provides analyzers used to index media and analyze search queries. */
class AnalyzerFactory {
    private var analyzer: Analyzer? = null
    private var queryAnalyzer: Analyzer? = null

    @Throws(IOException::class)
    private fun addTokenFilterForUnderscoreRemovalAroundToken(builder: Builder) {
        builder
            .addTokenFilter(PatternReplaceFilterFactory::class.java, "pattern", "^\\_", "replacement", "", "replace", "all")
            .addTokenFilter(PatternReplaceFilterFactory::class.java, "pattern", "\\_$", "replacement", "", "replace", "all")
    }

    @Throws(IOException::class)
    private fun addTokenFilterForTokenToDomainValue(builder: Builder) {
        builder
            .addTokenFilter(PatternReplaceFilterFactory::class.java, "pattern", "\\(", "replacement", "", "replace", "all")
            .addTokenFilter(PatternReplaceFilterFactory::class.java, "pattern", "\\)$", "replacement", "", "replace", "all")
            .addTokenFilter(PatternReplaceFilterFactory::class.java, "pattern", "\\)", "replacement", " ", "replace", "all")
            .addTokenFilter(PatternReplaceFilterFactory::class.java, "pattern", "\\{\\}", "replacement", "\\{ \\}", "replace", "all")
            .addTokenFilter(PatternReplaceFilterFactory::class.java, "pattern", "\\[\\]", "replacement", "\\[ \\]", "replace", "all")
    }

    @Throws(IOException::class)
    private fun createDefaultAnalyzerBuilder(): Builder {
        val builder = CustomAnalyzer.builder()
            .withTokenizer(StandardTokenizerFactory::class.java)
            .addTokenFilter(CJKWidthFilterFactory::class.java)
            .addTokenFilter(ASCIIFoldingFilterFactory::class.java, "preserveOriginal", "false")
            .addTokenFilter(LowerCaseFilterFactory::class.java)
            .addTokenFilter(StopFilterFactory::class.java, "words", STOP_WORDS)
            .addTokenFilter(EnglishPossessiveFilterFactory::class.java)
        addTokenFilterForUnderscoreRemovalAroundToken(builder)
        return builder
    }

    @Throws(IOException::class)
    private fun createArtistAnalyzerBuilder(): Builder {
        val builder = CustomAnalyzer.builder()
            .withTokenizer(StandardTokenizerFactory::class.java)
            .addTokenFilter(CJKWidthFilterFactory::class.java)
            .addTokenFilter(ASCIIFoldingFilterFactory::class.java, "preserveOriginal", "false")
            .addTokenFilter(LowerCaseFilterFactory::class.java)
            .addTokenFilter(StopFilterFactory::class.java, "words", STOP_WORDS_ARTIST)
            .addTokenFilter(EnglishPossessiveFilterFactory::class.java)
        addTokenFilterForUnderscoreRemovalAroundToken(builder)
        return builder
    }

    private fun createKeywordAnalyzerBuilder(): Builder = CustomAnalyzer.builder()
        .withTokenizer(KeywordTokenizerFactory::class.java)

    @Throws(IOException::class)
    private fun createGenreAnalyzerBuilder(): Builder = createKeywordAnalyzerBuilder().also {
        addTokenFilterForTokenToDomainValue(it)
    }

    @Throws(IOException::class)
    fun getAnalyzer(): Analyzer {
        analyzer?.let { return it }
        try {
            val defaultAnalyzer = createDefaultAnalyzerBuilder().build()
            val artistAnalyzer = createArtistAnalyzerBuilder().build()
            return PerFieldAnalyzerWrapper(defaultAnalyzer, mapOf(FieldNames.ARTIST to artistAnalyzer))
                .also { analyzer = it }
        } catch (error: IOException) {
            throw IOException("Error when initializing Analyzer.", error)
        }
    }

    @Throws(IOException::class)
    fun getQueryAnalyzer(): Analyzer {
        queryAnalyzer?.let { return it }
        try {
            val defaultAnalyzer = createDefaultAnalyzerBuilder().build()
            val artistAnalyzer = createArtistAnalyzerBuilder().build()
            val genreAnalyzer = createGenreAnalyzerBuilder().build()
            val fieldAnalyzers = mapOf(
                FieldNames.ARTIST to artistAnalyzer,
                FieldNames.GENRE to genreAnalyzer
            )
            return PerFieldAnalyzerWrapper(defaultAnalyzer, fieldAnalyzers).also { queryAnalyzer = it }
        } catch (error: IOException) {
            throw IOException("Error when initializing Analyzer.", error)
        }
    }

    companion object {
        private const val STOP_WORDS = "su/nnedition/kord/service/search/analysis/stopwords.txt"
        private const val STOP_WORDS_ARTIST = "su/nnedition/kord/service/search/analysis/stopwords_artist.txt"
    }
}
