package org.airsonic.player.dao;

import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Transcoding;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Unit test of {@link TranscodingDao}.
 *
 * @author Sindre Mehus
 */
public class TranscodingDaoTestCase extends DaoTestCase {

    private PlayerDao playerDao;

    @Before
    public void setUp() throws Exception {
        getJdbcHelper().update("delete from transcoding");
        playerDao = database.getRuntime().createPlayerDao();
    }

    @Test
    public void testCreateTranscoding() {
        Transcoding transcoding = new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", false);
        daos().getTranscodingDao().createTranscoding(transcoding);

        Transcoding newTranscoding = daos().getTranscodingDao().getAllTranscodings().get(0);
        assertTranscodingEquals(transcoding, newTranscoding);
    }

    @Test
    public void testUpdateTranscoding() {
        Transcoding transcoding = new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", false);
        daos().getTranscodingDao().createTranscoding(transcoding);
        transcoding = daos().getTranscodingDao().getAllTranscodings().get(0);

        transcoding.setName("newName");
        transcoding.setSourceFormats("newSourceFormats");
        transcoding.setTargetFormat("newTargetFormats");
        transcoding.setStep1("newStep1");
        transcoding.setStep2("newStep2");
        transcoding.setStep3("newStep3");
        transcoding.setDefaultActive(true);
        daos().getTranscodingDao().updateTranscoding(transcoding);

        Transcoding newTranscoding = daos().getTranscodingDao().getAllTranscodings().get(0);
        assertTranscodingEquals(transcoding, newTranscoding);
    }

    @Test
    public void testDeleteTranscoding() {
        assertEquals("Wrong number of transcodings.", 0, daos().getTranscodingDao().getAllTranscodings().size());

        daos().getTranscodingDao().createTranscoding(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", true));
        assertEquals("Wrong number of transcodings.", 1, daos().getTranscodingDao().getAllTranscodings().size());

        daos().getTranscodingDao().createTranscoding(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", true));
        assertEquals("Wrong number of transcodings.", 2, daos().getTranscodingDao().getAllTranscodings().size());

        daos().getTranscodingDao().deleteTranscoding(daos().getTranscodingDao().getAllTranscodings().get(0).getId());
        assertEquals("Wrong number of transcodings.", 1, daos().getTranscodingDao().getAllTranscodings().size());

        daos().getTranscodingDao().deleteTranscoding(daos().getTranscodingDao().getAllTranscodings().get(0).getId());
        assertEquals("Wrong number of transcodings.", 0, daos().getTranscodingDao().getAllTranscodings().size());
    }

    @Test
    public void testPlayerTranscoding() {
        Player player = new Player();
        playerDao.createPlayer(player);

        daos().getTranscodingDao().createTranscoding(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", false));
        daos().getTranscodingDao().createTranscoding(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", false));
        daos().getTranscodingDao().createTranscoding(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", false));
        Transcoding transcodingA = daos().getTranscodingDao().getAllTranscodings().get(0);
        Transcoding transcodingB = daos().getTranscodingDao().getAllTranscodings().get(1);
        Transcoding transcodingC = daos().getTranscodingDao().getAllTranscodings().get(2);

        List<Transcoding> activeTranscodings = daos().getTranscodingDao().getTranscodingsForPlayer(player.getId());
        assertEquals("Wrong number of transcodings.", 0, activeTranscodings.size());

        daos().getTranscodingDao().setTranscodingsForPlayer(player.getId(), new int[]{transcodingA.getId()});
        activeTranscodings = daos().getTranscodingDao().getTranscodingsForPlayer(player.getId());
        assertEquals("Wrong number of transcodings.", 1, activeTranscodings.size());
        assertTranscodingEquals(transcodingA, activeTranscodings.get(0));

        daos().getTranscodingDao().setTranscodingsForPlayer(player.getId(), new int[]{transcodingB.getId(), transcodingC.getId()});
        activeTranscodings = daos().getTranscodingDao().getTranscodingsForPlayer(player.getId());
        assertEquals("Wrong number of transcodings.", 2, activeTranscodings.size());
        assertTranscodingEquals(transcodingB, activeTranscodings.get(0));
        assertTranscodingEquals(transcodingC, activeTranscodings.get(1));

        daos().getTranscodingDao().setTranscodingsForPlayer(player.getId(), new int[0]);
        activeTranscodings = daos().getTranscodingDao().getTranscodingsForPlayer(player.getId());
        assertEquals("Wrong number of transcodings.", 0, activeTranscodings.size());
    }

    @Test
    public void testCascadingDeletePlayer() {
        Player player = new Player();
        playerDao.createPlayer(player);

        daos().getTranscodingDao().createTranscoding(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", true));
        Transcoding transcoding = daos().getTranscodingDao().getAllTranscodings().get(0);

        daos().getTranscodingDao().setTranscodingsForPlayer(player.getId(), new int[]{transcoding.getId()});
        List<Transcoding> activeTranscodings = daos().getTranscodingDao().getTranscodingsForPlayer(player.getId());
        assertEquals("Wrong number of transcodings.", 1, activeTranscodings.size());

        playerDao.deletePlayer(player.getId());
        activeTranscodings = daos().getTranscodingDao().getTranscodingsForPlayer(player.getId());
        assertEquals("Wrong number of transcodings.", 0, activeTranscodings.size());
    }

    @Test
    public void testCascadingDeleteTranscoding() {
        Player player = new Player();
        playerDao.createPlayer(player);

        daos().getTranscodingDao().createTranscoding(new Transcoding(null, "name", "sourceFormats", "targetFormat", "step1", "step2", "step3", true));
        Transcoding transcoding = daos().getTranscodingDao().getAllTranscodings().get(0);

        daos().getTranscodingDao().setTranscodingsForPlayer(player.getId(), new int[]{transcoding.getId()});
        List<Transcoding> activeTranscodings = daos().getTranscodingDao().getTranscodingsForPlayer(player.getId());
        assertEquals("Wrong number of transcodings.", 1, activeTranscodings.size());

        daos().getTranscodingDao().deleteTranscoding(transcoding.getId());
        activeTranscodings = daos().getTranscodingDao().getTranscodingsForPlayer(player.getId());
        assertEquals("Wrong number of transcodings.", 0, activeTranscodings.size());
    }

    private void assertTranscodingEquals(Transcoding expected, Transcoding actual) {
        assertEquals("Wrong name.", expected.getName(), actual.getName());
        assertEquals("Wrong source formats.", expected.getSourceFormats(), actual.getSourceFormats());
        assertEquals("Wrong target format.", expected.getTargetFormat(), actual.getTargetFormat());
        assertEquals("Wrong step 1.", expected.getStep1(), actual.getStep1());
        assertEquals("Wrong step 2.", expected.getStep2(), actual.getStep2());
        assertEquals("Wrong step 3.", expected.getStep3(), actual.getStep3());
        assertEquals("Wrong default active.", expected.isDefaultActive(), actual.isDefaultActive());
    }
}
