package com.example.new_better.controllers;

import com.example.new_better.dao.LikedSongsDAO;
import com.example.new_better.dao.PlaylistDAO;
import com.example.new_better.dao.SongDAO;
import com.example.new_better.models.Song;
import com.example.new_better.models.User;
import com.example.new_better.utils.MusicPlayerManager;
import com.example.new_better.utils.Session;
import com.example.new_better.utils.SongFolderImporter; // ✅ IMPORTED THIS
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class AllSongsController {

    @FXML private VBox songListContainer;

    private List<Song> songs;
    private LikedSongsDAO likedSongsDAO;
    private PlaylistDAO playlistDAO;
    private SongDAO songDAO;

    @FXML
    private void initialize() {
        likedSongsDAO = new LikedSongsDAO();
        playlistDAO = new PlaylistDAO();
        songDAO = new SongDAO();
        loadSongs();
        repairZeroDurations();
    }

    private void loadSongs() {
        songs = songDAO.getAllSongs();
        if (songs == null) songs = List.of();
        displaySongs();
    }

    public void setSongs(List<Song> newSongs) {
        this.songs = (newSongs == null) ? List.of() : newSongs;
        displaySongs();
    }

    // ✅ FIXED: Now uses the dynamic 'songs' folder path to find files
    private void repairZeroDurations() {
        new Thread(() -> {
            boolean updated = false;
            String baseDir = SongFolderImporter.getSavedSongsDir(); // Get the portable path

            for (Song song : songs) {
                if (song.getDuration() <= 0) {
                    File file;
                    // Check if path is legacy (Absolute) or Portable (Relative)
                    if (song.getFilePath().contains(":\\") || song.getFilePath().startsWith("/")) {
                        file = new File(song.getFilePath());
                        // Fallback: If absolute path fails, check relative to baseDir
                        if (!file.exists()) file = new File(baseDir, song.getFilePath());
                    } else {
                        // Standard Portable Path: Combine baseDir + relative path
                        file = new File(baseDir, song.getFilePath());
                    }

                    if (file.exists()) {
                        double estimatedTime = file.length() / 16000.0;
                        song.setDuration(estimatedTime);
                        songDAO.updateSongDuration(song.getSongId(), estimatedTime);
                        updated = true;
                    }
                }
            }
            if (updated) {
                Platform.runLater(this::displaySongs);
            }
        }).start();
    }

    private void displaySongs() {
        songListContainer.getChildren().clear();

        if (songs == null || songs.isEmpty()) {
            Label emptyLabel = new Label("No songs available");
            emptyLabel.getStyleClass().add("empty-label");
            songListContainer.getChildren().add(emptyLabel);
            return;
        }

        User user = Session.getInstance().getCurrentUser();

        for (Song song : songs) {
            HBox songRow = new HBox(15);
            songRow.setAlignment(Pos.CENTER_LEFT);
            songRow.getStyleClass().add("song-row");
            songRow.setPadding(new Insets(10, 20, 10, 15));

            Button playBtn = new Button();
            playBtn.getStyleClass().add("ios-btn");
            playBtn.setMinWidth(40);
            playBtn.setMaxWidth(40);
            Region playIcon = new Region();
            playIcon.getStyleClass().addAll("icon-region", "icon-play");
            playBtn.setGraphic(playIcon);
            playBtn.setOnAction(e -> {
                e.consume(); // ✅ prevent row click from also firing
                playSong(song);
            });

            Label titleLabel = new Label(song.getTitle());
            titleLabel.getStyleClass().add("song-title");
            titleLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(titleLabel, Priority.ALWAYS);

            Label genreLabel = new Label(song.getGenre());
            genreLabel.getStyleClass().add("song-genre");
            genreLabel.setPrefWidth(100);
            genreLabel.setMinWidth(100);

            String durationText = song.getFormattedDuration();
            if (song.getDuration() == 0) durationText = "--:--";
            Label durationLabel = new Label(durationText);
            durationLabel.getStyleClass().add("song-duration");
            durationLabel.setPrefWidth(60);
            durationLabel.setMinWidth(60);

            Button addBtn = new Button();
            addBtn.getStyleClass().add("ios-btn");
            addBtn.setMinWidth(40);
            Region addIcon = new Region();
            addIcon.getStyleClass().addAll("icon-region", "icon-plus");
            addBtn.setGraphic(addIcon);
            addBtn.setOnAction(e -> {
                e.consume(); // ✅ prevent row click from also firing
                showPlaylistDialog(song);
            });

            Button likeBtn = new Button();
            likeBtn.getStyleClass().add("ios-btn");
            likeBtn.setMinWidth(40);
            Region likeIcon = new Region();
            likeIcon.getStyleClass().addAll("icon-region", "icon-heart");
            likeBtn.setGraphic(likeIcon);

            boolean isLiked = false;
            if (user != null) {
                isLiked = likedSongsDAO.isLiked(user.getUserId(), song.getSongId());
            }
            if (isLiked) likeBtn.getStyleClass().add("liked");
            likeBtn.setOnAction(e -> {
                e.consume(); // ✅ prevent row click from also firing
                toggleLike(song, likeBtn);
            });

            // ✅ Row click plays the song
            songRow.setOnMouseClicked(e -> playSong(song));

            songRow.getChildren().addAll(playBtn, titleLabel, genreLabel, durationLabel, addBtn, likeBtn);
            songListContainer.getChildren().add(songRow);
        }
    }
    private void playSong(Song song) {
        MusicPlayerManager.getInstance().setQueue(songs);
        MusicPlayerManager.getInstance().playFromQueue(songs.indexOf(song));
    }

    private void toggleLike(Song song, Button likeBtn) {
        User user = Session.getInstance().getCurrentUser();
        if (user == null) return;
        boolean isLiked = likedSongsDAO.isLiked(user.getUserId(), song.getSongId());
        if (isLiked) {
            likedSongsDAO.unlikeSong(user.getUserId(), song.getSongId());
            likeBtn.getStyleClass().remove("liked");
        } else {
            likedSongsDAO.likeSong(user.getUserId(), song.getSongId());
            if (!likeBtn.getStyleClass().contains("liked")) likeBtn.getStyleClass().add("liked");
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  CUSTOM: Add to Playlist dialog
    // ─────────────────────────────────────────────────────────────
    private void showPlaylistDialog(Song song) {
        Stage choiceStage = buildDialogStage(songListContainer);

        Label titleLbl = new Label("Add Song");
        titleLbl.setStyle(
                "-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; " +
                        "-fx-font-family: 'SF Pro Display', 'Segoe UI', sans-serif;"
        );
        Label subLbl = new Label("\"" + song.getTitle() + "\"");
        subLbl.setStyle(
                "-fx-text-fill: rgba(255,255,255,0.75); -fx-font-size: 12px; " +
                        "-fx-font-family: 'SF Pro Text', 'Segoe UI', sans-serif;"
        );
        subLbl.setWrapText(true);
        subLbl.setMaxWidth(300);

        VBox headerBox = new VBox(5, titleLbl, subLbl);
        headerBox.setPadding(new Insets(20, 24, 16, 24));
        headerBox.setStyle(
                "-fx-background-color: linear-gradient(to right, #0A84FF, #30d5f5); " +
                        "-fx-background-radius: 18px 18px 0 0;"
        );

        Button addToQueueBtn = buildDialogButton("Add to Queue", true);
        Button addToPlaylistBtn = buildDialogButton("Add to Playlist", false);

        addToQueueBtn.setMaxWidth(Double.MAX_VALUE);
        addToPlaylistBtn.setMaxWidth(Double.MAX_VALUE);

        VBox choiceBox = new VBox(10, addToQueueBtn, addToPlaylistBtn);
        choiceBox.setPadding(new Insets(20, 24, 20, 24));

        Button cancelBtn = buildDialogButton("Cancel", false);
        cancelBtn.setOnAction(e -> choiceStage.close());

        HBox btnRow = new HBox(cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(0, 20, 18, 20));

        Region divider = new Region();
        divider.setMinHeight(1);
        divider.setStyle("-fx-background-color: rgba(255,255,255,0.08);");

        VBox card = new VBox(headerBox, choiceBox, divider, btnRow);
        card.setStyle(
                "-fx-background-color: #141420; " +
                        "-fx-background-radius: 18px; " +
                        "-fx-effect: dropshadow(gaussian, rgba(10,132,255,0.55), 36, 0, 0, 0);"
        );
        card.setMaxWidth(360);
        card.setMinWidth(320);

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: transparent;");
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        choiceStage.setScene(scene);
        choiceStage.show();

        addToQueueBtn.setOnAction(e -> {
            choiceStage.close();
            MusicPlayerManager.getInstance().addToQueue(song);
            showAlert("Added to Queue!", "\"" + song.getTitle() + "\" added to queue.");
        });

        addToPlaylistBtn.setOnAction(e -> {
            choiceStage.close();
            showPlaylistDialogFor(song, playlistDAO, songListContainer, null);
        });
    }

    void showAlert(String header, String message) {
        Stage stage = buildDialogStage(songListContainer);

        Label titleLbl = new Label(header);
        titleLbl.setStyle(
                "-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; " +
                        "-fx-font-family: 'SF Pro Display', 'Segoe UI', sans-serif;"
        );
        VBox headerBox = new VBox(titleLbl);
        headerBox.setPadding(new Insets(22, 24, 18, 24));
        headerBox.setStyle(
                "-fx-background-color: linear-gradient(to right, #0A84FF, #30d5f5); " +
                        "-fx-background-radius: 18px 18px 0 0;"
        );

        Label msgLbl = new Label(message);
        msgLbl.setStyle(
                "-fx-text-fill: #c8c8d0; -fx-font-size: 13px; " +
                        "-fx-font-family: 'SF Pro Text', 'Segoe UI', sans-serif;"
        );
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(300);

        VBox body = new VBox(msgLbl);
        body.setPadding(new Insets(16, 24, 10, 24));

        Region divider = new Region();
        divider.setMinHeight(1);
        divider.setStyle("-fx-background-color: rgba(255,255,255,0.08);");

        Button okBtn = buildDialogButton("OK", true);
        okBtn.setOnAction(e -> stage.close());

        HBox btnRow = new HBox(okBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(12, 20, 18, 20));

        VBox card = new VBox(headerBox, body, divider, btnRow);
        card.setStyle(
                "-fx-background-color: #141420; " +
                        "-fx-background-radius: 18px; " +
                        "-fx-effect: dropshadow(gaussian, rgba(10,132,255,0.55), 36, 0, 0, 0);"
        );
        card.setMaxWidth(340);
        card.setMinWidth(280);

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: transparent;");
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.show();
    }

    private void showAlert(String message) {
        showAlert("Notification", message);
    }

    // ─────────────────────────────────────────────────────────────
    //  Shared static-style helpers
    // ─────────────────────────────────────────────────────────────

    static Stage buildDialogStage(Region ownerNode) {
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.WINDOW_MODAL);
        if (ownerNode != null && ownerNode.getScene() != null) {
            stage.initOwner(ownerNode.getScene().getWindow());
        }
        return stage;
    }

    static Button buildDialogButton(String text, boolean primary) {
        Button btn = new Button(text);
        String base = primary
                ? "-fx-background-color: linear-gradient(to bottom, #2196f3, #0A84FF); " +
                "-fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-effect: dropshadow(gaussian, rgba(10,132,255,0.45), 8, 0, 0, 2);"
                : "-fx-background-color: rgba(255,255,255,0.08); " +
                "-fx-text-fill: #aaaaaa; -fx-font-weight: normal;";
        String hover = primary
                ? "-fx-background-color: linear-gradient(to bottom, #42a5f5, #1e90ff); " +
                "-fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-effect: dropshadow(gaussian, rgba(10,132,255,0.7), 12, 0, 0, 3);"
                : "-fx-background-color: rgba(255,255,255,0.15); " +
                "-fx-text-fill: white; -fx-font-weight: normal;";
        String common =
                "-fx-font-size: 13px; -fx-font-family: 'SF Pro Text', 'Segoe UI', sans-serif; " +
                        "-fx-background-radius: 10px; -fx-padding: 9px 22px; -fx-cursor: hand;";
        btn.setStyle(base + common);
        btn.setOnMouseEntered(e -> btn.setStyle(hover + common));
        btn.setOnMouseExited(e -> btn.setStyle(base + common));
        return btn;
    }

    static String rowStyle(boolean selected) {
        return selected
                ? "-fx-background-color: rgba(10,132,255,0.20); " +
                "-fx-text-fill: #4fc3f7; -fx-font-weight: bold; -fx-font-size: 13px; " +
                "-fx-font-family: 'SF Pro Text', 'Segoe UI', sans-serif; " +
                "-fx-background-radius: 10px; -fx-padding: 10px 14px; -fx-cursor: hand; " +
                "-fx-border-color: #0A84FF; -fx-border-width: 1.5px; -fx-border-radius: 10px;"
                : "-fx-background-color: rgba(255,255,255,0.04); " +
                "-fx-text-fill: #d0d0d8; -fx-font-weight: normal; -fx-font-size: 13px; " +
                "-fx-font-family: 'SF Pro Text', 'Segoe UI', sans-serif; " +
                "-fx-background-radius: 10px; -fx-padding: 10px 14px; -fx-cursor: hand; " +
                "-fx-border-color: rgba(255,255,255,0.06); -fx-border-width: 1px; -fx-border-radius: 10px;";
    }

    static String rowHoverStyle() {
        return "-fx-background-color: rgba(255,255,255,0.09); " +
                "-fx-text-fill: white; -fx-font-weight: normal; -fx-font-size: 13px; " +
                "-fx-font-family: 'SF Pro Text', 'Segoe UI', sans-serif; " +
                "-fx-background-radius: 10px; -fx-padding: 10px 14px; -fx-cursor: hand; " +
                "-fx-border-color: rgba(255,255,255,0.12); -fx-border-width: 1px; -fx-border-radius: 10px;";
    }

    static void showPlaylistDialogFor(Song song, PlaylistDAO playlistDAO, Region ownerNode, Runnable onAdded) {
        User user = Session.getInstance().getCurrentUser();
        if (user == null) return;

        List<Map<String, Object>> playlists = playlistDAO.getUserPlaylists(user.getUserId());
        if (playlists.isEmpty()) {
            showAlertStatic("No Playlists Found", "Create a playlist first before adding songs.", ownerNode);
            return;
        }

        Stage stage = buildDialogStage(ownerNode);

        Label titleLbl = new Label("Add to Playlist");
        titleLbl.setStyle(
                "-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; " +
                        "-fx-font-family: 'SF Pro Display', 'Segoe UI', sans-serif;"
        );
        Label subLbl = new Label("\"" + song.getTitle() + "\"");
        subLbl.setStyle(
                "-fx-text-fill: rgba(255,255,255,0.75); -fx-font-size: 12px; " +
                        "-fx-font-family: 'SF Pro Text', 'Segoe UI', sans-serif;"
        );
        subLbl.setWrapText(true);
        subLbl.setMaxWidth(300);

        VBox headerBox = new VBox(5, titleLbl, subLbl);
        headerBox.setPadding(new Insets(20, 24, 16, 24));
        headerBox.setStyle(
                "-fx-background-color: linear-gradient(to right, #0A84FF, #30d5f5); " +
                        "-fx-background-radius: 18px 18px 0 0;"
        );

        VBox listBox = new VBox(8);
        listBox.setPadding(new Insets(16, 16, 16, 16));

        ToggleGroup toggleGroup = new ToggleGroup();
        AtomicReference<String> selectedName = new AtomicReference<>(
                (String) playlists.get(0).get("playlist_name")
        );

        for (Map<String, Object> pl : playlists) {
            String name = (String) pl.get("playlist_name");
            ToggleButton row = new ToggleButton(name);
            row.setToggleGroup(toggleGroup);
            row.setMaxWidth(Double.MAX_VALUE);
            row.setAlignment(Pos.CENTER_LEFT);
            boolean isFirst = name.equals(selectedName.get());
            row.setSelected(isFirst);
            row.setStyle(rowStyle(isFirst));

            row.selectedProperty().addListener((obs, wasOn, isOn) -> {
                row.setStyle(rowStyle(isOn));
                if (isOn) selectedName.set(name);
            });
            row.setOnMouseEntered(e -> { if (!row.isSelected()) row.setStyle(rowHoverStyle()); });
            row.setOnMouseExited(e -> { if (!row.isSelected()) row.setStyle(rowStyle(false)); });
            listBox.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(listBox);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(200);
        scroll.setMinHeight(60);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background: #141420; -fx-background-color: #141420; -fx-border-color: transparent;");

        Platform.runLater(() -> {
            scroll.lookupAll(".scroll-bar").forEach(node ->
                    node.setStyle("-fx-background-color: transparent;")
            );
            scroll.lookupAll(".scroll-bar:vertical .thumb").forEach(node ->
                    node.setStyle("-fx-background-color: rgba(10,132,255,0.5); -fx-background-radius: 6px;")
            );
            scroll.lookupAll(".scroll-bar:vertical .track").forEach(node ->
                    node.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 6px;")
            );
            scroll.lookupAll(".increment-button, .decrement-button").forEach(node ->
                    node.setStyle("-fx-background-color: transparent; -fx-padding: 0;")
            );
            scroll.lookupAll(".increment-arrow, .decrement-arrow").forEach(node ->
                    node.setStyle("-fx-background-color: transparent; -fx-padding: 0;")
            );
        });

        Region divider = new Region();
        divider.setMinHeight(1);
        divider.setStyle("-fx-background-color: rgba(255,255,255,0.08);");

        Button cancelBtn = buildDialogButton("Cancel", false);
        Button confirmBtn = buildDialogButton("Add", true);

        cancelBtn.setOnAction(e -> stage.close());
        confirmBtn.setOnAction(e -> {
            String chosen = selectedName.get();
            stage.close();
            Platform.runLater(() -> {
                for (Map<String, Object> pl : playlists) {
                    if (pl.get("playlist_name").equals(chosen)) {
                        playlistDAO.addSongToPlaylist((int) pl.get("playlist_id"), song.getSongId());
                        showAlertStatic("Added!", "\"" + song.getTitle() + "\" added to " + chosen + ".", ownerNode);
                        if (onAdded != null) onAdded.run();
                        break;
                    }
                }
            });
        });

        HBox btnRow = new HBox(10, cancelBtn, confirmBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(12, 20, 18, 20));

        VBox card = new VBox(headerBox, scroll, divider, btnRow);
        card.setStyle(
                "-fx-background-color: #141420; " +
                        "-fx-background-radius: 18px; " +
                        "-fx-effect: dropshadow(gaussian, rgba(10,132,255,0.55), 36, 0, 0, 0);"
        );
        card.setMaxWidth(360);
        card.setMinWidth(320);

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: transparent;");
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.show();
    }

    static void showAlertStatic(String header, String message, Region ownerNode) {
        Stage stage = buildDialogStage(ownerNode);

        Label titleLbl = new Label(header);
        titleLbl.setStyle(
                "-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; " +
                        "-fx-font-family: 'SF Pro Display', 'Segoe UI', sans-serif;"
        );
        VBox headerBox = new VBox(titleLbl);
        headerBox.setPadding(new Insets(22, 24, 18, 24));
        headerBox.setStyle(
                "-fx-background-color: linear-gradient(to right, #0A84FF, #30d5f5); " +
                        "-fx-background-radius: 18px 18px 0 0;"
        );

        Label msgLbl = new Label(message);
        msgLbl.setStyle(
                "-fx-text-fill: #c8c8d0; -fx-font-size: 13px; " +
                        "-fx-font-family: 'SF Pro Text', 'Segoe UI', sans-serif;"
        );
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(300);

        VBox body = new VBox(msgLbl);
        body.setPadding(new Insets(16, 24, 10, 24));

        Region divider = new Region();
        divider.setMinHeight(1);
        divider.setStyle("-fx-background-color: rgba(255,255,255,0.08);");

        Button okBtn = buildDialogButton("OK", true);
        okBtn.setOnAction(e -> stage.close());

        HBox btnRow = new HBox(okBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(12, 20, 18, 20));

        VBox card = new VBox(headerBox, body, divider, btnRow);
        card.setStyle(
                "-fx-background-color: #141420; " +
                        "-fx-background-radius: 18px; " +
                        "-fx-effect: dropshadow(gaussian, rgba(10,132,255,0.55), 36, 0, 0, 0);"
        );
        card.setMaxWidth(340);
        card.setMinWidth(280);

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: transparent;");
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.show();
    }
}