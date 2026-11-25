package su.nnedition.kord.service;

import su.nnedition.kord.dao.DatabaseDao;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DatabaseStatusTest {
    @Test
    public void reportsImportFailureAndAllowsLaterOperations() throws Exception {
        SettingsService settings = mock(SettingsService.class);
        DatabaseDao dao = mock(DatabaseDao.class);
        DatabaseService service = new DatabaseService(settings, dao, mock(TaskSchedulingService.class));
        Path folder = Files.createTempDirectory("database-status-test");
        Path file = Files.createFile(folder.resolve("import.xml"));
        try {
            doThrow(new IllegalStateException("test")).when(dao).importDB(any());
            try {
                service.importDB(folder);
                fail("Import should fail");
            } catch (IllegalStateException expected) {
                assertEquals("test", expected.getMessage());
            }
            assertFalse(service.getImportStatus().running());
            assertTrue(service.getImportStatus().failed());
            assertEquals(1, service.getImportStatus().version());
            Files.delete(file);
            service.importDB(folder);
            assertEquals(2, service.getImportStatus().version());
            assertFalse(service.getImportStatus().failed());
            assertEquals("Nothing imported", service.getImportStatus().message());
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(folder);
        }
    }
}
