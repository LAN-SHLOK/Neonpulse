    package com.example.new_better.dao;

    import com.example.new_better.models.User;
    import com.example.new_better.utils.DatabaseInitializer;
    import com.example.new_better.utils.PasswordUtil;

    import java.sql.*;
    import java.time.LocalDateTime;

    public class UserDAO {

        private String getDbUrl() {
            return DatabaseInitializer.getDbUrl();
        }

        public User getUserByUsername(String username) {
            String sql = "SELECT * FROM users WHERE username = ?";
            try (Connection conn = DriverManager.getConnection(getDbUrl());
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) return extractUser(rs);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        public User getUserByEmail(String email) {
            String sql = "SELECT * FROM users WHERE email = ?";
            try (Connection conn = DriverManager.getConnection(getDbUrl());
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, email);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) return extractUser(rs);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        public User insertUser(User user) {
            String sql = "INSERT INTO users (username, email, password, profile_picture, is_verified) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = DriverManager.getConnection(getDbUrl());
                 PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                pstmt.setString(1, user.getUsername());
                pstmt.setString(2, user.getEmail());
                pstmt.setString(3, user.getPassword());

                // 🔥 CHANGED: Always set NULL for DB blob (we use local files now)
                pstmt.setNull(4, Types.BLOB);

                pstmt.setInt(5, user.isVerified() ? 1 : 0);

                pstmt.executeUpdate();

                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    user.setUserId(rs.getInt(1));
                    return user;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        // Keep the rest of the file exactly as it was...
        public boolean validateUserForReset(String username, String email) {
            String sql = "SELECT COUNT(*) FROM users WHERE username = ? AND email = ?";
            try (Connection conn = DriverManager.getConnection(getDbUrl());
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, email);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) return rs.getInt(1) > 0;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }

        public boolean updatePassword(String username, String newPassword) {
            String sql = "UPDATE users SET password = ? WHERE username = ?";
            try (Connection conn = DriverManager.getConnection(getDbUrl());
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, PasswordUtil.hashPassword(newPassword));
                pstmt.setString(2, username);
                return pstmt.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }

        public User authenticateUser(String input, String plainPassword) {
            User user = getUserByUsername(input);
            if (user == null) user = getUserByEmail(input);
            if (user == null) return null;
            if (PasswordUtil.checkPassword(plainPassword, user.getPassword())) return user;
            return null;
        }

        public void updateProfilePicture(int userId, byte[] profilePictureBytes) {
            // We might not use this anymore, but keep it valid just in case
            String sql = "UPDATE users SET profile_picture = ? WHERE user_id = ?";
            try (Connection conn = DriverManager.getConnection(getDbUrl());
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setBytes(1, profilePictureBytes);
                pstmt.setInt(2, userId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        public void verifyUser(int userId) {
            String sql = "UPDATE users SET is_verified = 1 WHERE user_id = ?";
            try (Connection conn = DriverManager.getConnection(getDbUrl());
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private User extractUser(ResultSet rs) throws SQLException {
            User user = new User();
            user.setUserId(rs.getInt("user_id"));
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            user.setPassword(rs.getString("password"));
            user.setProfilePicture(rs.getBytes("profile_picture"));
            user.setVerified(rs.getInt("is_verified") == 1);
            String createdAtStr = rs.getString("created_at");
            if (createdAtStr != null) {
                user.setCreatedAt(LocalDateTime.parse(createdAtStr.replace(" ", "T")));
            }
            return user;
        }
    }