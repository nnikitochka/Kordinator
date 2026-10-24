package org.airsonic.player.service.metadata;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Factory for creating meta-data parsers.
 *
 * @author Sindre Mehus
 */
@Component
public class MetaDataParserFactory {

    private final List<MetaDataParser> parsers;

    public MetaDataParserFactory(List<MetaDataParser> parsers) {
        this.parsers = parsers.stream()
                .sorted(Comparator.comparingInt(MetaDataParser::getOrder))
                .toList();
    }

    /**
     * Returns a meta-data parser for the given file.
     *
     * @param path The file in question.
     * @return An applicable parser, or <code>null</code> if no parser is found.
     */
    public MetaDataParser getParser(Path path) {
        return parsers.stream().filter(parser -> parser.isApplicable(path)).findFirst().orElse(null);
    }
}
