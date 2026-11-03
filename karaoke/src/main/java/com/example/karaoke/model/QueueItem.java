package com.example.karaoke.model;

public class QueueItem {
    private Long id;
    private String userName;
    private Song song;
    private String status; // WAITING, PLAYING, FINISHED

    public QueueItem() {}

    public QueueItem(Long id, String userName, Song song) {
        this.id = id;
        this.userName = userName;
        this.song = song;
        this.status = "WAITING";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public Song getSong() { return song; }
    public void setSong(Song song) { this.song = song; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}