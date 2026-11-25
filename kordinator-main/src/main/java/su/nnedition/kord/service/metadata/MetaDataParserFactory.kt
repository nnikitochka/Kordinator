package su.nnedition.kord.service.metadata

import java.nio.file.Path

/**
 * Factory for creating meta-data parsers.
 */
class MetaDataParserFactory(
    parsers: MutableList<MetaDataParser>
) {
    private val parsers: List<MetaDataParser> = parsers.stream()
        .sorted(Comparator.comparingInt { it.getOrder() })
        .toList()

    /**
     * Returns a meta-data parser for the given file.
     * 
     * @param path The file in question.
     * @return An applicable parser, or `null` if no parser is found.
     */
    fun getParser(path: Path?): MetaDataParser? {
        return parsers.stream()
            .filter { parser -> parser.isApplicable(path!!) }
            .findFirst().orElse(null)
    }
}
