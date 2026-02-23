package com.example.new_better.dao;

import com.example.new_better.models.Song;
import com.example.new_better.utils.DatabaseInitializer;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SongDAO {

    // ✅ Dynamic getter to ensure we always have the current deployment path
    private String getDbUrl() {
        return DatabaseInitializer.getDbUrl();
    }

    static {
        // 🔥 DEBUG: Keep your original debug checks
        String currentUrl = DatabaseInitializer.getDbUrl();
        System.out.println("SongDAO initializing with DB URL: " + currentUrl);

        if (currentUrl != null && currentUrl.startsWith("jdbc:sqlite:")) {
            String path = currentUrl.replace("jdbc:sqlite:", "");
            File dbFile = new File(path);
            System.out.println("DB Absolute Path: " + dbFile.getAbsolutePath());
            System.out.println("DB Exists: " + dbFile.exists());
        }
    }

    public List<Song> getAllSongs() {
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT * FROM songs ORDER BY title";

        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Updated to dynamic URL
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                songs.add(extractSong(rs));
            }

        } catch (SQLException e) {
            System.err.println("Error fetching all songs:");
            e.printStackTrace();
        }

        System.out.println("getAllSongs() returned: " + songs.size());
        return songs;
    }

    public Song getSongById(int songId) {
        String sql = "SELECT * FROM songs WHERE song_id = ?";

        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Updated to dynamic URL
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, songId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return extractSong(rs);
            }

        } catch (SQLException e) {
            System.err.println("Error fetching song by ID:");
            e.printStackTrace();
        }

        return null;
    }

    public Song getSongByFilePath(String filePath) {
        String sql = "SELECT * FROM songs WHERE file_path = ?";

        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Updated to dynamic URL
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, filePath);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return extractSong(rs);
            }

        } catch (SQLException e) {
            System.err.println("Error fetching song by file path:");
            e.printStackTrace();
        }

        return null;
    }

    /* =========================================================
       🔥 FIXED GENRE LOADING: Now case-insensitive
       ========================================================= */
    public List<Song> getSongsByGenre(String genre) {
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT * FROM songs WHERE genre = ? COLLATE NOCASE ORDER BY title";

        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Updated to dynamic URL
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, genre);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                songs.add(extractSong(rs));
            }

        } catch (SQLException e) {
            System.err.println("Error fetching songs by genre: " + genre);
            e.printStackTrace();
        }

        return songs;
    }

    public void insertSong(Song song) {
        String sql = "INSERT INTO songs (title, genre, file_path, duration) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Updated to dynamic URL
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, song.getTitle());
            pstmt.setString(2, song.getGenre());
            pstmt.setString(3, song.getFilePath());
            pstmt.setDouble(4, song.getDuration());

            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                song.setSongId(rs.getInt(1));
            }

        } catch (SQLException e) {
            System.err.println("Error inserting song:");
            e.printStackTrace();
        }
    }

    public List<Song> searchSongs(String query) {
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT * FROM songs WHERE title LIKE ? OR genre LIKE ? ORDER BY title";

        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Updated to dynamic URL
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchPattern = "%" + query + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                songs.add(extractSong(rs));
            }

        } catch (SQLException e) {
            System.err.println("Error searching songs:");
            e.printStackTrace();
        }

        return songs;
    }

    /* =========================================================
       🔥 REPAIR METHOD: Used by AllSongsController
       ========================================================= */
    public void updateSongDuration(int songId, double duration) {
        String sql = "UPDATE songs SET duration = ? WHERE song_id = ?";
        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Updated to dynamic URL
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, duration);
            pstmt.setInt(2, songId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error updating song duration:");
            e.printStackTrace();
        }
    }

    private Song extractSong(ResultSet rs) throws SQLException {
        // ✅ Keep: Original mapping and LocalDateTime parsing logic
        Song song = new Song();
        song.setSongId(rs.getInt("song_id"));
        song.setTitle(rs.getString("title"));
        song.setGenre(rs.getString("genre"));
        song.setFilePath(rs.getString("file_path"));
        song.setDuration(rs.getDouble("duration"));

        String uploadedAtStr = rs.getString("uploaded_at");
        if (uploadedAtStr != null) {
            try {
                song.setUploadedAt(LocalDateTime.parse(uploadedAtStr.replace(" ", "T")));
            } catch (Exception ignored) {
                song.setUploadedAt(LocalDateTime.now());
            }
        }
        return song;
    }
}