
package com.example.new_better;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class music_app extends Application {

    @Override
    public void start(Stage stage) {
        try {

            String fxmlPath = "/com/example/new_better/views/login.fxml";
            URL location = getClass().getResource(fxmlPath);

            if (location == null) {
                throw new RuntimeException("FXML NOT FOUND at: " + fxmlPath);
            }

            FXMLLoader loader = new FXMLLoader(location);
            Scene scene = new Scene(loader.load());

            stage.setTitle("NeonPulse");
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
