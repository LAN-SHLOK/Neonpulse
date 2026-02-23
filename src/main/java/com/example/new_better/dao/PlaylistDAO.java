package com.example.new_better.dao;

import com.example.new_better.models.Song;
import com.example.new_better.utils.DatabaseInitializer;

import java.sql.*;
import java.util.*;

public class PlaylistDAO {

    // ✅ Dynamic getter to ensure the deployment path is always synced
    private String getDbUrl() {
        return DatabaseInitializer.getDbUrl();
    }

    /* ================= CREATE ================= */

    public int createPlaylist(int userId, String playlistName, boolean isSystem) {
        String sql = "INSERT INTO playlists (user_id, playlist_name, is_system) VALUES (?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(getDbUrl());
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, userId);
            pstmt.setString(2, playlistName);
            pstmt.setInt(3, isSystem ? 1 : 0);

            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /* ================= GET ================= */

    public Map<String, Object> getPlaylistById(int playlistId) {
        String sql = "SELECT * FROM playlists WHERE playlist_id = ?";

        try (Connection conn = DriverManager.getConnection(getDbUrl());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, playlistId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Map<String, Object> playlist = new HashMap<>();
                playlist.put("playlist_id",   rs.getInt("playlist_id"));
                playlist.put("user_id",       rs.getInt("user_id"));
                playlist.put("playlist_name", rs.getString("playlist_name"));
                // 🔥 KEEP AS INTEGER: Controller uses (int) details.get("is_system")
                playlist.put("is_system",     rs.getInt("is_system"));
                playlist.put("created_at",    rs.getString("created_at"));

                return playlist;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Map<String, Object>> getUserPlaylists(int userId) {
        List<Map<String, Object>> playlists = new ArrayList<>();

        // ✅ User-created playlists (is_system=0) come first, sorted by song count DESC
        // ✅ System playlists (is_system=1) always appear after
        // ✅ Alphabetical tiebreaker when song counts are equal
        String sql = "SELECT p.*, COUNT(ps.song_id) as song_count " +
                "FROM playlists p " +
                "LEFT JOIN playlist_songs ps ON p.playlist_id = ps.playlist_id " +
                "WHERE p.user_id = ? " +
                "GROUP BY p.playlist_id " +
                "ORDER BY p.is_system ASC, song_count DESC, p.playlist_name ASC";

        try (Connection conn = DriverManager.getConnection(getDbUrl());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> playlist = new HashMap<>();
                playlist.put("playlist_id",   rs.getInt("playlist_id"));
                playlist.put("user_id",       rs.getInt("user_id"));
                playlist.put("playlist_name", rs.getString("playlist_name"));
                playlist.put("is_system",     rs.getInt("is_system"));
                playlist.put("created_at",    rs.getString("created_at"));
                playlist.put("song_count",    rs.getInt("song_count"));

                playlists.add(playlist);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return playlists;
    }

    /* ================= PLAYLIST SONGS ================= */

    public void addSongToPlaylist(int playlistId, int songId) {
        String sql = "INSERT OR IGNORE INTO playlist_songs (playlist_id, song_id) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(getDbUrl());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playlistId);
            pstmt.setInt(2, songId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeSongFromPlaylist(int playlistId, int songId) {
        String sql = "DELETE FROM playlist_songs WHERE playlist_id = ? AND song_id = ?";
        try (Connection conn = DriverManager.getConnection(getDbUrl());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playlistId);
            pstmt.setInt(2, songId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* ================= UPDATE ================= */

    public void renamePlaylist(int playlistId, String newName) {
        String sql = "UPDATE playlists SET playlist_name = ? WHERE playlist_name = ? AND playlist_id = ?";
        try (Connection conn = DriverManager.getConnection(getDbUrl());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setString(2, newName);
            pstmt.setInt(3, playlistId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deletePlaylist(int playlistId) {
        String sql = "DELETE FROM playlists WHERE playlist_id = ?";
        try (Connection conn = DriverManager.getConnection(getDbUrl());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playlistId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* ================= HELPER ================= */

    private Song extractSong(ResultSet rs) throws SQLException {
        Song song = new Song();
        song.setSongId(rs.getInt("song_id"));
        song.setTitle(rs.getString("title"));
        song.setGenre(rs.getString("genre"));
        song.setFilePath(rs.getString("file_path"));
        song.setDuration(rs.getDouble("duration"));
        return song;
    }

    public List<Song> getPlaylistSongs(int playlistId) {
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT s.* FROM songs s " +
                "INNER JOIN playlist_songs ps ON s.song_id = ps.song_id " +
                "WHERE ps.playlist_id = ? ORDER BY ps.added_at";

        try (Connection conn = DriverManager.getConnection(getDbUrl());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, playlistId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                songs.add(extractSong(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return songs;
    }
}