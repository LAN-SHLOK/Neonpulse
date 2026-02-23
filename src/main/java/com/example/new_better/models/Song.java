package com.example.new_better.models;

import java.time.LocalDateTime;

public class Song {
    private int songId;
    private String title;
    private String genre;
    private String filePath;
    private double duration;
    private LocalDateTime uploadedAt;

    public Song() {}

    public Song(int songId, String title, String genre, String filePath, double duration, LocalDateTime uploadedAt) {
        this.songId = songId;
        this.title = title;
        this.genre = genre;
        this.filePath = filePath;
        this.duration = duration;
        this.uploadedAt = uploadedAt;
    }

    // Getters and Setters
    public int getSongId() { return songId; }
    public void setSongId(int songId) { this.songId = songId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public double getDuration() { return duration; }
    public void setDuration(double duration) { this.duration = duration; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getFormattedDuration() {
        int minutes = (int) (duration / 60);
        int seconds = (int) (duration % 60);
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Song song = (Song) obj;
        return songId == song.songId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(songId);
    }
}