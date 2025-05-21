package com.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class clientMain extends Application {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8080;
    private static final Logger logger = LoggerFactory.getLogger(clientMain.class);

    //private static final String EXIT_MESSAGE = "exit";

    @FXML
    private Button sendButton;

    @FXML
    private TextField inputField;

    @FXML
    private TextArea responseArea;

    @FXML
    private ComboBox<String> videoComboBox;
    
    @FXML
    private ComboBox<String> formatComboBox;

    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;
    private String[] vids;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        logger.info("Yo");

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/client/client.fxml"));
        loader.setController(this);
        Parent root = loader.load();

        Scene scene = new Scene(root, 600, 300);
        stage.setTitle("Module Complaint Client");
        stage.setScene(scene);
        stage.show();

        formatComboBox.setDisable(true);
        videoComboBox.setDisable(true);

        setupNetworkConnection();
        initializeFormatComboBox();
    }

    private void setupNetworkConnection() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            logger.info("Connected to server1");
            new Thread(() -> {

                SpeedTestSocket speedTestSocket = new SpeedTestSocket();
                speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {

                    @Override
                    public void onCompletion(SpeedTestReport report) {
                        double downloadRate = report.getTransferRateBit().doubleValue(); // bits per second
                        String speedMsg = "SPEED " + downloadRate;
                        logger.info("Download speed: " + downloadRate + " bps");
                        if (out != null) {
                            out.println(speedMsg);
                        }
                    }
                    @Override
                    public void onError(SpeedTestError error, String errorMessage) {
                        logger.error("SpeedTest error: " + error + " - " + errorMessage);
                    }

                    @Override
                    public void onProgress(float percent, SpeedTestReport report) {}
                });
                // start a small download test (5 seconds) from a public test server
                speedTestSocket.startDownload("http://speedtest.tele2.net/5MB.zip");
            }).start();

            new Thread(() -> {

                try {
                    String line;
                    boolean firstMessage = true;
                    while ((line = in.readLine()) != null) {
                        String msg = line;
                        
                        if (firstMessage && msg.startsWith("Available Videos:")) {
                            String listPart = msg.substring("Available Videos:".length()).trim();
                            vids = listPart.split("\\s*,\\s*");
                            Platform.runLater(() -> formatComboBox.setDisable(false));
                            firstMessage = false;
                            continue;
                        }else{
                            logger.info("Server: " + msg);
                            Platform.runLater(() -> responseArea.appendText("Server: " + msg + "\n"));
                        }
                    }
                } catch (IOException e) {
                    Platform.runLater(() -> responseArea.appendText("Disconnected from server.\n"));
                }
            }).start();

        } catch (IOException e) {
            Platform.runLater(() -> responseArea.appendText("Connection failed: " + e.getMessage() + "\n"));
            inputField.setDisable(true);
            videoComboBox.setDisable(true);
            formatComboBox.setDisable(true);
        }
    }

    @FXML
    private void onSendButtonClicked() {
        String selection = videoComboBox.getValue();
        String message = (selection != null) ? "SELECT " + selection : inputField.getText();
        if (message != null && !message.isBlank() && out != null) {
            out.println(message);
        }
        sendButton.disableProperty()
        .bind(videoComboBox.valueProperty().isNull()
        .and(inputField.textProperty().isEmpty()));

    }

    private void initializeFormatComboBox() {
        formatComboBox.getItems().setAll("mp4", "avi", "mkv");
        formatComboBox.setOnAction(_ -> {
            String format = formatComboBox.getValue();
            if (format == null || vids == null) { 
                return; 
            }
            
            if (format != null && out != null) {
                List<String> filtered = Arrays.stream(vids)
                .filter(name -> name.endsWith("." + format))
                .collect(Collectors.toList());
    
                Platform.runLater(() -> {
                    videoComboBox.getItems().setAll(filtered);
                    videoComboBox.setDisable(filtered.isEmpty());
                });
            }
        });
    }
    
    @Override
    public void stop() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            logger.error("Error closing socket: " + e.getMessage());
        }
    }
}
