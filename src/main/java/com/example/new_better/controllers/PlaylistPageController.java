package com.example.new_better.controllers;

import com.example.new_better.dao.PlaylistDAO;
import com.example.new_better.dao.SongDAO;
import com.example.new_better.models.Song;
import com.example.new_better.utils.MusicPlayerManager;
import com.example.new_better.utils.PlaylistDownloader;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;

public class PlaylistPageController {

    @FXML private VBox songListContainer;
    @FXML private Button infinityBtn;
    @FXML private Button downloadBtn;

    private int playlistId;
    private String playlistName;
    private boolean isSystem;
    private boolean infinityMode = false;
    private List<Song> currentSongs;

    private PlaylistDAO playlistDAO;
    private SongDAO songDAO;

    @FXML
    private void initialize() {
        // ✅ Deployment Ready: These DAOs now use dynamic DB paths
        playlistDAO = new PlaylistDAO();
        songDAO = new SongDAO();
        Tooltip.install(infinityBtn, new Tooltip("Loop entire playlist"));
    }

    public void setPlaylistId(int playlistId) {
        this.playlistId = playlistId;
    }

    public void loadPlaylist() {
        if (songListContainer == null) return;
        songListContainer.getChildren().clear();

        // 1. Get Metadata from synchronized DB
        Map<String, Object> details = playlistDAO.getPlaylistById(playlistId);
        if (details == null) return;

        this.playlistName = (String) details.get("playlist_name");
        int systemFlag = (int) details.getOrDefault("is_system", 0);
        this.isSystem = (systemFlag == 1);

        // 2. Load Songs based on deployment-safe logic
        if (isSystem) {
            currentSongs = songDAO.getSongsByGenre(playlistName);
        } else {
            currentSongs = playlistDAO.getPlaylistSongs(playlistId);
        }

        if (currentSongs == null || currentSongs.isEmpty()) {
            Label empty = new Label("No songs in this playlist");
            empty.getStyleClass().add("empty-label");
            songListContainer.getChildren().add(empty);
            return;
        }

        // Pass 'isSystem' to hide the trash button for system playlists (like "Pop", "Sad")
        displaySongs(isSystem);
    }

    private void displaySongs(boolean hideRemove) {
        for (Song song : currentSongs) {

            HBox songRow = new HBox(15);
            songRow.setAlignment(Pos.CENTER_LEFT);
            songRow.getStyleClass().add("song-row");
            songRow.setPadding(new javafx.geometry.Insets(10, 20, 10, 15));

            // PLAY BUTTON
            Button playBtn = new Button();
            playBtn.getStyleClass().add("ios-btn");
            playBtn.setMinWidth(40);
            Region playIcon = new Region();
            playIcon.getStyleClass().addAll("icon-region", "icon-play");
            playBtn.setGraphic(playIcon);
            playBtn.setOnAction(e -> {
                e.consume(); // ✅ stop bubbling to row
                playFromPlaylist(song);
            });

            // TITLE
            Label titleLabel = new Label(song.getTitle());
            titleLabel.getStyleClass().add("song-title");
            titleLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(titleLabel, Priority.ALWAYS);

            // GENRE
            Label genreLabel = new Label(song.getGenre());
            genreLabel.getStyleClass().add("song-genre");
            genreLabel.setMinWidth(100);

            // DURATION
            Label durationLabel = new Label(song.getFormattedDuration());
            durationLabel.getStyleClass().add("song-duration");
            durationLabel.setMinWidth(60);

            songRow.getChildren().addAll(playBtn, titleLabel, genreLabel, durationLabel);

            // 🔥 TRASH BUTTON: Only for User-created Playlists
            if (!hideRemove) {
                Button removeBtn = new Button();
                removeBtn.getStyleClass().add("ios-btn");
                removeBtn.setMinWidth(40);

                Region removeIcon = new Region();
                removeIcon.getStyleClass().addAll("icon-region", "icon-trash");
                removeBtn.setGraphic(removeIcon);

                removeBtn.setOnAction(e -> {
                    e.consume(); // ✅ stop bubbling to row
                    playlistDAO.removeSongFromPlaylist(playlistId, song.getSongId());
                    loadPlaylist();
                });

                songRow.getChildren().add(removeBtn);
            }

            // ✅ Row click plays the song
            songRow.setOnMouseClicked(e -> playFromPlaylist(song));

            songListContainer.getChildren().add(songRow);
        }
    }

    private void playFromPlaylist(Song song) {
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        manager.setQueue(currentSongs);
        manager.playFromQueue(currentSongs.indexOf(song));

        if (infinityMode) {
            manager.setRepeat(true);
        }
    }

    @FXML
    private void handleDownload() {
        if (currentSongs == null || currentSongs.isEmpty()) return;

        // Deployment Fix: Center the directory chooser on the app window
        Stage stage = (Stage) songListContainer.getScene().getWindow();

        // Call the refactored Utility Class for safe file copying
        PlaylistDownloader.downloadPlaylist(
                playlistName,
                currentSongs,
                stage,
                isSystem
        );
    }

    @FXML
    private void handleInfinityMode() {
        infinityMode = !infinityMode;
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        manager.setRepeat(infinityMode);
        // Toggle visual blue state for "active" mode
        infinityBtn.setStyle(infinityMode ? "-fx-text-fill:#00d9ff;" : "");
    }

    @FXML
    private void handleJumpAhead() {
        MediaPlayer player = MusicPlayerManager.getInstance().getMediaPlayer();
        if (player != null) {
            Duration newTime = player.getCurrentTime().add(Duration.seconds(10));
            player.seek(newTime.greaterThan(player.getTotalDuration()) ? player.getTotalDuration() : newTime);
        }
    }

    @FXML
    private void handleJumpBack() {
        MediaPlayer player = MusicPlayerManager.getInstance().getMediaPlayer();
        if (player != null) {
            Duration newTime = player.getCurrentTime().subtract(Duration.seconds(10));
            player.seek(newTime.lessThan(Duration.ZERO) ? Duration.ZERO : newTime);
        }
    }
}