

package com.example.new_better.controllers;

import com.example.new_better.MainApp;
import com.example.new_better.dao.UserDAO;
import com.example.new_better.models.User;
import com.example.new_better.utils.Session;
import com.example.new_better.utils.SongFolderImporter;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private VBox formContainer;

    // ── Reset state (held in memory between the 2 dialog steps) ──
    private String resetUsername; // confirmed after step 1

    // ═══════════════════════════════════════════════════════════
    //  EXISTING — completely untouched
    // ═══════════════════════════════════════════════════════════

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        errorLabel.setText("");

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields");
            shakeAnimation();
            return;
        }

        loginButton.setDisable(true);
        loginButton.setText("Logging in...");

        Task<User> authTask = new Task<>() {
            @Override
            protected User call() {
                UserDAO userDAO = new UserDAO();
                return userDAO.authenticateUser(username, password);
            }

            @Override
            protected void succeeded() {
                User user = getValue();
                if (user == null) {
                    showError("Invalid username or password");
                    shakeAnimation();
                    resetButton();
                    return;
                }
                if (!user.isVerified()) {
                    showError("Please verify your email before logging in");
                    shakeAnimation();
                    resetButton();
                    return;
                }
                Session.getInstance().setCurrentUser(user);
                // ✅ New call - pass the saved songs directory
                String songsDir = SongFolderImporter.getSavedSongsDir();
                if (songsDir != null) {
                    SongFolderImporter.importSongsInBackground(songsDir);
                }
                MainApp.changeScene(
                        "/com/example/new_better/views/mainPage.fxml",
                        "/com/example/new_better/css/sidebar.css",
                        "/com/example/new_better/css/player_bar.css"
                );
            }

            @Override
            protected void failed() {
                showError("Login failed. Please try again.");
                shakeAnimation();
                resetButton();
            }
        };

        Thread thread = new Thread(authTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleGoToSignup() {
        MainApp.changeScene(
                "/com/example/new_better/views/signup.fxml",
                "/com/example/new_better/css/auth.css"
        );
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }

    private void resetButton() {
        Platform.runLater(() -> {
            loginButton.setDisable(false);
            loginButton.setText("Login");
        });
    }

    private void shakeAnimation() {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,        new KeyValue(formContainer.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(50),  new KeyValue(formContainer.translateXProperty(), -10)),
                new KeyFrame(Duration.millis(100), new KeyValue(formContainer.translateXProperty(), 10)),
                new KeyFrame(Duration.millis(150), new KeyValue(formContainer.translateXProperty(), -10)),
                new KeyFrame(Duration.millis(200), new KeyValue(formContainer.translateXProperty(), 10)),
                new KeyFrame(Duration.millis(250), new KeyValue(formContainer.translateXProperty(), 0))
        );
        timeline.play();
    }

    @FXML
    private void handleMinimize(ActionEvent event) {
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleExit() {
        Platform.exit();
        System.exit(0);
    }

    // ═══════════════════════════════════════════════════════════
    //  NEW — Forgot Password
    //
    //  Flow (zero external libraries):
    //  Step 1 → User enters Username + Email
    //           → validated against DB using validateUserForReset()
    //  Step 2 → User sets new password directly
    //           → saved via updatePassword() with hashing
    // ═══════════════════════════════════════════════════════════

    @FXML
    private void handleForgotPassword() {
        showVerifyIdentityStep();
    }

    // ── STEP 1: Verify username + email ─────────────────────
    private void showVerifyIdentityStep() {
        Stage stage = buildDialogStage();

        TextField usernameInput = new TextField();
        usernameInput.setPromptText("Your username");
        usernameInput.setStyle(fieldStyle());

        TextField emailInput = new TextField();
        emailInput.setPromptText("Your email address");
        emailInput.setStyle(fieldStyle());

        Label statusLbl = statusLabel();

        VBox body = new VBox(12, usernameInput, emailInput, statusLbl);
        body.setPadding(new Insets(18, 24, 10, 24));

        Button cancelBtn = buildDialogButton("Cancel", false);
        Button nextBtn   = buildDialogButton("Next", true);

        cancelBtn.setOnAction(e -> stage.close());

        nextBtn.setOnAction(e -> {
            String uname = usernameInput.getText().trim();
            String email  = emailInput.getText().trim();

            if (uname.isEmpty() || email.isEmpty()) {
                statusLbl.setText("Please fill in both fields.");
                shakeNode(uname.isEmpty() ? usernameInput : emailInput);
                return;
            }

            nextBtn.setDisable(true);
            nextBtn.setText("Checking...");

            // Run DB check off the FX thread
            Task<Boolean> checkTask = new Task<>() {
                @Override protected Boolean call() {
                    return new UserDAO().validateUserForReset(uname, email);
                }
                @Override protected void succeeded() {
                    if (getValue()) {
                        resetUsername = uname; // store for step 2
                        stage.close();
                        Platform.runLater(() -> showNewPasswordStep());
                    } else {
                        nextBtn.setDisable(false);
                        nextBtn.setText("Next");
                        statusLbl.setText("No account matches that username and email.");
                        shakeNode(usernameInput);
                    }
                }
                @Override protected void failed() {
                    nextBtn.setDisable(false);
                    nextBtn.setText("Next");
                    statusLbl.setText("Something went wrong. Please try again.");
                }
            };
            Thread t = new Thread(checkTask);
            t.setDaemon(true);
            t.start();
        });

        showStage(stage,
                card(
                        styledHeader("Forgot Password", "Enter your username and registered email"),
                        body,
                        divider(),
                        buttonRow(cancelBtn, nextBtn)
                )
        );
    }

    // ── STEP 2: Set new password ─────────────────────────────
    private void showNewPasswordStep() {
        Stage stage = buildDialogStage();

        PasswordField newPassField = new PasswordField();
        newPassField.setPromptText("New password (min 6 chars)");
        newPassField.setStyle(fieldStyle());

        PasswordField confirmPassField = new PasswordField();
        confirmPassField.setPromptText("Confirm new password");
        confirmPassField.setStyle(fieldStyle());

        Label statusLbl = statusLabel();

        VBox body = new VBox(12, newPassField, confirmPassField, statusLbl);
        body.setPadding(new Insets(18, 24, 10, 24));

        Button backBtn = buildDialogButton("Back", false);
        Button saveBtn = buildDialogButton("Save", true);

        backBtn.setOnAction(e -> {
            stage.close();
            Platform.runLater(this::showVerifyIdentityStep);
        });

        saveBtn.setOnAction(e -> {
            String newPass     = newPassField.getText();
            String confirmPass = confirmPassField.getText();

            if (newPass.length() < 6) {
                statusLbl.setText("Password must be at least 6 characters.");
                shakeNode(newPassField);
                return;
            }
            if (!newPass.equals(confirmPass)) {
                statusLbl.setText("Passwords do not match.");
                shakeNode(confirmPassField);
                return;
            }

            saveBtn.setDisable(true);
            saveBtn.setText("Saving...");

            Task<Boolean> saveTask = new Task<>() {
                @Override protected Boolean call() {
                    // updatePassword() now hashes via PasswordUtil internally
                    return new UserDAO().updatePassword(resetUsername, newPass);
                }
                @Override protected void succeeded() {
                    resetUsername = null; // clear state
                    stage.close();
                    Platform.runLater(() ->
                            showSuccessDialog(
                                    "Password Updated!",
                                    "Your password has been changed.\nYou can now log in."
                            )
                    );
                }
                @Override protected void failed() {
                    saveBtn.setDisable(false);
                    saveBtn.setText("Save");
                    statusLbl.setText("Failed to update. Please try again.");
                }
            };
            Thread t = new Thread(saveTask);
            t.setDaemon(true);
            t.start();
        });

        showStage(stage,
                card(
                        styledHeader("New Password", "Set a new password for @" + resetUsername),
                        body,
                        divider(),
                        buttonRow(backBtn, saveBtn)
                )
        );
    }

    // ── Success dialog (green accent) ────────────────────────
    private void showSuccessDialog(String header, String message) {
        Stage stage = buildDialogStage();

        Label iconLbl = new Label("✓");
        iconLbl.setStyle(
                "-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; " +
                        "-fx-background-color: rgba(255,255,255,0.25); -fx-background-radius: 50%; " +
                        "-fx-min-width: 34px; -fx-min-height: 34px; " +
                        "-fx-max-width: 34px; -fx-max-height: 34px; -fx-alignment: center;"
        );
        Label titleLbl = new Label(header);
        titleLbl.setStyle(headerLabelStyle());

        HBox headerBox = new HBox(12, iconLbl, titleLbl);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(20, 24, 18, 24));
        headerBox.setStyle(
                "-fx-background-color: linear-gradient(to right, #30d158, #34c759); " +
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

        Button okBtn = buildDialogButton("Login Now", true);
        okBtn.setOnAction(e -> stage.close());

        HBox btnRow = new HBox(okBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(12, 20, 18, 20));

        VBox card = new VBox(headerBox, body, divider(), btnRow);
        card.setStyle(
                "-fx-background-color: #141420; -fx-background-radius: 18px; " +
                        "-fx-effect: dropshadow(gaussian, rgba(48,209,88,0.5), 36, 0, 0, 0);"
        );
        card.setMaxWidth(340);
        card.setMinWidth(280);

        showStage(stage, card);
    }

    // ═══════════════════════════════════════════════════════════
    //  NeonPulse Dialog helpers (same style as rest of app)
    // ═══════════════════════════════════════════════════════════

    private Stage buildDialogStage() {
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.WINDOW_MODAL);
        if (formContainer != null && formContainer.getScene() != null) {
            stage.initOwner(formContainer.getScene().getWindow());
        }
        return stage;
    }

    private void showStage(Stage stage, VBox card) {
        card.setMaxWidth(380);
        card.setMinWidth(320);
        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: transparent;");
        root.setPadding(new Insets(20));
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.show();
    }

    private VBox styledHeader(String title, String subtitle) {
        Label titleLbl = new Label(title);
        titleLbl.setStyle(headerLabelStyle());
        Label subLbl = new Label(subtitle);
        subLbl.setStyle(subLabelStyle());
        subLbl.setWrapText(true);
        VBox header = new VBox(5, titleLbl, subLbl);
        header.setPadding(new Insets(20, 24, 16, 24));
        header.setStyle(
                "-fx-background-color: linear-gradient(to right, #0A84FF, #30d5f5); " +
                        "-fx-background-radius: 18px 18px 0 0;"
        );
        return header;
    }

    private VBox card(VBox header, VBox body, Region divider, HBox btnRow) {
        VBox card = new VBox(header, body, divider, btnRow);
        card.setStyle(
                "-fx-background-color: #141420; -fx-background-radius: 18px; " +
                        "-fx-effect: dropshadow(gaussian, rgba(10,132,255,0.55), 36, 0, 0, 0);"
        );
        return card;
    }

    private Region divider() {
        Region d = new Region();
        d.setMinHeight(1);
        d.setStyle("-fx-background-color: rgba(255,255,255,0.08);");
        return d;
    }

    private HBox buttonRow(Button left, Button right) {
        HBox row = new HBox(10, left, right);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(12, 20, 18, 20));
        return row;
    }

    private Label statusLabel() {
        Label lbl = new Label();
        lbl.setStyle(
                "-fx-text-fill: #ff6b6b; -fx-font-size: 12px; " +
                        "-fx-font-family: 'SF Pro Text', 'Segoe UI', sans-serif;"
        );
        lbl.setWrapText(true);
        return lbl;
    }

    private void shakeNode(Node node) {
        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO,        new KeyValue(node.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(50),  new KeyValue(node.translateXProperty(), -8)),
                new KeyFrame(Duration.millis(100), new KeyValue(node.translateXProperty(), 8)),
                new KeyFrame(Duration.millis(150), new KeyValue(node.translateXProperty(), -8)),
                new KeyFrame(Duration.millis(200), new KeyValue(node.translateXProperty(), 0))
        );
        shake.play();
    }

    private Button buildDialogButton(String text, boolean primary) {
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

    private String headerLabelStyle() {
        return "-fx-text-fill: white; -fx-font-size: 17px; -fx-font-weight: bold; " +
                "-fx-font-family: 'SF Pro Display', 'Segoe UI', sans-serif;";
    }

    private String subLabelStyle() {
        return "-fx-text-fill: rgba(255,255,255,0.78); -fx-font-size: 12px; " +
                "-fx-font-family: 'SF Pro Text', 'Segoe UI', sans-serif;";
    }

    private String fieldStyle() {
        return "-fx-background-color: rgba(255,255,255,0.07); " +
                "-fx-text-fill: white; " +
                "-fx-prompt-text-fill: rgba(255,255,255,0.35); " +
                "-fx-background-radius: 10px; " +
                "-fx-border-color: rgba(255,255,255,0.12); " +
                "-fx-border-radius: 10px; " +
                "-fx-border-width: 1px; " +
                "-fx-padding: 10px 14px; " +
                "-fx-font-size: 13px; " +
                "-fx-font-family: 'SF Pro Text', 'Segoe UI', sans-serif;";
    }
}
