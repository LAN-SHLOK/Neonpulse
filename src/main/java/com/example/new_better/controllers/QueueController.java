package com.example.new_better.controllers;

import com.example.new_better.models.Song;
import com.example.new_better.utils.MusicPlayerManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

public class QueueController implements MusicPlayerManager.SongChangeListener {

    @FXML
    private VBox queueContainer;

    @FXML
    private void initialize() {
        MusicPlayerManager.getInstance().addListener(this);
        loadQueue();
    }

    private void loadQueue() {
        queueContainer.getChildren().clear();
        List<Song> queue = MusicPlayerManager.getInstance().getQueue();
        Song currentSong = MusicPlayerManager.getInstance().getCurrentSong();

        if (queue.isEmpty()) {
            VBox emptyBox = new VBox(12);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(60, 20, 40, 20));

            Label emptyIcon = new Label("♫");
            emptyIcon.setStyle("-fx-font-size: 48px; -fx-text-fill: rgba(10,132,255,0.25);");

            Label emptyTitle = new Label("Queue is empty");
            emptyTitle.getStyleClass().add("queue-title");

            Label emptySubtitle = new Label("Add songs using the + button");
            emptySubtitle.getStyleClass().add("empty-label");

            emptyBox.getChildren().addAll(emptyIcon, emptyTitle, emptySubtitle);
            queueContainer.getChildren().add(emptyBox);
            return;
        }

        // Queue count header
        Label countLabel = new Label(queue.size() + " songs in queue");
        countLabel.getStyleClass().add("sidebar-header");
        countLabel.setPadding(new Insets(0, 0, 8, 4));
        queueContainer.getChildren().add(countLabel);

        for (int i = 0; i < queue.size(); i++) {
            Song song = queue.get(i);
            boolean isCurrent = song.equals(currentSong);
            int finalI = i;

            // --- Row ---
            HBox queueRow = new HBox(12);
            queueRow.setAlignment(Pos.CENTER_LEFT);
            queueRow.setMaxWidth(Double.MAX_VALUE);
            queueRow.getStyleClass().add("queue-row");

            if (isCurrent) {
                queueRow.getStyleClass().add("current-song");
            } else {
                // CSS handles hover via .queue-row:hover, no Java listeners needed
            }

            queueRow.setOnMouseClicked(e -> MusicPlayerManager.getInstance().playFromQueue(finalI));

            // --- Index badge ---
            Label indexLabel = new Label(String.valueOf(i + 1));
            indexLabel.setMinWidth(30);
            indexLabel.setMaxWidth(30);
            indexLabel.setMinHeight(30);
            indexLabel.setMaxHeight(30);
            indexLabel.setAlignment(Pos.CENTER);
            indexLabel.getStyleClass().add("queue-index");

            // Keep the blue circle for current song via inline (no CSS class covers this shape variant)
            if (isCurrent) {
                indexLabel.setStyle(
                        "-fx-background-color: #0A84FF; " +
                                "-fx-background-radius: 50%; " +
                                "-fx-text-fill: white; " +
                                "-fx-font-size: 11px; -fx-font-weight: bold;"
                );
            } else {
                indexLabel.setStyle(
                        "-fx-background-color: rgba(255,255,255,0.07); " +
                                "-fx-background-radius: 50%;"
                );
            }

            // --- Song info ---
            VBox songInfo = new VBox(3);
            HBox.setHgrow(songInfo, Priority.ALWAYS);

            Label titleLabel = new Label(song.getTitle());
            titleLabel.setMaxWidth(170);
            titleLabel.getStyleClass().add("queue-title");

            if (isCurrent) {
                titleLabel.setStyle("-fx-text-fill: #0A84FF; -fx-font-weight: bold;");
            }

            // --- Meta row ---
            HBox metaRow = new HBox(6);
            metaRow.setAlignment(Pos.CENTER_LEFT);

            Label genreLabel = new Label(song.getGenre());
            genreLabel.getStyleClass().add("queue-genre");

            metaRow.getChildren().add(genreLabel);

            if (isCurrent) {
                Label playingLabel = new Label("▶ NOW PLAYING");
                playingLabel.setStyle(
                        "-fx-text-fill: #0A84FF; -fx-font-size: 9px; -fx-font-weight: bold; " +
                                "-fx-background-color: rgba(10,132,255,0.15); " +
                                "-fx-background-radius: 4px; -fx-padding: 2px 6px;"
                );
                metaRow.getChildren().add(playingLabel);
            }

            songInfo.getChildren().addAll(titleLabel, metaRow);

            // --- Remove button ---
            Button removeBtn = new Button("✕");
            removeBtn.getStyleClass().add("remove-btn");
            removeBtn.setOnAction(e -> {
                MusicPlayerManager.getInstance().removeFromQueue(song);
                loadQueue();
            });

            queueRow.getChildren().addAll(indexLabel, songInfo, removeBtn);
            queueContainer.getChildren().add(queueRow);

            if (i < queue.size() - 1) {
                Region spacer = new Region();
                spacer.setPrefHeight(5);
                queueContainer.getChildren().add(spacer);
            }
        }
    }

    @FXML
    private void handleClearQueue() {
        MusicPlayerManager.getInstance().clearQueue();
        loadQueue();
    }

    @Override
    public void onSongChanged(Song song) {
        Platform.runLater(this::loadQueue);
    }
}