package com.example.new_better.utils;

import com.example.new_better.dao.SongDAO;
import com.example.new_better.models.Song;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.stage.Stage;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class SongFolderImporter {

    private static final String APP_DIR     = System.getProperty("user.dir");
    private static final String CONFIG_FILE = APP_DIR + File.separator + "config.properties";
    private static final String SONGS_DIR   = APP_DIR + File.separator + "songs";

    private static final String[] GENRES = {"party", "pop", "classical", "romantic", "sad"};
    private static final List<Runnable> importListeners = new ArrayList<>();

    // ✅ FIX: Guard flag — prevents duplicate import tasks running simultaneously.
    // AtomicBoolean is thread-safe without needing synchronized blocks.
    // Without this, MainApp.start() AND LoginController.succeeded() both call
    // importSongsInBackground(), spinning up two concurrent scanner threads
    // on every login. compareAndSet(false, true) ensures only the first caller wins.
    private static final AtomicBoolean importRunning = new AtomicBoolean(false);

    public static void addImportListener(Runnable listener) {
        if (listener != null) importListeners.add(listener);
    }

    public static String getSavedSongsDir() {
        return SONGS_DIR;
    }

    private static void saveSongsDir(String path) {
        Properties props = new Properties();
        props.setProperty("songs_dir", path);
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "NeonPulse Config");
            System.out.println("✅ Config saved at: " + CONFIG_FILE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void promptAndImport(Stage ownerStage) {
        System.out.println("📂 Scanning for songs in: " + SONGS_DIR);
        File songsDirFile = new File(SONGS_DIR);

        if (!songsDirFile.exists()) {
            songsDirFile.mkdirs();
            System.out.println("⚠️ Songs folder missing. Created at: " + SONGS_DIR);
        }

        for (String genre : GENRES) {
            new File(songsDirFile, genre).mkdirs();
        }

        saveSongsDir(SONGS_DIR);
        importSongsInBackground(SONGS_DIR);
    }

    public static void importSongsInBackground(String songsDir) {
        // ✅ FIX: If an import is already running, skip silently.
        // compareAndSet(false, true) is atomic — only one thread can flip it.
        if (!importRunning.compareAndSet(false, true)) {
            System.out.println("⏭️ Import already running, skipping duplicate call.");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                importSongs(songsDir);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            importRunning.set(false); // ✅ Release the guard when done
            System.out.println("✅ Songs import complete!");
            Platform.runLater(() -> {
                for (Runnable listener : importListeners) listener.run();
            });
        });

        task.setOnFailed(e -> {
            importRunning.set(false); // ✅ Release even on failure so next login can retry
            System.err.println("❌ Import failed: " + task.getException().getMessage());
        });

        Thread thread = new Thread(task, "song-importer-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private static void importSongs(String songsDir) {
        SongDAO songDAO = new SongDAO();

        for (String genre : GENRES) {
            File genreDir = new File(songsDir, genre);
            if (!genreDir.exists()) continue;

            File[] files = genreDir.listFiles((dir, name) ->
                    name.toLowerCase().matches(".*\\.(mp3|wav|m4a)$")
            );

            if (files != null) {
                for (File file : files) {
                    try {
                        String relativePath = genre + File.separator + file.getName();

                        if (songDAO.getSongByFilePath(relativePath) != null) continue;

                        String rawName = file.getName().replaceFirst("[.][^.]+$", "");
                        String title   = rawName.replaceAll("\\s*\\[.*?\\]", "").trim();
                        double duration = estimateDuration(file);

                        Song song = new Song();
                        song.setTitle(title);
                        song.setGenre(capitalizeFirstLetter(genre));
                        song.setFilePath(relativePath);
                        song.setDuration(duration);

                        songDAO.insertSong(song);
                        System.out.println("-> Imported: " + title);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static double estimateDuration(File file) {
        return (double) file.length() / 16000;
    }

    private static String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}