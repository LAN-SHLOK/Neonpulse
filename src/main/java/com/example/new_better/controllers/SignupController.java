package com.example.new_better.controllers;

import com.example.new_better.MainApp;
import com.example.new_better.dao.PlaylistDAO;
import com.example.new_better.dao.SongDAO;
import com.example.new_better.dao.UserDAO;
import com.example.new_better.models.Song;
import com.example.new_better.models.User;
import com.example.new_better.utils.PasswordUtil;
import com.example.new_better.utils.Session;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class SignupController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ImageView profileImageView;
    @FXML private Label errorLabel;
    @FXML private Button signupButton; // add fx:id="signupButton" in your FXML

    private File selectedImageFile;

    @FXML
    private void handleSelectImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Picture");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        File file = fileChooser.showOpenDialog(MainApp.getPrimaryStage());
        if (file != null) {
            try {
                selectedImageFile = file;
                profileImageView.setImage(new Image(file.toURI().toString()));
            } catch (Exception e) {
                e.printStackTrace();
                errorLabel.setText("Failed to load image");
            }
        }
    }

    @FXML
    private void handleSignup() {
        // ── Validation runs on JAT — no I/O, perfectly fine here ────────────
        String username        = usernameField.getText().trim();
        String email           = emailField.getText().trim();
        String password        = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            errorLabel.setText("Please fill in all fields");
            return;
        }
        if (!password.equals(confirmPassword)) {
            errorLabel.setText("Passwords do not match");
            return;
        }
        if (password.length() < 6) {
            errorLabel.setText("Password must be at least 6 characters");
            return;
        }

        // Lock UI so user can't double-submit
        setFormLocked(true);
        errorLabel.setText("Creating account...");

        final File imageToSave = selectedImageFile;

        // ── All DB work + file copy runs off the JAT ─────────────────────────
        Task<User> signupTask = new Task<>() {
            @Override
            protected User call() throws Exception {
                UserDAO userDAO = new UserDAO();

                if (userDAO.getUserByUsername(username) != null)
                    throw new SignupException("Username already exists");

                if (userDAO.getUserByEmail(email) != null)
                    throw new SignupException("Email already exists");

                User user = new User();
                user.setUsername(username);
                user.setEmail(email);
                user.setPassword(PasswordUtil.hashPassword(password));
                user.setVerified(true);

                User createdUser = userDAO.insertUser(user);
                if (createdUser == null)
                    throw new Exception("Signup failed. Please try again.");

                if (imageToSave != null)
                    saveProfileImageToDisk(imageToSave, createdUser.getUserId());

                createSystemPlaylists(createdUser.getUserId());

                return createdUser;
            }
        };

        signupTask.setOnSucceeded(e -> {
            Session.getInstance().setCurrentUser(signupTask.getValue());
            MainApp.changeScene(
                    "/com/example/new_better/views/mainPage.fxml",
                    "/com/example/new_better/css/sidebar.css",
                    "/com/example/new_better/css/player_bar.css"
            );
        });

        signupTask.setOnFailed(e -> {
            setFormLocked(false);
            Throwable ex = signupTask.getException();
            if (ex instanceof SignupException) {
                errorLabel.setText(ex.getMessage());       // friendly validation msg
            } else {
                errorLabel.setText(ex.getMessage());       // unexpected DB/IO error
                ex.printStackTrace();
            }
        });

        Thread t = new Thread(signupTask, "signup-thread");
        t.setDaemon(true);
        t.start();
    }

    private void setFormLocked(boolean locked) {
        usernameField.setDisable(locked);
        emailField.setDisable(locked);
        passwordField.setDisable(locked);
        confirmPasswordField.setDisable(locked);
        if (signupButton != null) signupButton.setDisable(locked);
    }

    // ✅ PORTABLE — user.dir = folder where the .exe runs, unchanged from your original
    private void saveProfileImageToDisk(File source, int userId) {
        try {
            String currentDir = System.getProperty("user.dir");
            File imagesDir = new File(currentDir, "images");

            if (!imagesDir.exists()) imagesDir.mkdirs();

            File dest = new File(imagesDir, "profile_" + userId + ".jpg");
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Saved profile image to: " + dest.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("Failed to save profile image to disk.");
            e.printStackTrace();
        }
    }

    private void createSystemPlaylists(int userId) {
        PlaylistDAO playlistDAO = new PlaylistDAO();
        SongDAO songDAO = new SongDAO();
        String[] moods = {"Party", "Pop", "Classical", "Romantic", "Sad"};

        for (String mood : moods) {
            int playlistId = playlistDAO.createPlaylist(userId, mood, true);
            List<Song> songs = songDAO.getSongsByGenre(mood);
            for (Song song : songs) {
                playlistDAO.addSongToPlaylist(playlistId, song.getSongId());
            }
        }
    }

    @FXML
    private void handleGoToLogin() {
        MainApp.changeScene(
                "/com/example/new_better/views/login.fxml",
                "/com/example/new_better/css/auth.css"
        );
    }

    @FXML
    private void handleMinimize(ActionEvent event) {
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleExit() {
        Platform.exit();
        System.exit(0);
    }

    // Distinguishes validation failures from unexpected errors inside the Task
    private static class SignupException extends Exception {
        SignupException(String message) { super(message); }
    }
}