package com.server;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

//import com.github.kokorin.jaffree.ffmpeg.*;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
//import com.github.kokorin.jaffree.ffprobe.*;

public class videoCatalog {
    public static List<String> getAvailableVideos() {
        //Creation of converted video directory
        new File(Config.CONVERTED_VIDEOS_DIR).mkdirs();

        //Scan raw folder and generate variants
        File rawDir = new File(Config.RAW_VIDEOS_DIR);
        for (File rawFile : rawDir.listFiles()) {
            String movieName = parseMovieName(rawFile); // Extract "Forrest_Gump" from filename
            generateAllVariants(movieName, rawFile);
        }
        System.out.println("All variants generated.");

        //Return list of available videos (e.g., "Forrest_Gump:mp4,avi,mkv")
        return listConvertedVideos();
    }

    private static String parseMovieName(File file) {
        String name = file.getName(); // "Forrest_Gump-720p.mkv"
        return name.split("-")[0];    // "Forrest_Gump"
    }

    private static void generateAllVariants(String movieName, File sourceFile) {
        for (String format : Config.FORMATS) {
            for (String resolution : Config.RESOLUTIONS) {
                File output = new File(Config.CONVERTED_VIDEOS_DIR, String.format("%s-%sp.%s", movieName, resolution, format));
                if (!output.exists()) {
                    transcodeVideo(sourceFile, output, resolution);
                }
            }
        }
    }

    private static void transcodeVideo(File input, File output, String resolution) {
        FFmpeg.atPath()
            .addInput(UrlInput.fromPath(input.toPath()))
            .addOutput(UrlOutput.toPath(output.toPath())
                .addArguments("-vf", "scale=-2:" + resolution)
                .addArguments("-c:v", "libx264")
            )
            .execute();
    }

    public static List<String> listConvertedVideos() {
        List<String> videos = new ArrayList<>();
        File convertedDir = new File(Config.CONVERTED_VIDEOS_DIR);
        for (File file : convertedDir.listFiles()) {
            videos.add(file.getName()); // e.g., "Forrest_Gump-480p.mp4"
        }
        return videos;
    }

    public static List<String> getBaseVideoNames() {
    return listConvertedVideos().stream()
        .map(fn -> fn.replaceAll("[-_][0-9]+p\\.[^.]+$", ""))
        .distinct()
        .sorted()
        .collect(Collectors.toList());
}

}