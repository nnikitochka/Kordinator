package org.airsonic.player.service.search;

import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.RandomSearchCriteria;
import org.airsonic.player.domain.SearchCriteria;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Factory class of Lucene Query.
 * This class is an extract of the functionality that was once part of SearchService.
 * It is for maintainability and verification.
 * Each corresponds to the SearchService method.
 * The API syntax for query generation depends on the lucene version.
 * verification with query grammar is possible.
 * On the other hand, the generated queries are relatively small by version.
 * Therefore, test cases of this class are useful for large version upgrades.
 **/
@Component
public class QueryFactory {

    private static final String ASTERISK = "*";

    private final AnalyzerFactory analyzerFactory;

    public QueryFactory(AnalyzerFactory analyzerFactory) {
        this.analyzerFactory = analyzerFactory;
    }

    private final Function<MusicFolder, Query> toFolderIdQuery = (folder) -> {
        // Unanalyzed field
        return new TermQuery(new Term(FieldNames.FOLDER_ID, folder.getId().toString()));
    };

    private final Function<MusicFolder, Query> toFolderPathQuery = (folder) -> {
        // Unanalyzed field
        return new TermQuery(new Term(FieldNames.FOLDER, folder.getPath().toString()));
    };

    /*
     *  XXX 3.x -> 8.x :
     *  "SpanOr" has been changed to "Or".
     *   - Path comparison is more appropriate with "Or".
     *   - If "SpanOr" is maintained, the DOC design needs to be changed.
     */
    private final BiFunction<Boolean, List<MusicFolder>, Query> toFolderQuery = (
            isId3, folders) -> {
        BooleanQuery.Builder mfQuery = new BooleanQuery.Builder();
        folders.stream()
            .map(isId3 ? toFolderIdQuery : toFolderPathQuery)
            .forEach(t -> mfQuery.add(t, Occur.SHOULD));
        return mfQuery.build();
    };

    /*
     *  XXX 3.x -> 8.x :
     * In order to support wildcards,
     * MultiFieldQueryParser has been replaced by the following process.
     *
     *  - There is also an override of MultiFieldQueryParser, but it is known to be high cost.
     *  - MultiFieldQueryParser was created before Java API was modernized.
     *  - The spec of Parser has changed from time to time. Using parser does not reduce library update risk.
     *  - Self made parser process reduces one library dependency.
     *  - It is easy to make corrections later when changing the query to improve search accuracy.
     */
    private Query createMultiFieldWildQuery(String[] fieldNames, String queryString, IndexType indexType)
            throws IOException {

        BooleanQuery.Builder mainQuery = new BooleanQuery.Builder();

        List<List<Query>> fieldsQuerys = new ArrayList<>();
        Analyzer analyzer = analyzerFactory.getQueryAnalyzer();

        /* Wildcard applies to all tokens. **/
        for (String fieldName : fieldNames) {
            try (TokenStream stream = analyzer.tokenStream(fieldName, queryString)) {
                stream.reset();
                List<Query> fieldQuerys = new ArrayList<>();
                while (stream.incrementToken()) {
                    String token = stream.getAttribute(CharTermAttribute.class).toString();
                    WildcardQuery wildcardQuery = new WildcardQuery(new Term(fieldName, token.concat(ASTERISK)));
                    if (indexType.getBoosts().containsKey(fieldName)) {
                        fieldQuerys.add(new BoostQuery(wildcardQuery, indexType.getBoosts().get(fieldName)));
                    } else {
                        fieldQuerys.add(wildcardQuery);
                    }
                }
                fieldsQuerys.add(fieldQuerys);
            }
        }

        /* If Field's Tokenizer is different, token's length may not match. **/
        int maxTermLength = fieldsQuerys.stream()
                .map(l -> l.size())
                .max(Integer::compare).get();

        if (0 < fieldsQuerys.size()) {
            for (int i = 0; i < maxTermLength; i++) {
                BooleanQuery.Builder fieldsQuery = new BooleanQuery.Builder();
                for (List<Query> fieldQuerys : fieldsQuerys) {
                    if (i < fieldQuerys.size()) {
                        fieldsQuery.add(fieldQuerys.get(i), Occur.SHOULD);
                    }
                }
                mainQuery.add(fieldsQuery.build(), Occur.SHOULD);
            }
        }

        return mainQuery.build();
    }

    /*
     * XXX 3.x -> 8.x :
     * RangeQuery has been changed to not allow null.
     */
    private final BiFunction<Integer, Integer, Query> toYearRangeQuery =
        (from, to) -> {
            return IntPoint.newRangeQuery(FieldNames.YEAR,
                from == null ? Integer.MIN_VALUE : from,
                to == null ? Integer.MAX_VALUE : to);
        };

