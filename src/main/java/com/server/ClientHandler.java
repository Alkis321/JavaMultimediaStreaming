package com.server;

import java.io.*;
import java.net.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientHandler implements Runnable {
    private static final String EXIT_MESSAGE = "exit";
    private final Socket clientSocket;
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private Process ffmpegProcess;

    public ClientHandler(Socket clientSocket) {
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
            out.println("Available Videos: " + String.join(", ",  VideoCatalog.listConvertedVideos()));
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
                    String video = message[1];
                    String protocol = message[3];
                    String uri;
                    switch (protocol) {
                        case "TCP":
                            uri = "tcp://" + Config.ADDRESS + ":" + Config.PROTOCOL_PORTS.get(protocol) + "?listen";
                            break;
                        case "UDP":
                            uri = "udp://" + Config.ADDRESS + ":" + Config.PROTOCOL_PORTS.get(protocol) + "?pkt_size=188";
                            break;
                        default:
                            uri = "rtp://" + Config.ADDRESS + ":" + Config.PROTOCOL_PORTS.get(protocol);
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
                if (inputLine.startsWith("HLS ")) {
                    // HLS <videoName>
                    commandHandled = true;
                    String[] parts = inputLine.split("\\s+");
                    String videoName = parts[1];
                    //log the request for statistics
                    logger.info("Client requested HLS for video: {}", videoName);                    
                }
                if (!commandHandled) {
                    out.println("Server received UNHANDLED: " + inputLine);
                }
            }            
            logger.info("Client disconnecting: " + clientSocket.getInetAddress().getHostAddress());

        } catch (IOException e) {
            logger.error("Error handling client: ", e);
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
        List<String> fullCommand = new ArrayList<>(List.of(
            "ffmpeg", "-re", "-i", Paths.get(Config.CONVERTED_VIDEOS_DIR, video).toString()
        ));

        switch (protocol) {
            case "RTP/UDP":
                fullCommand.addAll(List.of(
                    "-fflags", "+nobuffer+flush_packets",
                    "-flags", "global_header",
                    "-c:v", "libx264",
                    "-b:v", "3000k",
                    "-bsf:v", "h264_mp4toannexb",
                    "-an",
                    "-maxrate", "3000k",
                    "-bufsize", "6000k",
                    "-g", "15",
                    "-x264-params", "keyint=15:min-keyint=15:scenecut=0:intra-refresh=1",
                    "-flush_packets", "1",
                    "-muxdelay", "0",
                    "-muxpreload", "0",
                    "-preset", "ultrafast", 
                    "-tune", "zerolatency",
                    "-max_delay", "0",
                    "-payload_type", "96",
                    "-sdp_file", Config.STREAM_SDP_DIR,
                    "-f", "rtp"
                ));
                break;
            case "TCP":
                if (Paths.get(Config.CONVERTED_VIDEOS_DIR, video).toString().toLowerCase().endsWith(".avi")) {
                    logger.info("Using special handling for AVI file: {}", video);
                    //special handling for AVI files
                    fullCommand.addAll(List.of(
                        "-fflags", "+genpts",
                        "-c:v", "libx264",
                        "-preset", "ultrafast",
                        "-tune", "zerolatency",
                        "-c:a", "aac",
                        "-b:v", "2000k",
                        "-b:a", "128k",
                        "-flush_packets", "1",
                        "-muxdelay", "0",
                        "-muxpreload", "0",
                        "-f", "mpegts"
                    ));
                } else {
                    fullCommand.addAll(List.of(
                        "-fflags", "+flush_packets",
                        "-c:v", "copy",
                        "-c:a", "copy",
                        "-b:v", "2000k",
                        "-b:a", "128k",
                        "-bsf:v", "h264_mp4toannexb",
                        "-flush_packets", "1",
                        "-muxdelay", "0",
                        "-muxpreload", "0",
                        "-preset", "ultrafast", 
                        "-tune", "zerolatency",
                        "-max_delay", "0",
                        "-f", "mpegts"
                    ));
                }
                break;
            case "UDP":
                fullCommand.addAll(List.of(
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-tune", "zerolatency",
                    "-g", "15",
                    "-keyint_min", "15",
                    "-sc_threshold", "0",
                    "-bsf:v", "h264_mp4toannexb",
                    "-f", "h264"
                ));
                break;
            default:
                break;
        }
        fullCommand.add(uri);

        return fullCommand;
    }

    private String commandToString(List<String> command) {
        return String.join(" ", command);
    }
}
