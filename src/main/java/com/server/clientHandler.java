package com.server;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
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
                    if (parts.length < 2) {
                        out.println("HLS_ERROR Missing video name");
                    } else {
                        String videoName = parts[1];
                        logger.info("Parts: " + String.join(", ", parts));
                        logger.info("Client requested HLS for video: " + videoName);
                        // Notify client to wait
                        out.println("HLS_STARTED");
                        try {
                            
                            // 1) Create output directory: "hls-output/<videoName>"
                            Path outDir = Paths.get("hls-output", videoName);
                            Files.createDirectories(outDir);

                            // 2) Run FFmpeg to generate HLS playlist and segments
                            // Example: ffmpeg -i raw-videos/<videoName>.mp4 -codec: copy -start_number 0 -hls_time 6 -hls_list_size 0 -f hls hls-output/<videoName>/master.m3u8
                            List<String> cmd = createFFMpegHLSCommand(videoName, outDir.toString());
                            ProcessBuilder pb = new ProcessBuilder(cmd);
                            pb.redirectErrorStream(true);
                            Process hlsProc = pb.start();
                            int exitCode = hlsProc.waitFor();
                            if (exitCode == 0) {
                                // 3) On success, send URL to client
                                String encodedVideo = URLEncoder.encode(videoName, "UTF-8");
                                String playlistURL = "http://" + Config.ADDRESS + ":" + Config.HTTP_PORT + "/hls-output/" + encodedVideo + "/master.m3u8";
                                out.println("HLS_READY " + playlistURL);
                                logger.info("HLS generation successful for '{}', URL: {}", videoName, playlistURL);
                            } else {
                                out.println("HLS_ERROR FFmpeg exited with code " + exitCode);
                                logger.error("FFmpeg HLS generation failed for '{}' with exit code {}", videoName, exitCode);
                            }
                        } catch (Exception e) {
                            out.println("HLS_ERROR " + e.getMessage());
                            logger.error("Error during HLS generation for '{}':", videoName, e);
                        }
                    }
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

    private List<String> createFFMpegHLSCommand(String videoName, String outputDir) {
        // Example inputs; adjust paths as needed
        String inputPath = "raw-videos/" + videoName + ".mp4";
        String outputPath = outputDir + "/master.m3u8";
        return List.of(
            "ffmpeg", "-i", inputPath,
            "-codec:", "copy",
            "-start_number", "0",
            "-hls_time", "6",
            "-hls_list_size", "0",
            "-f", "hls",
            outputPath
        );
    }

    private String commandToString(List<String> command) {
        return String.join(" ", command);
    }
}