    /**
     * Query generation expression extracted from
     * {@link org.airsonic.player.service.SearchService#search(SearchCriteria, List, IndexType)}.
     *
     * @param criteria criteria
     * @param musicFolders musicFolders
     * @param indexType {@link IndexType}
     * @return Query
     * @throws IOException When parsing of MultiFieldQueryParser fails
     */
    public Query search(SearchCriteria criteria, List<MusicFolder> musicFolders,
            IndexType indexType) throws IOException {

        BooleanQuery.Builder mainQuery = new BooleanQuery.Builder();

        Query multiFieldQuery = createMultiFieldWildQuery(indexType.getFields(), criteria.getQuery(), indexType);
        mainQuery.add(multiFieldQuery, Occur.MUST);

        boolean isId3 = indexType == IndexType.ALBUM_ID3 || indexType == IndexType.ARTIST_ID3;
        Query folderQuery = toFolderQuery.apply(isId3, musicFolders);
        mainQuery.add(folderQuery, Occur.MUST);

        return mainQuery.build();
    }

    /**
     * Query generation expression extracted from
     * {@link org.airsonic.player.service.SearchService#getRandomSongs(RandomSearchCriteria)}.
     */
    public Query getRandomSongs(RandomSearchCriteria criteria) throws IOException {

        BooleanQuery.Builder query = new BooleanQuery.Builder();

        Analyzer analyzer = analyzerFactory.getQueryAnalyzer();

        // Unanalyzed field
        query.add(new TermQuery(new Term(FieldNames.MEDIA_TYPE, MediaType.MUSIC.name())), Occur.MUST);

        if (!isEmpty(criteria.getGenre())) {

            // Unanalyzed field, but performs filtering according to id3 tag parser.
            try (TokenStream stream = analyzer.tokenStream(FieldNames.GENRE, criteria.getGenre())) {
                stream.reset();
                if (stream.incrementToken()) {
                    String token = stream.getAttribute(CharTermAttribute.class).toString();
                    query.add(new TermQuery(new Term(FieldNames.GENRE, token)), Occur.MUST);
                }
            }
        }

        if (criteria.getFromYear() != null || criteria.getToYear() != null) {
            query.add(toYearRangeQuery.apply(criteria.getFromYear(), criteria.getToYear()), Occur.MUST);
        }

        query.add(toFolderQuery.apply(false, criteria.getMusicFolders()), Occur.MUST);

        return query.build();
    }

    /**
     * Query generation expression extracted from
     * {@link org.airsonic.player.service.SearchService#searchByName( String, String, int, int, List, Class)}.
     *
     * @param fieldName {@link FieldNames}
     * @return Query
     * @throws IOException When parsing of QueryParser fails
     */
    public Query searchByName(String fieldName, String name) throws IOException {

        BooleanQuery.Builder mainQuery = new BooleanQuery.Builder();

        Analyzer analyzer = analyzerFactory.getQueryAnalyzer();

        try (TokenStream stream = analyzer.tokenStream(fieldName, name)) {
            stream.reset();
            stream.incrementToken();

            /*
             *  XXX 3.x -> 8.x :
             * In order to support wildcards,
             * QueryParser has been replaced by the following process.
             */

            /* Wildcards apply only to tail tokens **/
            while (true) {
                String token = stream.getAttribute(CharTermAttribute.class).toString();
                if (stream.incrementToken()) {
                    mainQuery.add(new TermQuery(new Term(fieldName, token)), Occur.SHOULD);
                } else {
                    WildcardQuery wildcardQuery = new WildcardQuery(new Term(fieldName, token.concat(ASTERISK)));
                    mainQuery.add(wildcardQuery, Occur.SHOULD);
                    break;
                }
            }

        }

        return mainQuery.build();
    }

    /**
     * Query generation expression extracted from
     * {@link org.airsonic.player.service.SearchService#getRandomAlbums(int, List)}.
     *
     * @param musicFolders musicFolders
     * @return Query
     */
    public Query getRandomAlbums(List<MusicFolder> musicFolders) {
        return new BooleanQuery.Builder()
                .add(toFolderQuery.apply(false, musicFolders), Occur.SHOULD)
                .build();
    }

    /**
     * Query generation expression extracted from
     * {@link org.airsonic.player.service.SearchService#getRandomAlbumsId3(int, List)}.
     *
     * @param musicFolders musicFolders
     * @return Query
     */
    public Query getRandomAlbumsId3(List<MusicFolder> musicFolders) {
        return new BooleanQuery.Builder()
                .add(toFolderQuery.apply(true, musicFolders), Occur.SHOULD)
                .build();
    }
}
