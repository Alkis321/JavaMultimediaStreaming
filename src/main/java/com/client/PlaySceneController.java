package com.client;

import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class PlaySceneController {

    @FXML
    private Button playButton;

    @FXML
    private Button backButton;

    @FXML
    private WebView webView;

    private Scene mainScene; 
    private String hlsUrl;

    public void setMainScene(Scene scene) {
        this.mainScene = scene;
    }

    public void setHlsUrl(String url) {
        this.hlsUrl = url;
    }

    @FXML
    private void initialize() {
        // You could disable playButton until url is set,
        // but since setHlsUrl runs before show, it's fine.
    }

    /** Bound to the Play button’s onAction in playScene.fxml */
    @FXML
    private void onPlayClicked() {
        if (hlsUrl == null || hlsUrl.isEmpty()) {
            return;
        }
        webView.getEngine().load(hlsUrl);
    }

    @FXML
    private void onBackClicked() {
        // Find the current stage and set the original scene
        Stage stage = (Stage) backButton.getScene().getWindow();
        if (mainScene != null) {
            stage.setScene(mainScene);
        }
    }
}
