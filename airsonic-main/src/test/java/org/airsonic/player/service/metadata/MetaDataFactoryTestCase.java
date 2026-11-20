package org.airsonic.player.service.metadata;

import org.airsonic.player.service.SettingsService;
import org.airsonic.player.runtime.RuntimeDatabaseRule;
import org.airsonic.player.runtime.RuntimeCompositionRoot;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;


public class MetaDataFactoryTestCase {

    @ClassRule
    public static final RuntimeDatabaseRule database = new RuntimeDatabaseRule();

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static Path someMp3;
    private static Path someFlv;
    private static Path someJunk;

    @BeforeClass
    public static void createTestFiles() throws IOException {
        someMp3 = temporaryFolder.newFile("some.mp3").toPath();
        someFlv = temporaryFolder.newFile("some.flv").toPath();
        someJunk = temporaryFolder.newFile("some.junk").toPath();
    }

    RuntimeCompositionRoot runtime;

    SettingsService settingsService;

    @Before
    public void loadMediaServices() {
        runtime = database.getRuntime();
        settingsService = runtime.createSettingsService();
    }

    @Test
    public void testorder() {
        assertParserOrder(runtime.createMetaDataParserFactory());
    }

    @Test
    public void testOrderWhenConstructedWithReversedParsers() {
        List<MetaDataParser> reversed = List.of(
                runtime.createDefaultMetaDataParser(), runtime.createFFmpegParser(), runtime.createJaudiotaggerParser()).stream()
                .sorted(Comparator.comparingInt(MetaDataParser::getOrder).reversed())
                .toList();
        assertParserOrder(new MetaDataParserFactory(reversed));
    }

    private void assertParserOrder(MetaDataParserFactory factory) {
        MetaDataParser parser;

        settingsService.setVideoFileTypes("mp3 flv");

        parser = factory.getParser(someMp3);
        assertThat(parser, instanceOf(JaudiotaggerParser.class));

        parser = factory.getParser(someFlv);
        assertThat(parser, instanceOf(FFmpegParser.class));

        parser = factory.getParser(someJunk);
        assertThat(parser, instanceOf(FFmpegParser.class));
    }

}
