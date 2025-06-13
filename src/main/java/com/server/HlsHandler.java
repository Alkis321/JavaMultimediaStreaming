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

public class HlsHandler implements HttpHandler{
    private final Path baseHLSDirectory;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HlsHandler.class);

    public HlsHandler(String hlsDirectory) {
        this.baseHLSDirectory = Paths.get(hlsDirectory).toAbsolutePath().normalize();
    }


    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            //get the request method
            String requestMethod = exchange.getRequestMethod();
            
            String requestPath = exchange.getRequestURI().getPath();        
            String relative = requestPath.replaceFirst("/hls-output", "");
            if (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            
            Path fileOnDisk = baseHLSDirectory.resolve(relative).normalize();
            logger.debug("File path: " + fileOnDisk);
            logger.debug("File exists: " + Files.exists(fileOnDisk));
            
            if (!fileOnDisk.startsWith(baseHLSDirectory) ||
                !Files.exists(fileOnDisk) ||
                Files.isDirectory(fileOnDisk)) {
                logger.error("404 will be sent");
                send404(exchange);
                return;
            }
            
            //CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            
            String contentType = guessContentType(fileOnDisk.getFileName().toString());
            logger.info("Content type: " + contentType);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            long length = Files.size(fileOnDisk);
            logger.info("File size: " + length);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, length);

            // Only write content for GET requests, not for HEAD
            if ("GET".equalsIgnoreCase(requestMethod)) {
                try (OutputStream os = exchange.getResponseBody();
                    InputStream is = Files.newInputStream(fileOnDisk)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                    logger.debug("File sent successfully");
                }
            } else {
                exchange.getResponseBody().close();
                logger.debug("HEAD request handled (no content sent)");
            }
        } catch (Exception e) {
            logger.error("EXCEPTION: " + e.getMessage());
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
            //master playlist file
            return "application/vnd.apple.mpegurl";
        } else if (filename.endsWith(".ts")) {
            //HLS segment file
            return "video/MP2T";
        } else {
            return "application/octet-stream";
        }
    }
}
