package com.example.new_better.utils;

import com.example.new_better.models.User;

public class Session {
    private static Session instance;
    private User currentUser;

    // 🔥 New field for iOS-level state persistence
    private double userVolume = 0.5; // Default to 50% volume

    private Session() {}

    public static Session getInstance() {
        if (instance == null) {
            instance = new Session();
        }
        return instance;
    }

    // --- User Session Methods ---
    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public void logout() {
        currentUser = null;
        MusicPlayerManager.getInstance().stop();
        MusicPlayerManager.getInstance().clearQueue();
    }

    // --- 🔥 NEW: Volume Management Methods ---

    /**
     * Gets the current session volume (0.0 to 1.0).
     */
    public double getUserVolume() {
        return userVolume;
    }

    /**
     * Sets the session volume and persists it during the app's runtime.
     */
    public void setUserVolume(double userVolume) {
        this.userVolume = userVolume;
    }
}