package com.example.new_better.controllers;

import com.example.new_better.MainApp;
import com.example.new_better.dao.PlaylistDAO;
import com.example.new_better.dao.SongDAO;
import com.example.new_better.models.Song;
import com.example.new_better.models.User;
import com.example.new_better.utils.Session;
import com.example.new_better.utils.SongFolderImporter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.Map;

public class MainController {

    @FXML private VBox rootPane;
    @FXML private Label pageTitleLabel;
    @FXML private TextField searchField;
    @FXML private ImageView profileAvatar;
    @FXML private VBox playlistList;
    @FXML private StackPane playerBarContainer;
    @FXML private StackPane centerContent;
    @FXML private VBox pageContentContainer;
    @FXML private VBox queueList;
    @FXML private VBox queuePanelContainer;

    private String currentView = "all_songs";
    private Object currentController;

    @FXML
    private void initialize() {
        SongFolderImporter.addImportListener(() -> {
            Platform.runLater(() -> {
                if ("all_songs".equals(currentView)) loadAllSongs();
            });
        });

        loadPlayerBar();
        loadPlaylists();
        loadAllSongs();
        setupProfile();   // ✅ now reads from disk, not BLOB
        setupSearch();

        Platform.runLater(this::forceLayoutRefresh);
    }

    private void forceLayoutRefresh() {
        if (rootPane != null) {
            rootPane.applyCss();
            rootPane.layout();
        }
    }

    /* =========================================================
       USER PROFILE AVATAR
       ========================================================= */

