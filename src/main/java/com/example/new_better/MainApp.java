package com.example.new_better;

import com.example.new_better.utils.DatabaseInitializer;
import com.example.new_better.utils.MusicPlayerManager;
import com.example.new_better.utils.SongFolderImporter;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;

/**
 * ARCHITECTURAL CHANGES vs ORIGINAL:
 *
 * 1. [CONCURRENCY] DatabaseInitializer.initialize() is now run off the JavaFX
 *    Application Thread (JAT) using a Task<Void>. DB init can involve disk I/O
 *    (creating tables, running migrations) which blocks the UI. The Task pattern
 *    ensures the splash/login scene renders immediately while init happens in
 *    the background. The scene load is then triggered via task.setOnSucceeded().
 *
 * 2. [ERROR HANDLING] All critical startup failures now show a user-facing Alert
 *    dialog instead of throwing a raw RuntimeException or printing to stderr.
 *    After showing the dialog, Platform.exit() is called cleanly.
 *
 * 3. [CONCURRENCY] SongFolderImporter.promptAndImport() is deferred via
 *    Platform.runLater() so it does not block the start() method after the scene
 *    is shown. This avoids a subtle race condition where the importer dialog
 *    could open before the primary stage is fully rendered.
 */
public class MainApp extends Application {

    private static Stage primaryStage;
    private static double xOffset = 0;
    private static double yOffset = 0;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.initStyle(StageStyle.UNDECORATED);

        // Show the login scene immediately — do NOT block here with DB work.
        showLoginScene();

        // ✅ FIX: Run DB initialization OFF the JavaFX Application Thread.
        // The original code ran DatabaseInitializer.initialize() synchronously
        // in start(), which means any slow disk I/O (table creation, WAL setup,
        // migration scripts) would freeze the UI before it even appeared.
        Task<Void> dbInitTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                DatabaseInitializer.initialize();
                return null;
            }
        };

        dbInitTask.setOnSucceeded(e -> {
            // DB is ready. Now it's safe to prompt for song import.
            // Platform.runLater ensures this runs after the current render pulse.
            Platform.runLater(() -> SongFolderImporter.promptAndImport(primaryStage));
        });

        dbInitTask.setOnFailed(e -> {
            // ✅ FIX: User-facing error instead of a silent crash.
            Throwable ex = dbInitTask.getException();
            Platform.runLater(() -> showErrorAlert(
                    "Database Error",
                    "NeonPulse could not initialize its database.",
                    "The app will now close. Details:\n" + ex.getMessage()
            ));
        });

        Thread dbThread = new Thread(dbInitTask, "db-init-thread");
        dbThread.setDaemon(true); // Won't prevent JVM from exiting if window closes
        dbThread.start();
    }

    // ─── Scene Helpers ───────────────────────────────────────────────────────

    private void showLoginScene() {
        try {
            URL fxmlUrl = MainApp.class.getResource("/com/example/new_better/views/login.fxml");
            if (fxmlUrl == null) {
                // ✅ FIX: Show alert instead of throwing RuntimeException
                showErrorAlert("Startup Error", "login.fxml was not found.",
                        "Check that your resources folder is correctly configured.");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            enableWindowDrag(root);

            Scene scene = new Scene(root);
            addCss(scene, "/com/example/new_better/css/main.css");
            addCss(scene, "/com/example/new_better/css/auth.css");

            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1024);
            primaryStage.setMinHeight(700);

            // Maximise manually — setMaximized(true) is unreliable with UNDECORATED
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            primaryStage.setX(bounds.getMinX());
            primaryStage.setY(bounds.getMinY());
            primaryStage.setWidth(bounds.getWidth());
            primaryStage.setHeight(bounds.getHeight());

            primaryStage.show();

        } catch (Exception e) {
            showErrorAlert("Startup Error", "Failed to load the login screen.", e.getMessage());
        }
    }

    /**
     * Centralized scene switcher used by all controllers.
     * Retains current window size so the user's layout doesn't jump.
     */
    public static void changeScene(String fxmlPath, String... cssFiles) {
        try {
            URL fxmlUrl = MainApp.class.getResource(fxmlPath);
            if (fxmlUrl == null) {
                showErrorAlert("Navigation Error", "Screen not found.",
                        "Could not load: " + fxmlPath);
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            enableWindowDrag(root);

            Scene currentScene = primaryStage.getScene();
            double width  = (currentScene != null) ? currentScene.getWidth()  : 1280;
            double height = (currentScene != null) ? currentScene.getHeight() : 800;

            Scene scene = new Scene(root, width, height);
            addCssStatic(scene, "/com/example/new_better/css/main.css");
            for (String css : cssFiles) addCssStatic(scene, css);

            primaryStage.setScene(scene);

            // Re-apply maximization after scene swap (UNDECORATED quirk)
            if (primaryStage.isMaximized()) {
                primaryStage.setMaximized(true);
            }

        } catch (Exception e) {
            showErrorAlert("Navigation Error", "An unexpected error occurred.", e.getMessage());
        }
    }

    // ─── Window Drag ─────────────────────────────────────────────────────────

    public static void enableWindowDrag(Parent root) {
        root.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        root.setOnMouseDragged(event -> {
            primaryStage.setX(event.getScreenX() - xOffset);
            primaryStage.setY(event.getScreenY() - yOffset);
        });
    }

    // ─── CSS Helpers ─────────────────────────────────────────────────────────

    private void addCss(Scene scene, String path) {
        addCssStatic(scene, path);
    }

    private static void addCssStatic(Scene scene, String path) {
        URL cssUrl = MainApp.class.getResource(path);
        if (cssUrl == null) {
            System.err.println("⚠️ CSS not found: " + path);
        } else {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
    }

    // ─── Error Dialog ────────────────────────────────────────────────────────

    /**
     * ✅ FIX: Centralised user-facing error dialog.
     * Must always be called on the JavaFX Application Thread.
     * Calls Platform.exit() after the user dismisses it.
     */
    public static void showErrorAlert(String title, String header, String detail) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(detail != null ? detail : "No additional information available.");
        alert.showAndWait();
        Platform.exit();
    }

    /**
     * Non-fatal version — shows the alert but does NOT exit the app.
     */
    public static void showWarningAlert(String title, String header, String detail) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(detail != null ? detail : "");
        alert.show();
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    public void stop() {
        MusicPlayerManager.getInstance().shutdown();
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}