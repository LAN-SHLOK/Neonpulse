package com.example.new_better.controllers;

import com.example.new_better.MainApp;
import com.example.new_better.models.User;
import com.example.new_better.utils.Session;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * ARCHITECTURAL CHANGES vs ORIGINAL:
 *
 * 1. [CONCURRENCY] Image loading has been moved off the JAT using a Task<Image>.
 *    Image() with a file URI does disk I/O. For large JPEGs (e.g. unresized phone
 *    photos) this can visibly stutter the UI. The Task loads the Image in the
 *    background and applies it via Platform.runLater().
 *
 * 2. [ERROR HANDLING] All catch blocks now show a user-facing Alert via
 *    MainApp.showWarningAlert() instead of just printing to stderr.
 *
 * 3. [ROBUSTNESS] Image copy in handleChangeProfilePicture() is also now run
 *    in a Task so a large file copy never jams the UI thread.
 *
 * 4. [NEW] setMainController() added so that after a picture change, the
 *    top-right circle avatar in MainController refreshes automatically.
 */
public class ProfileController {

    @FXML private ImageView profileImageView;
    @FXML private javafx.scene.control.Label usernameLabel;
    @FXML private javafx.scene.control.Label emailLabel;

    // Centralised path helper to avoid repetition
    private static final String IMAGES_DIR =
            System.getProperty("user.dir") + File.separator + "images" + File.separator;

    // ✅ NEW: reference to MainController so avatar circle updates after picture change
    private MainController mainController;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    private void initialize() {
        loadProfile();
    }

    private void loadProfile() {
        User user = Session.getInstance().getCurrentUser();
        if (user == null) return;

        usernameLabel.setText(user.getUsername());
        emailLabel.setText(user.getEmail());

        // ✅ FIX: Load image off the JAT to avoid UI stutter on large files.
        Task<Image> imageLoadTask = new Task<>() {
            @Override
            protected Image call() {
                File imageFile = new File(IMAGES_DIR + "profile_" + user.getUserId() + ".jpg");
                if (imageFile.exists()) {
                    return new Image(imageFile.toURI().toString());
                }
                File defaultFile = new File(IMAGES_DIR + "default_user.png");
                if (defaultFile.exists()) {
                    return new Image(defaultFile.toURI().toString());
                }
                return null; // No image available
            }
        };

        imageLoadTask.setOnSucceeded(e -> {
            Image img = imageLoadTask.getValue();
            if (img != null) profileImageView.setImage(img);
        });

        imageLoadTask.setOnFailed(e -> {
            // ✅ FIX: Show warning instead of printing to stderr.
            Platform.runLater(() -> MainApp.showWarningAlert(
                    "Image Load Error",
                    "Could not load your profile picture.",
                    imageLoadTask.getException().getMessage()
            ));
        });

        Thread t = new Thread(imageLoadTask, "profile-image-loader");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleChangeProfilePicture() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Picture");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        File selectedFile = fileChooser.showOpenDialog(profileImageView.getScene().getWindow());
        if (selectedFile == null) return;

        User user = Session.getInstance().getCurrentUser();
        if (user == null) return;

        File imagesDir = new File(IMAGES_DIR);
        if (!imagesDir.exists()) imagesDir.mkdirs();

        File destFile = new File(imagesDir, "profile_" + user.getUserId() + ".jpg");

        // ✅ FIX: Run the file copy off the JAT. Large photos (10MB+ from phones)
        // would previously freeze the UI for the entire copy duration.
        Task<Image> copyTask = new Task<>() {
            @Override
            protected Image call() throws Exception {
                Files.copy(selectedFile.toPath(), destFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                // Load the new image while still on the background thread
                return new Image(destFile.toURI().toString());
            }
        };

        copyTask.setOnSucceeded(e -> {
            profileImageView.setImage(copyTask.getValue());
            // ✅ NEW: also refresh the top-right circle avatar in the main layout
            if (mainController != null) {
                mainController.refreshProfileAvatar();
            }
        });

        copyTask.setOnFailed(e -> Platform.runLater(() -> {
            // ✅ FIX: User-facing error instead of silent e.printStackTrace()
            MainApp.showWarningAlert(
                    "Upload Failed",
                    "Could not save your profile picture.",
                    copyTask.getException().getMessage()
            );
        }));

        Thread t = new Thread(copyTask, "profile-image-copy");
        t.setDaemon(true);
        t.start();
    }
}