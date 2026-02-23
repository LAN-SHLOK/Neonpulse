
package com.example.new_better.utils;

import com.example.new_better.models.Song;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections; // Import needed for shuffle
import java.util.List;
import java.util.Random;

public class MusicPlayerManager {

    private static MusicPlayerManager instance;
    private MediaPlayer mediaPlayer;

    // 🔥 Added originalQueue to remember order when Shuffle is OFF
    private List<Song> queue;
    private List<Song> originalQueue;

    private int currentIndex;
    private boolean shuffle;
    private boolean repeat;
    private boolean repeatOne;
    private List<SongChangeListener> listeners;

    private MusicPlayerManager() {
        queue = new ArrayList<>();
        originalQueue = new ArrayList<>(); // Initialize backup list
        currentIndex = -1;
        shuffle = false;
        repeat = false;
        repeatOne = false;
        listeners = new ArrayList<>();
    }

    public static MusicPlayerManager getInstance() {
        if (instance == null) {
            instance = new MusicPlayerManager();
        }
        return instance;
    }

    // ==========================================================
    // 🔥 FIXED SHUFFLE LOGIC
    // ==========================================================
    public void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;

        Song currentSong = getCurrentSong(); // Remember what is playing right now

        if (shuffle) {
            // 1. Save the current order so we can restore it later
            originalQueue = new ArrayList<>(queue);

            // 2. Shuffle the actual playing queue
            Collections.shuffle(queue, new Random());

            // 3. Move the currently playing song to the top (or find its new spot)
            // so playback doesn't skip abruptly.
            if (currentSong != null) {
                // Option A: Just find where it went
                currentIndex = queue.indexOf(currentSong);
            }
        } else {
            // Restore the original order
            if (originalQueue != null && !originalQueue.isEmpty()) {
                queue = new ArrayList<>(originalQueue);

                // Find where our current song is in the original list
                if (currentSong != null) {
                    currentIndex = queue.indexOf(currentSong);
                }
            }
        }
        System.out.println("Shuffle set to: " + shuffle);
    }

    // ==========================================================
    // UPDATED PLAYBACK LOGIC (Simplifies Next/Prev)
    // ==========================================================

    public void playNext() {

        if (queue.isEmpty()) return;

        // 🔥 FORCE RANDOM LOGIC if shuffle is true
        if (shuffle) {
            Random rand = new Random();
            int newIndex = currentIndex;

            // Keep picking a random number until it's different from the current song
            // (unless there is only 1 song in the list)
            if (queue.size() > 1) {
                while (newIndex == currentIndex) {
                    newIndex = rand.nextInt(queue.size());
                }
            }
            currentIndex = newIndex;

        } else {
            // Normal sequential logic
            if (currentIndex < 0) {
                currentIndex = 0;
            } else {
                currentIndex++;
            }

            // Handle end of playlist
            if (currentIndex >= queue.size()) {
                if (repeat) {
                    currentIndex = 0;
                } else {
                    stop();
                    currentIndex = queue.size() - 1;
                    notifyListeners();
                    return;
                }
            }
        }

        playSong(queue.get(currentIndex));
    }

    public void playPrevious() {

        if (queue.isEmpty()) return;

        if (shuffle) {
            // 🔥 Randomize previous too
            Random rand = new Random();
            currentIndex = rand.nextInt(queue.size());
        } else {
            if (currentIndex <= 0) {
                currentIndex = 0;
            } else {
                currentIndex--;
            }
        }

        playSong(queue.get(currentIndex));
    }

    // ==========================================================
    // EXISTING FUNCTIONS (Untouched Logic)
    // ==========================================================

    public void playSong(Song song) {
        disposeCurrentPlayer();
        try {
            // ✅ Reconstruct full path from saved songs dir + relative path
            String songsDir = SongFolderImporter.getSavedSongsDir();
            File file;

            if (songsDir != null && !song.getFilePath().contains(":\\")) {
                // New deployment-safe relative path e.g. "pop/song.mp3"
                file = new File(songsDir, song.getFilePath());
            } else {
                // Fallback for old absolute paths already in DB
                file = new File(song.getFilePath());
            }

            if (!file.exists()) {
                showError("Song file not found",
                        "'" + song.getTitle() + "' could not be found.\n" +
                                "Expected at: " + file.getAbsolutePath());
                return;
            }

            // ✅ Continue with your existing playback code below
            String uri = file.toURI().toString();
            Media media = new Media(uri);
            mediaPlayer = new MediaPlayer(media);

            mediaPlayer.setOnReady(() -> {
                mediaPlayer.play();
                currentIndex = queue.indexOf(song);
                notifyListeners();
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                if (repeatOne) {
                    mediaPlayer.seek(Duration.ZERO);
                    mediaPlayer.play();
                } else {
                    playNext();
                }
            });

            mediaPlayer.setOnError(() ->
                    showError("Playback Error", "Could not play: " + song.getTitle())
            );

        } catch (Exception e) {
            e.printStackTrace();
            showError("Playback Error", "Unexpected error playing: " + song.getTitle());
        }
    }

    private void disposeCurrentPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    public void playFromQueue(int index) {
        if (index >= 0 && index < queue.size()) {
            currentIndex = index;
            playSong(queue.get(currentIndex));
        }
    }

    public void playPause() {
        if (mediaPlayer == null) return;
        MediaPlayer.Status status = mediaPlayer.getStatus();
        if (status == MediaPlayer.Status.PLAYING) mediaPlayer.pause();
        else mediaPlayer.play();
    }

    public void stop() {
        if (mediaPlayer != null) mediaPlayer.stop();
    }

    public void seek(double seconds) {
        if (mediaPlayer != null) mediaPlayer.seek(Duration.seconds(seconds));
    }

    public void setVolume(double volume) {
        if (mediaPlayer != null) mediaPlayer.setVolume(volume);
    }


    public void setQueue(List<Song> songs) {
        queue.clear();
        if (originalQueue != null) originalQueue.clear();

        queue.addAll(songs);
        // If setting new queue, assume it is the "original" order initially
        originalQueue = new ArrayList<>(songs);

        currentIndex = -1;
        notifyListeners();
    }

    public void removeFromQueue(Song song) {
        int index = queue.indexOf(song);
        if (index != -1) {
            queue.remove(index);
            if (originalQueue != null) originalQueue.remove(song);

            if (index < currentIndex) {
                currentIndex--;
            } else if (index == currentIndex) {
                if (!queue.isEmpty()) {
                    currentIndex = Math.min(currentIndex, queue.size() - 1);
                    playSong(queue.get(currentIndex));
                } else {
                    currentIndex = -1;
                    stop();
                }
            }
        }
    }

    public void clearQueue() {
        queue.clear();
        if (originalQueue != null) originalQueue.clear();
        currentIndex = -1;
        stop();
        notifyListeners();
    }

    public List<Song> getQueue() {
        return new ArrayList<>(queue);
    }

    public Song getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            return queue.get(currentIndex);
        }
        return null;
    }

    public MediaPlayer getMediaPlayer() { return mediaPlayer; }
    public boolean isShuffle() { return shuffle; }
    public boolean isRepeat() { return repeat; }
    public void setRepeat(boolean repeat) { this.repeat = repeat; }
    public boolean isRepeatOne() { return repeatOne; }
    public void setRepeatOne(boolean repeatOne) { this.repeatOne = repeatOne; }

    public void addListener(SongChangeListener listener) { listeners.add(listener); }
    public void removeListener(SongChangeListener listener) { listeners.remove(listener); }

    private void notifyListeners() {
        for (SongChangeListener listener : listeners) {
            listener.onSongChanged(getCurrentSong());
        }
    }

    public void shutdown() {
        disposeCurrentPlayer();
        queue.clear();
        listeners.clear();
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public interface SongChangeListener {
        void onSongChanged(Song song);
    }
    public void addToQueue(Song song) {
        queue.add(song);
        if (originalQueue != null) originalQueue.add(song); // Keep backup synced
        notifyListeners(); // 🔥 ADDED: refreshes queue panel automatically
    }
}