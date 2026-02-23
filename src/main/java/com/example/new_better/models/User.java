package com.example.new_better.models;

import java.time.LocalDateTime;

public class User {
    private int userId;
    private String username;
    private String email;
    private String password;
    private byte[] profilePicture;
    private boolean isVerified;
    private LocalDateTime createdAt;

    public User() {}

    public User(int userId, String username, String email, String password, byte[] profilePicture, boolean isVerified, LocalDateTime createdAt) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.password = password;
        this.profilePicture = profilePicture;
        this.isVerified = isVerified;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public byte[] getProfilePicture() { return profilePicture; }
    public void setProfilePicture(byte[] profilePicture) { this.profilePicture = profilePicture; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}