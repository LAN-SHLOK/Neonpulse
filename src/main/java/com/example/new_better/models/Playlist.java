package com.example.new_better.models;

import java.sql.Timestamp;

public class Playlist {

    private int playlistId;
    private String name;
    private int userId;
    private Timestamp createdAt;

    // Constructors
    public Playlist() {
    }

    public Playlist(int playlistId, String name, int userId) {
        this.playlistId = playlistId;
        this.name = name;
        this.userId = userId;
    }

    // Getters and Setters
    public int getId() { // NOTE: matching the .getId() call in controller
        return playlistId;
    }

    public void setId(int playlistId) {
        this.playlistId = playlistId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    // 🔥 CRITICAL: This method determines what shows up in the Dropdown Menu
    @Override
    public String toString() {
        return name;
    }
}