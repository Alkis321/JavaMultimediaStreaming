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

    public HLSHandler(String hlsDirectory) {
        this.baseHLSDirectory = Paths.get(hlsDirectory).toAbsolutePath().normalize();
    }


    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        String relative = requestPath.replaceFirst("/hls-output", "");
        Path fileOnDisk = baseHLSDirectory.resolve(relative).normalize();

        if (!fileOnDisk.startsWith(baseHLSDirectory) ||
            !Files.exists(fileOnDisk) ||
            Files.isDirectory(fileOnDisk)) {
            send404(exchange);
            return;
        }

        String contentType = guessContentType(fileOnDisk.getFileName().toString());
        exchange.getResponseHeaders().set("Content-Type", contentType);
        long length = Files.size(fileOnDisk);
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, length);

        try (OutputStream os = exchange.getResponseBody();
             InputStream is = Files.newInputStream(fileOnDisk)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
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
