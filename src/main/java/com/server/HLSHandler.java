package com.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HLSHandler implements HttpHandler{
    private final Path baseHLSDirectory;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HLSHandler.class);

    public HLSHandler(String hlsDirectory) {
        this.baseHLSDirectory = Paths.get(hlsDirectory).toAbsolutePath().normalize();
    }


    @Override
    public void handle(HttpExchange exchange) throws IOException {
    try {
        String requestPath = exchange.getRequestURI().getPath();
        logger.debug("DEBUG - Request path: " + requestPath);
        
        String relative = requestPath.replaceFirst("/hls-output", "");
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        logger.debug("DEBUG - Relative path: " + relative);
        
        Path fileOnDisk = baseHLSDirectory.resolve(relative).normalize();
        logger.debug("DEBUG - Base dir: " + baseHLSDirectory);
        logger.debug("DEBUG - File path: " + fileOnDisk);
        logger.debug("DEBUG - File exists: " + Files.exists(fileOnDisk));
        
        if (!fileOnDisk.startsWith(baseHLSDirectory) ||
            !Files.exists(fileOnDisk) ||
            Files.isDirectory(fileOnDisk)) {
            logger.debug("DEBUG - 404 will be sent");
            send404(exchange);
            return;
        }
        // Add this to your handle method right before setting the Content-Type header:
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        String contentType = guessContentType(fileOnDisk.getFileName().toString());
        logger.debug("DEBUG - Content type: " + contentType);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        long length = Files.size(fileOnDisk);
        logger.debug("DEBUG - File size: " + length);
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, length);

        try (OutputStream os = exchange.getResponseBody();
             InputStream is = Files.newInputStream(fileOnDisk)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            logger.debug("DEBUG - File sent successfully");
        }
    } catch (Exception e) {
        logger.debug("DEBUG - EXCEPTION: " + e.getMessage());
        e.printStackTrace();
        send404(exchange);
    }
}

    private void send404(HttpExchange exchange) throws IOException {
        byte[] msg = "404 Not Found".getBytes();
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, msg.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(msg);
        }
    }

    private String guessContentType(String filename) {
        if (filename.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        } else if (filename.endsWith(".ts")) {
            return "video/MP2T";
        } else {
            return "application/octet-stream";
        }
    }
}
