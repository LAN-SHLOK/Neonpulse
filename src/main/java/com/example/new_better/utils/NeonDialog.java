package com.example.new_better.utils;

import com.example.new_better.MainApp;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class NeonDialog {

    public static void showInfo(String titleText, String messageText) {

        Stage owner = MainApp.getPrimaryStage();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("neon-dialog");

        Label title = new Label(titleText);
        title.getStyleClass().add("neon-dialog-title");

        Label message = new Label(messageText);
        message.getStyleClass().add("neon-dialog-message");

        Button okBtn = new Button("OK");
        okBtn.getStyleClass().add("button-primary");

        okBtn.setOnAction(e -> dialog.close());

        root.getChildren().addAll(title, message, okBtn);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        scene.getStylesheets().add(
                MainApp.class.getResource("/com/example/new_better/css/main.css").toExternalForm()
        );

        dialog.setScene(scene);
        dialog.showAndWait();
    }

    public static boolean showConfirm(String titleText, String messageText) {

        Stage owner = MainApp.getPrimaryStage();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("neon-dialog");

        Label title = new Label(titleText);
        title.getStyleClass().add("neon-dialog-title");

        Label message = new Label(messageText);
        message.getStyleClass().add("neon-dialog-message");

        HBox buttons = new HBox(15);
        buttons.setAlignment(Pos.CENTER);

        Button yesBtn = new Button("Yes");
        yesBtn.getStyleClass().add("button-primary");

        Button noBtn = new Button("Cancel");
        noBtn.getStyleClass().add("button");

        final boolean[] result = {false};

        yesBtn.setOnAction(e -> {
            result[0] = true;
            dialog.close();
        });

        noBtn.setOnAction(e -> dialog.close());

        buttons.getChildren().addAll(noBtn, yesBtn);
        root.getChildren().addAll(title, message, buttons);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        scene.getStylesheets().add(
                MainApp.class.getResource("/com/example/new_better/css/main.css").toExternalForm()
        );

        dialog.setScene(scene);
        dialog.showAndWait();

        return result[0];
    }
}
