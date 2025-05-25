package com.server;

import java.io.*;
import java.net.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class clientHandler implements Runnable {
    private static final String EXIT_MESSAGE = "exit";
    private final Socket clientSocket;
    private static final Logger logger = LoggerFactory.getLogger(clientHandler.class);
    private Process ffmpegProcess;
    public clientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }
    
    @Override
    public void run() {
        try (
            //Handling of client
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            logger.info("Client connected: {}", clientSocket.getRemoteSocketAddress());
            out.println("Available Videos: " + String.join(", ",  videoCatalog.listConvertedVideos()));
            //out.println("Welcome to the server! Type 'exit' to disconnect.");
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                logger.info("Received from client: " + inputLine);
                boolean commandHandled = false;
                
                // Check for exit message
                if (EXIT_MESSAGE.equalsIgnoreCase(inputLine.trim())) {
                    out.println("Goodbye!");
                    commandHandled = true;
                    break;
                }
                if (inputLine.startsWith("SPEED ")) {
                    logger.info("Client reported download speed: " + inputLine.substring(6) + " Mbps");
                    commandHandled = true;
                    continue; 
                }
                if (inputLine.startsWith("STREAM")) {
                    // STREAM <video> <format> <proto>
                    logger.info("Clien requested {} streaming", inputLine);
                    commandHandled = true;
                    
                    String[] message = inputLine.split("\\s+");
                    for (String s : message) {
                        logger.info("p: " + s);
                    }

                    String video = message[1];
                    String protocol = message[3];
                    String uri;
                    switch (protocol) {
                        case "TCP":
                            uri = "tcp://" + Config.ADDRESS + ":" + Config.PROTOCOL_PORTS.get(protocol) + "?listen";
                            break;
                        case "UDP":
                            uri = "udp://" + Config.ADDRESS + ":" + Config.PROTOCOL_PORTS.get(protocol);
                            break;
                        default:
                            uri = "rtp://" + Config.ADDRESS + ":" + Config.PROTOCOL_PORTS.get(protocol) + "?listen";
                            break;
                    }
                    List<String> fullCommand = createFFMpegStreamCommand(uri, video, protocol);
                    new Thread(() -> {
                        try {
                            logger.info("Starting ffmpeg with command: " + commandToString(fullCommand));
                            this.ffmpegProcess = new ProcessBuilder(fullCommand)
                                .redirectErrorStream(true)
                                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                                .start();


                        } catch (Exception e) {
                            logger.error("Error starting ffmpeg: ", e);
                        }


                    }).start();
                }
                
                if (!commandHandled) {
                    out.println("Server received: " + inputLine);
                }
            }            
            logger.info("Client disconnecting: " + clientSocket.getInetAddress().getHostAddress());

        } catch (IOException e) {
            logger.error("Error handling client: " + e.getMessage());
        } finally {
            if (this.ffmpegProcess != null && this.ffmpegProcess.isAlive()) {
                logger.info("Client disconnected, ensuring ffmpeg process is also stopped.");
                this.ffmpegProcess.destroyForcibly();
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket: " + e.getMessage());
            }
        }
    }


    private List<String> createFFMpegStreamCommand(String uri, String video, String protocol) {
        String transportStream = "mpegts"; // Default for TCP/UDP direct streaming
        List<String> fullCommand = new ArrayList<>(List.of(
            "ffmpeg", "-re", "-i", Paths.get(Config.CONVERTED_VIDEOS_DIR, video).toString()
        ));

        if (protocol.equals("RTP/UDP")) {
            transportStream = "rtp";
            fullCommand.addAll(List.of(
                "-c:v", "copy",
                "-ac", "2",
                "-b:v", "1500k",
                "-b:a", "96k"
            ));
        } else {
            fullCommand.addAll(List.of(
                "-c:v", "copy",
                "-c:a", "aac",
                "-ac", "2",
                "-b:v", "1500k",
                "-b:a", "96k",
                "-bsf:v", "h264_mp4toannexb"    
            ));

        }

        // Common flags
        fullCommand.addAll(List.of(
            "-fflags", "nobuffer", 
            "-flush_packets", "1",
            "-muxdelay", "0.001",
            "-muxpreload", "0.001",
            "-preset", "ultrafast", 
            "-tune", "zerolatency"
        ));
        
        fullCommand.add("-f");
        fullCommand.add(transportStream);

        if (protocol.equals("RTP/UDP")) {
            fullCommand.add("-sdp_file");
            fullCommand.add(Config.STREAM_SDP_DIR);
        }
        fullCommand.add(uri);

        return fullCommand;
    }

    private String commandToString(List<String> command) {
        return String.join(" ", command);
    }
}
