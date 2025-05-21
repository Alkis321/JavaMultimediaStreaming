package com.server;

public abstract class Config {
  public static final String RAW_VIDEOS_DIR = "./videos/raw"; // Relative path
  public static final String CONVERTED_VIDEOS_DIR = "./videos/converted"; // Relative path
  public static final String FFMPEG_PATH = System.getenv("FFMPEG_PATH");
  //public static final String FFMPEG_PATH = "C:\\Program Files\\FFMPEG\\ffmpeg-7.1.1-full_build\\ffmpeg-7.1.1-full_build\\bin";
  public static final int port = 5000;
  public static final int streamPort = 6000;
  public static final String[] FORMATS = {"mp4", "mkv", "avi"};
  public static final String[] RESOLUTIONS = {"240", "360", "480", "720", "1080"};
  public static final int[] BITRATES = {400, 750, 1000, 2500, 4500};
}