    /**
     * ✅ FIX: The original read profile_picture BLOB from the User object,
     * which is always null — UserDAO.insertUser() sets pstmt.setNull(4, BLOB)
     * because images are now stored on disk, not in the database.
     *
     * Fix: load the image from the local 'images' folder using the same
     * user.dir + images/ path that SignupController and ProfileController use.
     * Image loading runs off the JAT (Task) to avoid UI stutter.
     */
    private void setupProfile() {
        User user = Session.getInstance().getCurrentUser();
        if (user == null) return;

        Task<Image> loadTask = new Task<>() {
            @Override
            protected Image call() {
                String currentDir = System.getProperty("user.dir");

                // Try user-specific image first
                File imageFile = new File(currentDir + File.separator + "images"
                        + File.separator + "profile_" + user.getUserId() + ".jpg");

                if (imageFile.exists()) {
                    return new Image(imageFile.toURI().toString());
                }

                // Fall back to default avatar
                File defaultFile = new File(currentDir + File.separator + "images"
                        + File.separator + "default_user.png");

                if (defaultFile.exists()) {
                    return new Image(defaultFile.toURI().toString());
                }

                return null; // No image available — circle stays empty
            }
        };

        loadTask.setOnSucceeded(e -> {
            Image img = loadTask.getValue();
            if (img != null && profileAvatar != null) {
                profileAvatar.setImage(img);
            }
        });

        loadTask.setOnFailed(e ->
                System.err.println("⚠️ Could not load profile avatar: "
                        + loadTask.getException().getMessage())
        );

        Thread t = new Thread(loadTask, "avatar-loader");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Called by ProfileController after the user changes their picture,
     * so the top-right avatar updates immediately without a scene reload.
     */
    public void refreshProfileAvatar() {
        setupProfile();
    }

    /* =========================================================
       SEARCH FUNCTIONALITY
       ========================================================= */
    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) ->
                handleSearch(newVal.trim()));
    }

    private void handleSearch(String query) {
        if (query.isEmpty()) {
            reloadCurrentView();
            return;
        }
        if (currentController instanceof AllSongsController) {
            SongDAO songDAO = new SongDAO();
            List<Song> results = songDAO.searchSongs(query);
            ((AllSongsController) currentController).setSongs(results);
            pageTitleLabel.setText("Search: " + query);
        }
    }

    private void reloadCurrentView() {
        switch (currentView) {
            case "all_songs"       -> loadAllSongs();
            case "liked_songs"     -> loadLikedSongs();
            case "recently_played" -> loadRecentlyPlayed();
            case "playlist"        -> loadPlaylistView();
            default                -> loadAllSongs();
        }
    }

    /* =========================================================
       PLAYER BAR (Bottom)
       ========================================================= */
    private void loadPlayerBar() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/new_better/views/player_bar.fxml"));
            Node playerBar = loader.load();

            PlayerBarController pController = loader.getController();
            pController.setQueuePanelContainer(queuePanelContainer);
            pController.setMainController(this);

            playerBarContainer.getChildren().setAll(playerBar);

            FXMLLoader queueLoader = new FXMLLoader(
                    getClass().getResource("/com/example/new_better/views/queue_panel.fxml"));
            Node queuePanel = queueLoader.load();
            queueList.getChildren().setAll(queuePanel);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* =========================================================
       QUEUE TOGGLE
       ========================================================= */
    public void toggleQueuePanel() {
        if (queuePanelContainer == null) return;
        boolean isNowVisible = !queuePanelContainer.isVisible();
        queuePanelContainer.setVisible(isNowVisible);
        queuePanelContainer.setManaged(isNowVisible);
        if (isNowVisible) {
            queuePanelContainer.setPrefWidth(350);
            queuePanelContainer.setMinWidth(350);
            queuePanelContainer.setPrefHeight(centerContent.getHeight());
            queuePanelContainer.setMinHeight(centerContent.getHeight());
            queuePanelContainer.setStyle(
                    "-fx-background-color: #1a1a1a;" +
                            "-fx-border-color: #333333;" +
                            "-fx-border-width: 0 0 0 1;"
            );
            queuePanelContainer.toFront();
        }
    }

    /* =========================================================
       SIDEBAR PLAYLISTS
       ========================================================= */
    private void loadPlaylists() {
        User user = Session.getInstance().getCurrentUser();
        if (user == null) return;

        PlaylistDAO playlistDAO = new PlaylistDAO();
        List<Map<String, Object>> playlists = playlistDAO.getUserPlaylists(user.getUserId());

        playlistList.getChildren().clear();
        for (Map<String, Object> playlist : playlists) {
            int playlistId    = (int) playlist.get("playlist_id");
            String playlistName = (String) playlist.get("playlist_name");

            Button btn = new Button(playlistName);
            btn.getStyleClass().add("playlist-item");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> loadPlaylistPage(playlistId));
            playlistList.getChildren().add(btn);
        }
    }

    public void refreshPlaylists() { loadPlaylists(); }

    /* =========================================================
       NAVIGATION
       ========================================================= */
    public void loadPlaylistPage(int playlistId) {
        try {
            searchField.clear();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/new_better/views/playlist_page.fxml"));
            Node content = loader.load();

            PlaylistPageController controller = loader.getController();
            controller.setPlaylistId(playlistId);
            controller.loadPlaylist();

            pageContentContainer.getChildren().setAll(content);
            currentController = controller;
            currentView = "playlist_page";

            PlaylistDAO dao = new PlaylistDAO();
            Map<String, Object> pl = dao.getPlaylistById(playlistId);
            if (pl != null) pageTitleLabel.setText((String) pl.get("playlist_name"));

        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void loadPlaylistView() {
        try {
            searchField.clear();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/new_better/views/playlist.fxml"));
            Node content = loader.load();

            PlaylistController controller = loader.getController();
            controller.setMainController(this);
            controller.loadUserPlaylists();

            pageContentContainer.getChildren().setAll(content);
            currentController = controller;
            currentView = "playlist";
            pageTitleLabel.setText("Your Playlists");

        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void loadAllSongs() {
        try {
            searchField.clear();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/new_better/views/all_songs.fxml"));
            Node content = loader.load();

            pageContentContainer.getChildren().setAll(content);
            currentController = loader.getController();
            currentView = "all_songs";
            pageTitleLabel.setText("All Songs");

        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void loadLikedSongs() {
        try {
            searchField.clear();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/new_better/views/liked_songs.fxml"));
            Node content = loader.load();

            pageContentContainer.getChildren().setAll(content);
            currentController = loader.getController();
            currentView = "liked_songs";
            pageTitleLabel.setText("Liked Songs");

        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void loadRecentlyPlayed() {
        try {
            searchField.clear();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/new_better/views/recently_played.fxml"));
            Node content = loader.load();

            pageContentContainer.getChildren().setAll(content);
            currentController = loader.getController();
            currentView = "recently_played";
            pageTitleLabel.setText("Recently Played");

        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void loadProfile() {
        try {
            searchField.clear();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/new_better/views/profile.fxml"));
            Node content = loader.load();

            // ✅ Pass MainController reference to ProfileController so it can
            // call refreshProfileAvatar() after the user changes their picture
            ProfileController profileController = loader.getController();
            profileController.setMainController(this);

            pageContentContainer.getChildren().setAll(content);
            currentController = profileController;
            currentView = "profile";
            pageTitleLabel.setText("Profile");

        } catch (Exception e) { e.printStackTrace(); }
    }

    /* =========================================================
       WINDOW CONTROLS
       ========================================================= */
    @FXML
    private void handleLogout() {
        Session.getInstance().logout();
        MainApp.changeScene("/com/example/new_better/views/login.fxml",
                "/com/example/new_better/css/auth.css");
    }

    @FXML
    private void handleMinimize(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleExit() {
        Platform.exit();
        System.exit(0);
    }

    @FXML
    private void handleToggle() { toggleQueuePanel(); }
}