package com.example.new_better.utils;

import com.example.new_better.models.Song;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class PlaylistDownloader {

    public static void downloadPlaylist(String playlistName, List<Song> songs, Stage ownerStage, boolean isSystemPlaylist) {

        if (songs == null || songs.isEmpty()) {
            showAlert("Empty Playlist", "This playlist has no songs to download.", ownerStage, true);
            return;
        }

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Download Location");
        File selectedDirectory = directoryChooser.showDialog(ownerStage);

        if (selectedDirectory != null) {
            Task<Void> task = new Task<Void>() {
                @Override
                protected Void call() {
                    String folderName = sanitizeFileName(playlistName);
                    Path playlistFolder = Paths.get(selectedDirectory.getAbsolutePath(), folderName);

                    try {
                        if (!Files.exists(playlistFolder)) {
                            Files.createDirectories(playlistFolder);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Could not create folder: " + e.getMessage());
                    }

                    // ✅ PORTABLE FIX: Get the path to the 'songs' folder inside the installer
                    String sourceBaseDir = SongFolderImporter.getSavedSongsDir();
                    List<String> failedSongs = new ArrayList<>();
                    int successCount = 0;

                    for (Song song : songs) {
                        try {
                            File sourceFile;

                            // 1. Check if the path is absolute (Legacy support)
                            if (song.getFilePath().contains(":\\") || song.getFilePath().startsWith("/")) {
                                sourceFile = new File(song.getFilePath());
                                // If absolute path doesn't exist, try looking in relative folder
                                if (!sourceFile.exists()) {
                                    sourceFile = new File(sourceBaseDir, song.getFilePath());
                                }
                            } else {
                                // 2. Standard Relative Path (Best for portable app)
                                sourceFile = new File(sourceBaseDir, song.getFilePath());
                            }

                            if (sourceFile.exists()) {
                                String fileName = sourceFile.getName();
                                Path destPath = playlistFolder.resolve(fileName);

                                // Handle duplicate file names
                                int counter = 1;
                                while (Files.exists(destPath)) {
                                    String nameWithoutExt = fileName.contains(".")
                                            ? fileName.substring(0, fileName.lastIndexOf('.'))
                                            : fileName;
                                    String ext = fileName.contains(".")
                                            ? fileName.substring(fileName.lastIndexOf('.'))
                                            : "";
                                    destPath = playlistFolder.resolve(nameWithoutExt + "_" + counter + ext);
                                    counter++;
                                }

                                Files.copy(sourceFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
                                successCount++;
                            } else {
                                System.err.println("❌ Song file missing: " + sourceFile.getAbsolutePath());
                                failedSongs.add(song.getTitle());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            failedSongs.add(song.getTitle());
                        }
                    }

                    // If NO songs were downloaded, throw error
                    if (successCount == 0 && !songs.isEmpty()) {
                        throw new RuntimeException("Could not find any song files. Check 'songs' folder.");
                    }

                    return null;
                }

                @Override
                protected void succeeded() {
                    Platform.runLater(() ->
                            showAlert(
                                    "Download Complete",
                                    "Playlist \"" + playlistName + "\" saved successfully!\n" +
                                            "Location: " + selectedDirectory.getAbsolutePath(),
                                    ownerStage,
                                    false
                            )
                    );
                }

                @Override
                protected void failed() {
                    Platform.runLater(() ->
                            showAlert(
                                    "Download Issue",
                                    "Some songs could not be downloaded.\nCheck logs for details.",
                                    ownerStage,
                                    true
                            )
                    );
                }
            };

            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  CUSTOM UI ALERTS (Unchanged Style)
    // ─────────────────────────────────────────────────────────────
    private static void showAlert(String header, String message, Stage ownerStage, boolean isError) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showAlert(header, message, ownerStage, isError));
            return;
        }

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.WINDOW_MODAL);
        if (ownerStage != null) stage.initOwner(ownerStage);

        String headerGradient = isError
                ? "-fx-background-color: linear-gradient(to right, #ff3b30, #ff6b6b);"
                : "-fx-background-color: linear-gradient(to right, #0A84FF, #30d5f5);";

        Label iconLbl = new Label(isError ? "✕" : "✓");
        iconLbl.setStyle(
                "-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; " +
                        "-fx-background-color: rgba(255,255,255,0.20); -fx-background-radius: 50%; " +
                        "-fx-min-width: 36px; -fx-min-height: 36px; -fx-max-width: 36px; -fx-max-height: 36px; -fx-alignment: center;"
        );

        Label titleLbl = new Label(header);
        titleLbl.setStyle("-fx-text-fill: white; -fx-font-size: 17px; -fx-font-weight: bold; -fx-font-family: 'SF Pro Display', 'Segoe UI', sans-serif;");

        HBox headerBox = new HBox(12, iconLbl, titleLbl);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(20, 24, 18, 24));
        headerBox.setStyle(headerGradient + " -fx-background-radius: 18px 18px 0 0;");

        Label msgLbl = new Label(message);
        msgLbl.setStyle("-fx-text-fill: #c8c8d0; -fx-font-size: 13px; -fx-font-family: 'SF Pro Text', 'Segoe UI', sans-serif;");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(300);

        VBox body = new VBox(msgLbl);
        body.setPadding(new Insets(16, 24, 10, 24));

        Region divider = new Region();
        divider.setMinHeight(1);
        divider.setStyle("-fx-background-color: rgba(255,255,255,0.08);");

        String btnColor = isError ? "-fx-background-color: linear-gradient(to bottom, #ff5e57, #ff3b30);" : "-fx-background-color: linear-gradient(to bottom, #2196f3, #0A84FF);";
        String btnCommon = "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 10px; -fx-padding: 9px 28px; -fx-cursor: hand;";

        Button okBtn = new Button("OK");
        okBtn.setStyle(btnColor + btnCommon);
        okBtn.setOnAction(e -> stage.close());

        HBox btnRow = new HBox(okBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(12, 20, 18, 20));

        VBox card = new VBox(headerBox, body, divider, btnRow);
        card.setStyle("-fx-background-color: #141420; -fx-background-radius: 18px; -fx-effect: dropshadow(gaussian, " + (isError ? "rgba(255,59,48,0.45)" : "rgba(10,132,255,0.55)") + ", 36, 0, 0, 0);");

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: transparent;");
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.show();
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9.\\- ]", "_").trim();
    }
}