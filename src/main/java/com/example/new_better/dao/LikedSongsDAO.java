
package com.example.new_better.dao;

import com.example.new_better.models.Song;
import com.example.new_better.utils.DatabaseInitializer; // ✅ Imported your utility
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LikedSongsDAO {

    // ✅ Use the dynamic URL from your Deployment Initializer
    private String getDbUrl() {
        return DatabaseInitializer.getDbUrl();
    }

    public void likeSong(int userId, int songId) {
        String sql = "INSERT OR IGNORE INTO liked_songs (user_id, song_id) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Dynamic Connection
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setInt(2, songId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void unlikeSong(int userId, int songId) {
        String sql = "DELETE FROM liked_songs WHERE user_id = ? AND song_id = ?";
        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Dynamic Connection
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setInt(2, songId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isLiked(int userId, int songId) {
        String sql = "SELECT COUNT(*) FROM liked_songs WHERE user_id = ? AND song_id = ?";
        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Dynamic Connection
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setInt(2, songId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<Song> getLikedSongs(int userId) {
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT s.* FROM songs s " +
                "INNER JOIN liked_songs ls ON s.song_id = ls.song_id " +
                "WHERE ls.user_id = ? ORDER BY ls.liked_at DESC";

        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Dynamic Connection
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                songs.add(extractSongFromResultSet(rs));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return songs;
    }

    private Song extractSongFromResultSet(ResultSet rs) throws SQLException {
        Song song = new Song();
        song.setSongId(rs.getInt("song_id"));
        song.setTitle(rs.getString("title"));
        song.setGenre(rs.getString("genre"));
        song.setFilePath(rs.getString("file_path"));
        song.setDuration(rs.getDouble("duration"));
        return song;
    }
}