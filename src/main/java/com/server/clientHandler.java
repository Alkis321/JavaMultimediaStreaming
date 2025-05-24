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
                
                // Check for exit message
                if (EXIT_MESSAGE.equalsIgnoreCase(inputLine.trim())) {
                    out.println("Goodbye!");
                    break;
                }
                if (inputLine.startsWith("SPEED ")) {
                    logger.info("Client reported download speed: " + inputLine.substring(6) + " Mbps");
                    continue; 
                }
                if (inputLine.startsWith("STREAM")) {
                    // STREAM <video> <format> <proto>
                    logger.info("Clien requested {} streaming", inputLine); 
                    
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
                            uri = "udp://" + Config.ADDRESS + ":" + Config.PROTOCOL_PORTS.get(protocol) + "?listen";
                            break;
                        default:
                            uri = "rtp://" + Config.ADDRESS + ":" + Config.PROTOCOL_PORTS.get(protocol) + "?listen";
                            break;
                    }
                    List<String> fullCommand = createFFMpegStreamCommand(uri, video, protocol);
                    new Thread(() -> {
                        try {
                            logger.info("Starting ffmpeg with command: " + commandToString(fullCommand));
                            new ProcessBuilder(fullCommand)
                                .redirectErrorStream(true)
                                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                                .start();

                            Thread.sleep(500); // Wait for ffmpeg to start
                        } catch (Exception e) {
                            logger.error("Error starting ffmpeg: ", e);
                        }


                    }).start();
                }
                
                // Echo message back with server acknowledgment
                out.println("Server received: " + inputLine);
            }
            
            //Here streaming capabilities will be handled
            

            System.out.println("Client disconnecting: " + clientSocket.getInetAddress().getHostAddress());
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }


    private List<String> createFFMpegStreamCommand(String uri, String video, String protocol) {
        String transportStream = "mpegts";
        if(protocol.equals("RTP/UDP")) {
            transportStream = "rtp";
        } 
        List<String> fullCommand = new ArrayList<>(List.of(
            "ffmpeg", "-re", "-i", Paths.get(Config.CONVERTED_VIDEOS_DIR, video).toString(),
            "-an", "-c:v", "copy",
            "-fflags", "+nobuffer",
            "-f", transportStream, uri
        ));


        return fullCommand;
    }

    private String commandToString(List<String> command) {
        return String.join(" ", command);
    }
}
