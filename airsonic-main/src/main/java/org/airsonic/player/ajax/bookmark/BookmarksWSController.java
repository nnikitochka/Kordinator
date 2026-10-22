package org.airsonic.player.ajax.bookmark;

import org.airsonic.player.domain.Bookmark;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.BookmarkService;
import org.airsonic.player.service.MediaFileService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@MessageMapping("/bookmarks")
public class BookmarksWSController {
    private final BookmarkService bookmarkService;
    private final MediaFileService mediaFileService;

    public BookmarksWSController(
            BookmarkService bookmarkService,
            MediaFileService mediaFileService
    ) {
        this.bookmarkService = bookmarkService;
        this.mediaFileService = mediaFileService;
    }

    @SubscribeMapping("/list")
    private Map<Integer, BookmarkInfo> getBookmarks(Principal user) {
        return bookmarkService.getBookmarks(user.getName()).stream()
                .map(b -> bookmarkToBookmarkInfo(b, user.getName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(bi -> bi.getMediaFileEntry().getId(), bi -> bi));
    }

    private BookmarkInfo bookmarkToBookmarkInfo(Bookmark bookmark, String user) {
        if (bookmark == null) {
            return null;
        }
        MediaFile mediaFile = mediaFileService.getMediaFile(bookmark.getMediaFileId());
        if (mediaFile == null) {
            return null;
        }
        return new BookmarkInfo(
                bookmark.getId(),
                mediaFileService.toMediaFileEntryList(Collections.singletonList(mediaFile), user, true, false, null, null, null).getFirst(),
                bookmark.getChanged(),
                bookmark.getCreated(),
                bookmark.getComment(),
                bookmark.getPositionMillis());
    }

    @MessageMapping("/set")
    public boolean create(Principal user, BookmarkCreateRequest bookmark) {
        return bookmarkService.setBookmark(user.getName(), bookmark.getMediaFileId(), bookmark.getPositionMillis(), bookmark.getComment());
    }

    @MessageMapping("/delete")
    public int delete(Principal user, int mediaFileId) {
        bookmarkService.deleteBookmark(user.getName(), mediaFileId);
        return mediaFileId;
    }

    @MessageMapping("/get")
    @SendToUser(broadcast = false)
    public BookmarkInfo getBookmark(Principal user, int mediaFileId) {
        return bookmarkToBookmarkInfo(bookmarkService.getBookmark(user.getName(), mediaFileId), user.getName());
    }
}
