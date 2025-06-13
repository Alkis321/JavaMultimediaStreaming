package com.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class HlsCatalog {
    private static final Logger logger = LoggerFactory.getLogger(HlsCatalog.class);
    
    public static void makeHlsSegments() {
        logger.info("Starting HLS segment generation for all videos");
        
        // Create output directory if it doesn't exist
        File hlsOutputDir = new File(Config.HLS_OUTPUT_DIR);
        if (!hlsOutputDir.exists()) {
            hlsOutputDir.mkdirs();
        }
        
        // Get all video files
        File rawDir = new File(Config.RAW_VIDEOS_DIR);
        if (!rawDir.exists()) {
            logger.error("Raw videos directory does not exist: {}", Config.RAW_VIDEOS_DIR);
            return;
        }
        
        // Find all video files with extensions from Config.FORMATS
        File[] videoFiles = rawDir.listFiles((dir, name) -> {
            for (String format : Config.FORMATS) {
                if (name.toLowerCase().endsWith("." + format.toLowerCase())) {
                    return true;
                }
            }
            return false;
        });
        
        if (videoFiles == null || videoFiles.length == 0) {
            logger.warn("No video files found in: {}", Config.RAW_VIDEOS_DIR);
            return;
        }
        
        // Process each video sequentially
        for (File videoFile : videoFiles) {
            processVideo(videoFile);
        }
        
        logger.info("HLS processing completed for all videos");
    }
    
    private static void processVideo(File videoFile) {
        String videoName = videoFile.getName();
        String baseName = videoName.substring(0, videoName.lastIndexOf('.'));

        Path outDir = Paths.get(Config.HLS_OUTPUT_DIR, baseName);
        Path masterPlaylist = outDir.resolve("master.m3u8"); 

        if (Files.exists(outDir) && Files.exists(masterPlaylist)) {
            logger.info("Skipping HLS generation for {} - already exists", baseName);
            return; // Skip processing - already done in a previous run
        }

        logger.info("Processing video for HLS: {}", videoName);
        
        try {
            String resolutionStr = videoName.replaceAll(".*-(\\d+)p\\..*", "$1");
            int sourceHeight;
            try {
                sourceHeight = Integer.parseInt(resolutionStr);
                logger.info("Extracted resolution from filename: {}p", sourceHeight);
            } catch (NumberFormatException e) {
                logger.warn("Could not extract resolution from filename: {}, using 240p", videoName);
                sourceHeight = 240; // Default to lowest resolution
            }
            
            // Find valid resolutions (current and lower)
            List<String> targetResolutions = new ArrayList<>();
            for (String res : Config.RESOLUTIONS) {
                if (Integer.parseInt(res) <= sourceHeight) {
                    targetResolutions.add(res);
                }
            }
            
            // Create output directory
            Path outputDir = Paths.get(Config.HLS_OUTPUT_DIR, baseName);
            Files.createDirectories(outputDir);
            
            // Generate HLS for each resolution
            List<String> playlistPaths = new ArrayList<>();
            
            for (String resolution : targetResolutions) {
                String resDir = outputDir.resolve(resolution + "p").toString();
                Files.createDirectories(Paths.get(resDir));
                
                // Generate HLS for this resolution
                String playlistPath = generateHls(videoFile.getPath(), resDir, resolution);
                if (playlistPath != null) {
                    playlistPaths.add(playlistPath);
                }
            }
            
            // Create master playlist
            if (!playlistPaths.isEmpty()) {
                createMasterPlaylist(outputDir.toString(), playlistPaths, targetResolutions);
                logger.info("HLS generation complete for: {}", baseName);
            }
            
        } catch (Exception e) {
            logger.error("Failed to process video: {} - {}", videoName, e.getMessage());
        }
    }
    
    private static String generateHls(String inputPath, String outputDir, String resolution) 
            throws IOException, InterruptedException {
        
        int height = Integer.parseInt(resolution);
        int width = height * 16 / 9;
        int bitrate = calculateBitrate(height);
        
        String playlistPath = Paths.get(outputDir, "playlist.m3u8").toString();
        
        List<String> cmd = new ArrayList<>();
        cmd.addAll(List.of(
            "ffmpeg", "-i", inputPath,
            "-vf", "scale=w=" + width + ":h=" + height + ":force_original_aspect_ratio=decrease",
            "-c:v", "libx264",
            "-b:v", bitrate + "k",
            "-c:a", "aac",
            "-b:a", "128k",
            "-hls_time", "4",
            "-hls_playlist_type", "vod",
            "-hls_segment_filename", outputDir + "/segment_%03d.ts",
            playlistPath
        ));
        
        logger.info("Running ffmpeg for {}p HLS generation", resolution);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process process = pb.start();
        
        int exitCode = process.waitFor();
        if (exitCode == 0) {
            logger.info("Generated {}p HLS segments for: {}", resolution, inputPath);
            return playlistPath;
        } else {
            logger.error("Failed to generate {}p HLS segments, exit code: {}", resolution, exitCode);
            return null;
        }
    }
    
    private static void createMasterPlaylist(String outputDir, List<String> playlistPaths, List<String> resolutions) 
            throws IOException {
        
        String masterPath = Paths.get(outputDir, "master.m3u8").toString();
        
        try (FileWriter writer = new FileWriter(masterPath)) {
            writer.write("#EXTM3U\n");
            writer.write("#EXT-X-VERSION:3\n");
            
            // Add each variant
            for (int i = 0; i < playlistPaths.size(); i++) {
                int height = Integer.parseInt(resolutions.get(i));
                int width = height * 16 / 9;
                int bitrate = calculateBitrate(height) * 1000; // Convert to bits/s
                
                String relativePath = Paths.get(playlistPaths.get(i)).getFileName().toString();
                String folder = resolutions.get(i) + "p";
                
                writer.write("#EXT-X-STREAM-INF:BANDWIDTH=" + bitrate + ",RESOLUTION=" + width + "x" + height + "\n");
                writer.write(folder + "/" + relativePath + "\n");
            }
        }
        
        logger.info("Created master playlist: {}", masterPath);
    }
    
    private static int calculateBitrate(int height) {
        if (height <= 240) return 400;
        if (height <= 360) return 700;
        if (height <= 480) return 1500;
        if (height <= 720) return 3000;
        return 5000;  // 1080p
    }
}
