package com.example.karaoke.controller;

import com.example.karaoke.model.Song;
import com.example.karaoke.service.KaraokeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/songs")
public class SongController {

    private final KaraokeService karaokeService;

    public SongController(KaraokeService karaokeService) {
        this.karaokeService = karaokeService;
    }

    @GetMapping
    public List<Song> searchSongs(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String language) {
        return karaokeService.searchSongs(query, source, language);
    }
}