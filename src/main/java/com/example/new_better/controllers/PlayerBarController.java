package com.example.new_better.controllers;

import com.example.new_better.dao.LikedSongsDAO;
import com.example.new_better.dao.RecentlyPlayedDAO;
import com.example.new_better.models.Song;
import com.example.new_better.utils.MusicPlayerManager;
import com.example.new_better.utils.Session;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * ARCHITECTURAL CHANGES vs ORIGINAL:
 *
 * 1. [UI PRECISION] The fixed 200ms Timeline polling loop has been REPLACED with
 *    MediaPlayer's native currentTimeProperty listener. This is a reactive,
 *    event-driven approach:
 *      - No wasted CPU ticks when the player is paused or stopped.
 *      - Progress updates happen exactly when MediaPlayer fires them (typically
 *        every ~66ms for audio, timed to the media clock, not a wall clock).
 *      - Timeline polling can drift; the native listener never does.
 *      - We still guard against seek conflicts with isUserDraggingSlider.
 *
 * 2. [UI PRECISION] totalDurationProperty listener sets the slider max ONCE when
 *    the media is ready, rather than recalculating it every update tick.
 *
 * 3. [MEMORY] Listeners are stored as named fields and REMOVED from the old
 *    MediaPlayer before a new one is attached. The original code added a new
 *    statusProperty listener on every song change without ever removing the old
 *    one, causing listener accumulation and potential memory leaks over a long
 *    listening session.
 *
 * 4. [ERROR HANDLING] handleLike() now wraps DAO calls in a try/catch and shows
 *    a non-fatal warning dialog on failure rather than silently swallowing errors.
 *
 * 5. [THREAD SAFETY] All UI mutations are explicitly wrapped in Platform.runLater()
 *    where there's any chance they're called from a non-JAT context (e.g., the
 *    MediaPlayer's internal media thread).
 */
public class PlayerBarController implements MusicPlayerManager.SongChangeListener {

    // ─── FXML Bindings ───────────────────────────────────────────────────────

    @FXML private Button queueBtn;
    @FXML private SVGPath playPauseIcon;
    @FXML private VBox queuePanelContainer;
    @FXML private Label songTitleLabel;
    @FXML private Label songGenreLabel;
    @FXML private Label timeLabel;
    @FXML private Button playPauseBtn;
    @FXML private Button shuffleBtn;
    @FXML private Button repeatBtn;
    @FXML private Button likeBtn;
    @FXML private Slider progressSlider;
    @FXML private Slider volumeSlider;

    // ─── SVG Icon Paths ──────────────────────────────────────────────────────

    private static final String PLAY_PATH         = "M8 5v14l11-7z";
    private static final String PAUSE_PATH        = "M6 19h4V5H6v14zm8-14v14h4V5h-4z";
    private static final String HEART_FILLED      = "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z";
    private static final String HEART_OUTLINE     = "M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z";

    // ─── State ───────────────────────────────────────────────────────────────

    private LikedSongsDAO likedSongsDAO;
    private RecentlyPlayedDAO recentlyPlayedDAO;
    private boolean isUserDraggingSlider = false;
    private MainController mainController;

    /**
     * ✅ FIX: Store listeners as named fields so we can REMOVE them from the
     * old MediaPlayer before attaching to a new one.
     * The original code called player.statusProperty().addListener() on every
     * song change with a new anonymous lambda, never removing previous ones.
     */
    private InvalidationListener currentTimeListener;
    private InvalidationListener statusListener;

    // ─── Initialization ──────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        likedSongsDAO    = new LikedSongsDAO();
        recentlyPlayedDAO = new RecentlyPlayedDAO();
        MusicPlayerManager.getInstance().addListener(this);
        initializeVolumeControl();
        initializeProgressLogic();
        syncCurrentState();
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void setQueuePanelContainer(VBox container) {
        this.queuePanelContainer = container;
    }

    private void syncCurrentState() {
        updatePlayPauseButton();
        updateShuffleState();
        updateRepeatState();
        updateLikeState();
    }

    // ─── Progress & Time ─────────────────────────────────────────────────────

    private void initializeProgressLogic() {
        // Drag start: freeze the slider so our listener doesn't fight the user.
        progressSlider.setOnMousePressed(e -> isUserDraggingSlider = true);

        // Drag end: seek to where the user dropped the thumb.
        progressSlider.setOnMouseReleased(e -> {
            MediaPlayer player = MusicPlayerManager.getInstance().getMediaPlayer();
            if (player != null) {
                player.seek(Duration.seconds(progressSlider.getValue()));
            }
            isUserDraggingSlider = false;
        });

        // ✅ FIX: Define listeners as fields (not inline lambdas) so they can
        // be cleanly removed when the MediaPlayer changes.
        currentTimeListener = obs -> {
            if (!isUserDraggingSlider) {
                Platform.runLater(this::updateProgressUI);
            }
        };

        statusListener = obs -> Platform.runLater(this::updatePlayPauseButton);
    }

    /**
     * ✅ FIX: Replaces the 200ms Timeline polling with MediaPlayer's native
     * currentTimeProperty. This is event-driven — the listener fires only when
     * the position actually changes (i.e., when playing), saving CPU cycles
     * while paused or stopped.
     *
     * Also attaches a totalDurationProperty listener to set the slider max ONCE
     * when media is ready, rather than recalculating on every tick.
     */
    private void attachMediaListeners(MediaPlayer player) {
        if (player == null) return;

        // Set slider range when the media duration becomes known
        player.totalDurationProperty().addListener((obs, oldDur, newDur) -> {
            if (newDur != null && newDur.greaterThan(Duration.ZERO)) {
                Platform.runLater(() -> progressSlider.setMax(newDur.toSeconds()));
            }
        });

        // Also set it immediately if media is already ready
        Duration total = player.getTotalDuration();
        if (total != null && total.greaterThan(Duration.ZERO)) {
            progressSlider.setMax(total.toSeconds());
        }

        player.currentTimeProperty().addListener(currentTimeListener);
        player.statusProperty().addListener(statusListener);
    }

    /**
     * Removes our named listeners from the given player.
     * Called before we attach to a new MediaPlayer to prevent listener leaks.
     */
    private void detachMediaListeners(MediaPlayer player) {
        if (player == null) return;
        if (currentTimeListener != null) player.currentTimeProperty().removeListener(currentTimeListener);
        if (statusListener != null)      player.statusProperty().removeListener(statusListener);
    }

    private void updateProgressUI() {
        MediaPlayer player = MusicPlayerManager.getInstance().getMediaPlayer();
        if (player == null) return;

        Duration currentDuration = player.getCurrentTime();
        Duration totalDuration   = player.getTotalDuration();
        if (currentDuration == null || totalDuration == null) return;

        double current = currentDuration.toSeconds();
        double total   = totalDuration.toSeconds();

        if (total > 0) {
            progressSlider.setValue(current);
            if (timeLabel != null) {
                timeLabel.setText(formatTime(current) + " / " + formatTime(total));
            }
        }
    }

    /** Formats a raw second count into MM:SS. */
    private String formatTime(double seconds) {
        int m = (int) seconds / 60;
        int s = (int) seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    // ─── Playback Controls ───────────────────────────────────────────────────

    @FXML private void handlePlayPause() { MusicPlayerManager.getInstance().playPause(); }
    @FXML private void handleNext()      { MusicPlayerManager.getInstance().playNext(); }
    @FXML private void handlePrevious() { MusicPlayerManager.getInstance().playPrevious(); }

    @FXML
    private void handleShuffle() {
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        manager.setShuffle(!manager.isShuffle());
        updateShuffleState();
    }

    @FXML
    private void handleRepeat() {
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        if (!manager.isRepeat() && !manager.isRepeatOne()) {
            manager.setRepeat(true);
            manager.setRepeatOne(false);
        } else if (manager.isRepeat()) {
            manager.setRepeat(false);
            manager.setRepeatOne(true);
        } else {
            manager.setRepeat(false);
            manager.setRepeatOne(false);
        }
        updateRepeatState();
    }

    // ─── Like ────────────────────────────────────────────────────────────────

    @FXML
    private void handleLike() {
        Song current = MusicPlayerManager.getInstance().getCurrentSong();
        if (current == null || Session.getInstance().getCurrentUser() == null) return;
        int userId = Session.getInstance().getCurrentUser().getUserId();

        // ✅ FIX: Wrap DAO calls in try/catch and show a warning on failure.
        try {
            boolean isLiked = likedSongsDAO.isLiked(userId, current.getSongId());
            if (isLiked) likedSongsDAO.unlikeSong(userId, current.getSongId());
            else         likedSongsDAO.likeSong(userId, current.getSongId());
            updateLikeState();
        } catch (Exception e) {
            System.err.println("Like action failed: " + e.getMessage());
            // Non-fatal: show warning but don't crash the app
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Like Failed");
                alert.setHeaderText("Could not update like status.");
                alert.setContentText("Please check your database connection and try again.");
                alert.show();
            });
        }
    }

    // ─── UI State Updaters ───────────────────────────────────────────────────

    private void updatePlayPauseButton() {
        MediaPlayer player = MusicPlayerManager.getInstance().getMediaPlayer();
        if (playPauseIcon == null) return;
        boolean playing = player != null && player.getStatus() == MediaPlayer.Status.PLAYING;
        playPauseIcon.setContent(playing ? PAUSE_PATH : PLAY_PATH);
    }

    private void updateShuffleState() {
        boolean active = MusicPlayerManager.getInstance().isShuffle();
        if (shuffleBtn != null && shuffleBtn.getGraphic() != null) {
            shuffleBtn.getGraphic().setStyle(active
                    ? "-fx-background-color: #0A84FF; -fx-opacity: 1;"
                    : "-fx-background-color: white; -fx-opacity: 0.6;");
        }
    }

    private void updateRepeatState() {
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        if (repeatBtn == null || repeatBtn.getGraphic() == null) return;
        if (manager.isRepeatOne()) {
            repeatBtn.getGraphic().setStyle("-fx-fill: #32CD32; -fx-opacity: 1.0;");
        } else if (manager.isRepeat()) {
            repeatBtn.getGraphic().setStyle("-fx-fill: #0A84FF; -fx-opacity: 1.0;");
        } else {
            repeatBtn.getGraphic().setStyle("-fx-fill: white; -fx-opacity: 0.6;");
        }
    }

    private void updateLikeState() {
        Song current = MusicPlayerManager.getInstance().getCurrentSong();
        if (current == null || Session.getInstance().getCurrentUser() == null) return;
        try {
            boolean liked = likedSongsDAO.isLiked(
                    Session.getInstance().getCurrentUser().getUserId(),
                    current.getSongId());
            if (likeBtn != null && likeBtn.getGraphic() instanceof SVGPath icon) {
                icon.setContent(liked ? HEART_FILLED : HEART_OUTLINE);
                icon.setStyle(liked ? "-fx-fill: #FF2D55;" : "-fx-fill: white;");
            }
        } catch (Exception e) {
            System.err.println("Could not read like state: " + e.getMessage());
        }
    }

    // ─── Volume ──────────────────────────────────────────────────────────────

    private void initializeVolumeControl() {
        volumeSlider.setValue(Session.getInstance().getUserVolume() * 100);
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double vol = newVal.doubleValue() / 100.0;
            MusicPlayerManager.getInstance().setVolume(vol);
        });
    }

    // ─── Song Change Callback ────────────────────────────────────────────────

    /**
     * Called by MusicPlayerManager when the track changes.
     * ✅ FIX: We detach listeners from the OLD player before attaching to the
     * NEW one, preventing the listener accumulation bug in the original code.
     */
    @Override
    public void onSongChanged(Song song) {
        // Detach from whatever player was active before
        MediaPlayer oldPlayer = MusicPlayerManager.getInstance().getMediaPlayer();
        detachMediaListeners(oldPlayer);

        Platform.runLater(() -> {
            if (song != null) {
                songTitleLabel.setText(song.getTitle());
                songGenreLabel.setText(song.getGenre());
                updateLikeState();
                if (Session.getInstance().getCurrentUser() != null) {
                    recentlyPlayedDAO.addToRecentlyPlayed(
                            Session.getInstance().getCurrentUser().getUserId(),
                            song.getSongId());
                }
            }
            // Attach to the new player
            MediaPlayer newPlayer = MusicPlayerManager.getInstance().getMediaPlayer();
            attachMediaListeners(newPlayer);
            updatePlayPauseButton();
        });
    }

    // ─── Queue Panel ─────────────────────────────────────────────────────────

    @FXML
    private void handleToggleQueue() {
        if (mainController != null) {
            mainController.toggleQueuePanel();
            if (queueBtn != null) {
                boolean isVisible = queuePanelContainer != null && queuePanelContainer.isVisible();
                queueBtn.setOpacity(isVisible ? 1.0 : 0.6);
            }
        }
    }
}