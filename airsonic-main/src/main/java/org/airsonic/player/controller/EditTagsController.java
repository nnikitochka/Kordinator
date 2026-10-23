package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.metadata.JaudiotaggerParser;
import org.airsonic.player.service.metadata.MetaDataParser;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the page used to edit MP3 tags.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/editTags")
public class EditTagsController {
    private final MediaFileService mediaFileService;

    public EditTagsController(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {

        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");
        MediaFile dir = mediaFileService.getMediaFile(id);
        List<MediaFile> files = mediaFileService.getChildrenOf(dir, true, false, true, false);

        Map<String, Object> map = new HashMap<>();
        if (!files.isEmpty()) {
            map.put("defaultArtist", files.getFirst().getArtist());
            map.put("defaultAlbum", files.getFirst().getAlbumName());
            map.put("defaultYear", files.getFirst().getYear());
            map.put("defaultGenre", files.getFirst().getGenre());
        }
        map.put("allGenres", JaudiotaggerParser.getID3V1Genres());

        List<Song> songs = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            songs.add(createSong(files.get(i), i));
        }
        map.put("id", id);
        map.put("songs", songs);

        return new ModelAndView("editTags","model",map);
    }

    private Song createSong(MediaFile file, int index) {
        Song song = new Song();
        song.setId(file.getId());
        song.setFileName(FilenameUtils.getBaseName(file.getPath()));
        song.setTrack(file.getTrackNumber());
        song.setSuggestedTrack(index + 1);
        song.setTitle(file.getTitle());
        song.setSuggestedTitle(MetaDataParser.guessTitle(file.getRelativePath()));
        song.setArtist(file.getArtist());
        song.setAlbum(file.getAlbumName());
        song.setYear(file.getYear());
        song.setGenre(file.getGenre());
        return song;
    }


    /**
     * Contains information about a single song.
     */
    public static class Song {
        private int id;
        private String fileName;
        private Integer suggestedTrack;
        private Integer track;
        private String suggestedTitle;
        private String title;
        private String artist;
        private String album;
        private Integer year;
        private String genre;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public Integer getSuggestedTrack() {
            return suggestedTrack;
        }

        public void setSuggestedTrack(Integer suggestedTrack) {
            this.suggestedTrack = suggestedTrack;
        }

        public Integer getTrack() {
            return track;
        }

        public void setTrack(Integer track) {
            this.track = track;
        }

        public String getSuggestedTitle() {
            return suggestedTitle;
        }

        public void setSuggestedTitle(String suggestedTitle) {
            this.suggestedTitle = suggestedTitle;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getAlbum() {
            return album;
        }

        public void setAlbum(String album) {
            this.album = album;
        }

        public Integer getYear() {
            return year;
        }

        public void setYear(Integer year) {
            this.year = year;
        }

        public String getGenre() {
            return genre;
        }

        public void setGenre(String genre) {
            this.genre = genre;
        }
    }
}
