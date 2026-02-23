package com.example.new_better.controllers;

import com.example.new_better.dao.PlaylistDAO;
import com.example.new_better.models.Song;
import com.example.new_better.models.User;
import com.example.new_better.utils.PlaylistDownloader;
import com.example.new_better.utils.Session;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class PlaylistController {

    @FXML
    private FlowPane playlistsContainer;

    private MainController mainController;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * Loads user and system playlists into the UI container.
     * iOS Level: Adds a Context Menu for triggering the Download utility.
     */
    public void loadUserPlaylists() {
        // 🔥 Fix: Add bottom padding so the last card isn't clipped or blocked by the UI edge
        playlistsContainer.setPadding(new javafx.geometry.Insets(20, 20, 80, 20));
        playlistsContainer.getChildren().clear();

        User user = Session.getInstance().getCurrentUser();
        if (user == null) return;

        PlaylistDAO playlistDAO = new PlaylistDAO();
        List<Map<String, Object>> playlists = playlistDAO.getUserPlaylists(user.getUserId());

        for (Map<String, Object> playlist : playlists) {
            int playlistId = (int) playlist.get("playlist_id");
            String playlistName = (String) playlist.get("playlist_name");
            int isSystem = (int) playlist.getOrDefault("is_system", 0);

            // createPlaylistCard now handles the sizing and pick-on-bounds logic
            VBox card = createPlaylistCard(playlistId, playlistName, isSystem);
            playlistsContainer.getChildren().add(card);
        }
    }
    private VBox createPlaylistCard(int playlistId, String playlistName, int isSystem) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("playlist-card");
        if (isSystem == 1) card.getStyleClass().add("system-card");

        // 🔥 FIX 1: setPickOnBounds(true) ensures the empty space inside the VBox is clickable.
        // Without this, clicking between the icon and the text might do nothing.
        card.setPickOnBounds(true);

        // 🔥 FIX 2: Explicit sizing ensures the card maintains a solid "hitbox" even at the end of a FlowPane.
        card.setMinWidth(140);
        card.setMinHeight(160);

        Label iconLabel = new Label(playlistName.substring(0, 1).toUpperCase());
        iconLabel.getStyleClass().add("playlist-card-icon");

        Label nameLabel = new Label(playlistName);
        nameLabel.getStyleClass().add("playlist-card-name");

        // 🔥 FIX 3: MouseTransparent(true) prevents the labels from "eating" the click event.
        // This allows the event to bubble up to the card's setOnMouseClicked listener.
        iconLabel.setMouseTransparent(true);
        nameLabel.setMouseTransparent(true);

        card.getChildren().addAll(iconLabel, nameLabel);

        // LEFT CLICK: Open Playlist Page
        card.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                // Tactile feedback: Request focus on click
                card.requestFocus();
                if (mainController != null) {
                    mainController.loadPlaylistPage(playlistId);
                }
            }
        });

        // RIGHT CLICK: iOS Context Menu (The Download Button)
        ContextMenu contextMenu = new ContextMenu();
        MenuItem downloadItem = new MenuItem("Download Playlist");
        downloadItem.setOnAction(event -> {
            showToast("Opening download location...");
            handleDownloadAction(playlistId, playlistName, isSystem == 1);
        });

        contextMenu.getItems().add(downloadItem);

        // Ensure the context menu is correctly linked to the card bounds
        card.setOnContextMenuRequested(e -> contextMenu.show(card, e.getScreenX(), e.getScreenY()));

        return card;
    }
    /**
     * Handles the call to the specialized PlaylistDownloader utility.
     */
    private void handleDownloadAction(int playlistId, String playlistName, boolean isSystem) {
        PlaylistDAO dao = new PlaylistDAO();
        List<Song> songs = dao.getPlaylistSongs(playlistId);

        // Retrieve the current stage for the DirectoryChooser dialog
        Stage stage = (Stage) playlistsContainer.getScene().getWindow();

        // Execute the real file download utility
        PlaylistDownloader.downloadPlaylist(playlistName, songs, stage, isSystem);
    }

    /**
     * Handles the iOS-styled dialog for creating a new playlist.
     */
    @FXML
    private void handleCreatePlaylist() {
        String fxmlPath = "/com/example/new_better/views/ios_dialog.fxml";
        URL location = getClass().getResource(fxmlPath);

        if (location == null) {
            System.err.println("CRITICAL ERROR: FXML file not found at " + fxmlPath);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(location);
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initStyle(StageStyle.TRANSPARENT);

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            dialogStage.setScene(scene);

            TextField input = (TextField) root.lookup("#playlistNameInput");
            Button cancelBtn = (Button) root.lookup("#cancelBtn");
            Button createBtn = (Button) root.lookup("#createBtn");

            cancelBtn.setOnAction(e -> dialogStage.close());
            createBtn.setOnAction(e -> {
                String name = input.getText().trim();
                if (!name.isEmpty()) {
                    User user = Session.getInstance().getCurrentUser();
                    if (user != null) {
                        new PlaylistDAO().createPlaylist(user.getUserId(), name, false);
                        loadUserPlaylists();
                        if (mainController != null) mainController.refreshPlaylists();
                    }
                }
                dialogStage.close();
            });

            // iOS Entrance Animation
            root.setScaleX(0.8);
            root.setScaleY(0.8);
            root.setOpacity(0);
            ScaleTransition st = new ScaleTransition(Duration.millis(200), root);
            st.setToX(1.0); st.setToY(1.0);
            FadeTransition ft = new FadeTransition(Duration.millis(200), root);
            ft.setToValue(1.0);
            dialogStage.setOnShown(e -> { st.play(); ft.play(); });

            dialogStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * iOS-style Dynamic Island Toast Notification.
     */
    private void showToast(String message) {
        Label toastLabel = new Label(message);
        toastLabel.getStyleClass().add("ios-toast");

        Platform.runLater(() -> {
            try {
                // Find the main container to overlay the toast
                StackPane root = (StackPane) playlistsContainer.getScene().getRoot();

                root.getChildren().add(toastLabel);
                StackPane.setAlignment(toastLabel, Pos.TOP_CENTER);
                toastLabel.setTranslateY(-60);

                // Animation: Slide Down
                TranslateTransition slideDown = new TranslateTransition(Duration.millis(400), toastLabel);
                slideDown.setToY(30);

                // Animation: Fade Out
                FadeTransition fadeOut = new FadeTransition(Duration.millis(500), toastLabel);
                fadeOut.setDelay(Duration.seconds(3));
                fadeOut.setToValue(0);
                fadeOut.setOnFinished(e -> root.getChildren().remove(toastLabel));

                slideDown.play();
                fadeOut.play();
            } catch (Exception e) {
                System.out.println("Toast Fallback: " + message);
            }
        });
    }
}