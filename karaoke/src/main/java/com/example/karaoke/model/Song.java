package com.example.karaoke.model;

public class Song {
    private Long id;
    private String title;
    private String artist;
    private String source;
    private String url;
    private String language;

    public Song() {}

    public Song(Long id, String title, String artist, String source, String url, String language) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.source = source;
        this.url = url;
        this.language = language;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}