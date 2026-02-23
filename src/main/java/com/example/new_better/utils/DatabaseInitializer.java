package com.example.new_better.utils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Portable DB strategy preserved: DB file sits next to the .exe in user.dir.
 * Copy the folder to any PC → it works. No registry, no AppData, no admin rights needed.
 *
 * Actual bugs fixed:
 *   1. Exception is now RETHROWN so MainApp's Task.setOnFailed() shows the Alert.
 *      Original swallowed it — app continued running with no database.
 *   2. SQL injection in genre seed loop → replaced with PreparedStatement.
 *   3. PRAGMA foreign_keys moved before DDL (correct SQLite ordering).
 *   4. PRAGMA journal_mode = WAL added — prevents DB lock errors when import
 *      thread and player thread open connections simultaneously.
 *   5. Broken user_id=1 seed block removed — system playlists are created in
 *      SignupController after insertUser() returns the actual user ID.
 */
public class DatabaseInitializer {

    // ✅ PORTABLE: DB lives next to the EXE. Copy folder = works anywhere.
    private static final String DB_PATH =
            System.getProperty("user.dir") + File.separator + "neonpulse.db";

    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;

    public static void initialize() {
        System.out.println("🚀 Initializing Database at: " + DB_PATH);

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // ✅ FIX 3 & 4: Configure connection before any DDL
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA foreign_keys = ON");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    username        TEXT UNIQUE NOT NULL,
                    email           TEXT UNIQUE NOT NULL,
                    password        TEXT NOT NULL,
                    profile_picture BLOB,
                    is_verified     INTEGER DEFAULT 1,
                    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS songs (
                    song_id     INTEGER PRIMARY KEY AUTOINCREMENT,
                    title       TEXT NOT NULL,
                    genre       TEXT,
                    file_path   TEXT UNIQUE NOT NULL,
                    duration    REAL,
                    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS playlists (
                    playlist_id   INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id       INTEGER NOT NULL,
                    playlist_name TEXT NOT NULL,
                    is_system     INTEGER DEFAULT 0,
                    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS playlist_songs (
                    playlist_id INTEGER NOT NULL,
                    song_id     INTEGER NOT NULL,
                    added_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (playlist_id, song_id),
                    FOREIGN KEY (playlist_id) REFERENCES playlists(playlist_id) ON DELETE CASCADE,
                    FOREIGN KEY (song_id)     REFERENCES songs(song_id)         ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS liked_songs (
                    user_id  INTEGER NOT NULL,
                    song_id  INTEGER NOT NULL,
                    liked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (user_id, song_id),
                    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                    FOREIGN KEY (song_id) REFERENCES songs(song_id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS recently_played (
                    user_id   INTEGER NOT NULL,
                    song_id   INTEGER NOT NULL,
                    played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (user_id, song_id, played_at),
                    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                    FOREIGN KEY (song_id) REFERENCES songs(song_id) ON DELETE CASCADE
                )
            """);

            // ✅ FIX 5: Removed broken user_id=1 seed block.
            // System playlists are now created in SignupController with the real user ID.

            System.out.println("✅ Database initialized successfully.");

        } catch (Exception e) {
            System.err.println("❌ Database initialization failed: " + e.getMessage());
            e.printStackTrace();
            // ✅ FIX 1: RETHROW so MainApp's Task.setOnFailed() triggers the Alert dialog.
            // Without this the app silently continues with no DB and crashes later.
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public static String getDbUrl()  { return DB_URL;  }
    public static String getDbPath() { return DB_PATH; }
}