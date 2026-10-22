package org.airsonic.player.ajax.bookmark;

import org.airsonic.player.service.BookmarkService;
import org.airsonic.player.service.MediaFileService;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookmarksWSControllerTest {

    @Test
    void getBookmarkReturnsNullWhenBookmarkDoesNotExist() {
        BookmarkService bookmarkService = mock(BookmarkService.class);
        MediaFileService mediaFileService = mock(MediaFileService.class);
        Principal principal = () -> "testUser";

        when(bookmarkService.getBookmark("testUser", 123)).thenReturn(null);

        BookmarksWSController controller = new BookmarksWSController(bookmarkService, mediaFileService);
        BookmarkInfo result = controller.getBookmark(principal, 123);

        assertNull(result);
    }
}
