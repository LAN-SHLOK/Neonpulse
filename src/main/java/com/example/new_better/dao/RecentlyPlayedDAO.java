//package com.example.new_better.dao;
//
//import com.example.new_better.models.Song;
//
//import java.sql.*;
//import java.util.ArrayList;
//import java.util.List;
//
//public class RecentlyPlayedDAO {
//    private static final String DB_URL = "jdbc:sqlite:neonpulse.db";
//
//    public void addToRecentlyPlayed(int userId, int songId) {
//        // Check if the last played song is the same (prevent consecutive duplicates)
//        if (isLastPlayedSong(userId, songId)) {
//            return;
//        }
//
//        try (Connection conn = DriverManager.getConnection(DB_URL)) {
//
//            // FIRST: Delete any existing entries for this song (remove old position)
//            String deleteSql = "DELETE FROM recently_played WHERE user_id = ? AND song_id = ?";
//            try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
//                pstmt.setInt(1, userId);
//                pstmt.setInt(2, songId);
//                pstmt.executeUpdate();
//            }
//
//            // SECOND: Insert new entry (moves song to top)
//            String insertSql = "INSERT INTO recently_played (user_id, song_id) VALUES (?, ?)";
//            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
//                pstmt.setInt(1, userId);
//                pstmt.setInt(2, songId);
//                pstmt.executeUpdate();
//            }
//
//            // Keep only last 100 records
//            cleanupOldRecords(userId);
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private boolean isLastPlayedSong(int userId, int songId) {
//        String sql = "SELECT song_id FROM recently_played WHERE user_id = ? ORDER BY played_at DESC LIMIT 1";
//        try (Connection conn = DriverManager.getConnection(DB_URL);
//             PreparedStatement pstmt = conn.prepareStatement(sql)) {
//
//            pstmt.setInt(1, userId);
//            ResultSet rs = pstmt.executeQuery();
//
//            if (rs.next()) {
//                return rs.getInt("song_id") == songId;
//            }
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//        return false;
//    }
//
//    private void cleanupOldRecords(int userId) {
//        String sql = "DELETE FROM recently_played WHERE user_id = ? AND played_at NOT IN " +
//                "(SELECT played_at FROM recently_played WHERE user_id = ? ORDER BY played_at DESC LIMIT 100)";
//        try (Connection conn = DriverManager.getConnection(DB_URL);
//             PreparedStatement pstmt = conn.prepareStatement(sql)) {
//
//            pstmt.setInt(1, userId);
//            pstmt.setInt(2, userId);
//            pstmt.executeUpdate();
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public List<Song> getRecentlyPlayed(int userId) {
//        List<Song> songs = new ArrayList<>();
//        String sql = "SELECT DISTINCT s.* FROM songs s " +
//                "INNER JOIN recently_played rp ON s.song_id = rp.song_id " +
//                "WHERE rp.user_id = ? ORDER BY rp.played_at DESC LIMIT 50";
//
//        try (Connection conn = DriverManager.getConnection(DB_URL);
//             PreparedStatement pstmt = conn.prepareStatement(sql)) {
//
//            pstmt.setInt(1, userId);
//            ResultSet rs = pstmt.executeQuery();
//
//            while (rs.next()) {
//                songs.add(extractSongFromResultSet(rs));
//            }
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//        return songs;
//    }
//
//    private Song extractSongFromResultSet(ResultSet rs) throws SQLException {
//        Song song = new Song();
//        song.setSongId(rs.getInt("song_id"));
//        song.setTitle(rs.getString("title"));
//        song.setGenre(rs.getString("genre"));
//        song.setFilePath(rs.getString("file_path"));
//        song.setDuration(rs.getDouble("duration"));
//        return song;
//    }
//}
package com.example.new_better.dao;

import com.example.new_better.models.Song;
import com.example.new_better.utils.DatabaseInitializer; // ✅ Keep: Utility import
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RecentlyPlayedDAO {

    // ✅ Keep: Dynamic path logic for Deployment
    private String getDbUrl() {
        return DatabaseInitializer.getDbUrl();
    }

    public void addToRecentlyPlayed(int userId, int songId) {
        // ✅ Keep: Prevention of consecutive duplicates
        if (isLastPlayedSong(userId, songId)) {
            return;
        }

        try (Connection conn = DriverManager.getConnection(getDbUrl())) { // ✅ Updated to dynamic URL

            // ✅ Keep: Delete any existing entries for this song (remove old position)
            String deleteSql = "DELETE FROM recently_played WHERE user_id = ? AND song_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, songId);
                pstmt.executeUpdate();
            }

            // ✅ Keep: Insert new entry (moves song to top)
            String insertSql = "INSERT INTO recently_played (user_id, song_id) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, songId);
                pstmt.executeUpdate();
            }

            // ✅ Keep: Cleanup logic for last 100 records
            cleanupOldRecords(userId);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isLastPlayedSong(int userId, int songId) {
        String sql = "SELECT song_id FROM recently_played WHERE user_id = ? ORDER BY played_at DESC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Updated to dynamic URL
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("song_id") == songId;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void cleanupOldRecords(int userId) {
        String sql = "DELETE FROM recently_played WHERE user_id = ? AND played_at NOT IN " +
                "(SELECT played_at FROM recently_played WHERE user_id = ? ORDER BY played_at DESC LIMIT 100)";
        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Updated to dynamic URL
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Song> getRecentlyPlayed(int userId) {
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT DISTINCT s.* FROM songs s " +
                "INNER JOIN recently_played rp ON s.song_id = rp.song_id " +
                "WHERE rp.user_id = ? ORDER BY rp.played_at DESC LIMIT 50";

        try (Connection conn = DriverManager.getConnection(getDbUrl()); // ✅ Updated to dynamic URL
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
        // ✅ Keep: Mapping logic
        Song song = new Song();
        song.setSongId(rs.getInt("song_id"));
        song.setTitle(rs.getString("title"));
        song.setGenre(rs.getString("genre"));
        song.setFilePath(rs.getString("file_path"));
        song.setDuration(rs.getDouble("duration"));
        return song;
    }
}