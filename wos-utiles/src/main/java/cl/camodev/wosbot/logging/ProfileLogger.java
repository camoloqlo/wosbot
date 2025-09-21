package cl.camodev.wosbot.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cl.camodev.wosbot.ot.DTOProfiles;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

/**
 * ProfileLogger - A wrapper for SLF4J that creates separate log files for each profile
 * This class adds profile-specific logging capability without modifying the existing logging configuration
 */
public class ProfileLogger {
    private static final Logger mainLogger = LoggerFactory.getLogger(ProfileLogger.class);
    private static final Map<Long, PrintWriter> profileLogWriters = new ConcurrentHashMap<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat fileNameDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    
    // Log rotation settings
    private static final long MAX_LOG_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_BACKUP_FILES = 5;
    
    private final Logger logger;
    private final DTOProfiles profile;
    private final String className;
    
    /**
     * Create a new ProfileLogger for a specific class and profile
     * 
     * @param clazz The class using the logger
     * @param profile The profile to log for, or null for the main logger
     */
    public ProfileLogger(Class<?> clazz, DTOProfiles profile) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.profile = profile;
        this.className = clazz.getSimpleName();
        
        // Ensure log directory exists
        try {
            Files.createDirectories(Paths.get("log"));
        } catch (IOException e) {
            mainLogger.error("Failed to create log directory", e);
        }
        
        // Initialize profile log writer if needed
        if (profile != null && !profileLogWriters.containsKey(profile.getId())) {
            try {
                initializeProfileLogWriter(profile);
            } catch (IOException e) {
                mainLogger.error("Failed to initialize log writer for profile " + profile.getName(), e);
            }
        }
    }
    
    /**
     * Create a new ProfileLogger for a specific class without a profile
     * 
     * @param clazz The class using the logger
     */
    public ProfileLogger(Class<?> clazz) {
        this(clazz, null);
    }
    
    /**
     * Initialize a log writer for a profile
     * 
     * @param profile The profile to initialize a log writer for
     * @throws IOException If the log file cannot be created
     */
    private synchronized void initializeProfileLogWriter(DTOProfiles profile) throws IOException {
        if (profileLogWriters.containsKey(profile.getId())) {
            return;
        }
        
        String sanitizedProfileName = sanitizeFileName(profile.getName());
        String logFilePath = "log/profile_" + sanitizedProfileName + "_" + profile.getId() + ".log";
        
        File logFile = new File(logFilePath);
        
        // Check if log file exists and needs to be rotated
        if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
            rotateLogFile(logFile);
        } else if (!logFile.exists()) {
            logFile.createNewFile();
        }
        
        PrintWriter writer = new PrintWriter(new FileWriter(logFile, true), true);
        profileLogWriters.put(profile.getId(), writer);
        
        // Write header to log file
        writer.println("==========================================================");
        writer.println("Profile Log Started: " + dateFormat.format(new Date()));
        writer.println("Profile: " + profile.getName() + " (ID: " + profile.getId() + ")");
        writer.println("Emulator: " + profile.getEmulatorNumber());
        writer.println("==========================================================");
    }
    
    /**
     * Rotate the log file by compressing it and creating a new one
     * 
     * @param logFile The log file to rotate
     * @throws IOException If an error occurs during rotation
     */
    private void rotateLogFile(File logFile) throws IOException {
        String logFileName = logFile.getName();
        String logBaseName = logFileName.substring(0, logFileName.lastIndexOf('.'));
        String date = fileNameDateFormat.format(new Date());
        
        // Find the next available index
        int index = 0;
        boolean indexFound = false;
        
        while (!indexFound && index < MAX_BACKUP_FILES) {
            File backupFile = new File(logFile.getParent(), logBaseName + "." + date + "." + index + ".gz");
            if (!backupFile.exists()) {
                indexFound = true;
            } else {
                index++;
            }
        }
        
        // If we've reached max backups, delete oldest one based on name
        if (!indexFound) {
            File[] backupFiles = logFile.getParentFile().listFiles((dir, name) -> 
                name.startsWith(logBaseName) && name.endsWith(".gz"));
            
            if (backupFiles != null && backupFiles.length > 0) {
                // Sort by name to find oldest (assuming date-based naming)
                File oldestFile = backupFiles[0];
                for (File file : backupFiles) {
                    if (file.getName().compareTo(oldestFile.getName()) < 0) {
                        oldestFile = file;
                    }
                }
                
                if (!oldestFile.delete()) {
                    mainLogger.warn("Failed to delete oldest backup file: " + oldestFile.getAbsolutePath());
                }
            }
            
            // Reset index to 0
            index = 0;
        }
        
        // Create backup file with gzip compression
        File backupFile = new File(logFile.getParent(), logBaseName + "." + date + "." + index + ".gz");
        
        try (FileInputStream fis = new FileInputStream(logFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             FileOutputStream fos = new FileOutputStream(backupFile);
             GZIPOutputStream gzos = new GZIPOutputStream(new BufferedOutputStream(fos))) {
            
            byte[] buffer = new byte[8192];
            int len;
            while ((len = bis.read(buffer)) > 0) {
                gzos.write(buffer, 0, len);
            }
        }
        
        // Clear the original log file
        new FileWriter(logFile, false).close();
    }
    
    /**
     * Sanitize a file name to remove invalid characters
     * 
     * @param fileName The file name to sanitize
     * @return The sanitized file name
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    /**
     * Format a log message with a timestamp and class name
     * 
     * @param level The log level
     * @param message The log message
     * @return The formatted log message
     */
    private String formatLogMessage(String level, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(dateFormat.format(new Date()));
        sb.append(" [").append(level).append("] ");
        sb.append(className).append(" - ");
        sb.append(message);
        return sb.toString();
    }
    
    /**
     * Log an INFO message
     * 
     * @param message The message to log
     */
    public void info(String message) {
        // Log to the main logger
        logger.info(message);
        
        // Also log to the profile log file if a profile is set
        if (profile != null) {
            PrintWriter writer = profileLogWriters.get(profile.getId());
            if (writer != null) {
                checkAndRotateLogIfNeeded(profile);
                writer.println(formatLogMessage("INFO", message));
            }
        }
    }
    
    /**
     * Check if the log file needs rotation and rotate if necessary
     * 
     * @param profile The profile to check the log file for
     */
    private void checkAndRotateLogIfNeeded(DTOProfiles profile) {
        if (profile == null) return;
        
        String sanitizedProfileName = sanitizeFileName(profile.getName());
        String logFilePath = "log/profile_" + sanitizedProfileName + "_" + profile.getId() + ".log";
        File logFile = new File(logFilePath);
        
        if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
            try {
                // Close the current writer
                PrintWriter writer = profileLogWriters.remove(profile.getId());
                if (writer != null) {
                    writer.close();
                }
                
                // Rotate the log file
                rotateLogFile(logFile);
                
                // Create a new writer
                initializeProfileLogWriter(profile);
            } catch (IOException e) {
                mainLogger.error("Failed to rotate log file for profile " + profile.getName(), e);
            }
        }
    }
    
    /**
     * Log a DEBUG message
     * 
     * @param message The message to log
     */
    public void debug(String message) {
        // Log to the main logger
        logger.debug(message);
        
        // Also log to the profile log file if a profile is set
        if (profile != null) {
            PrintWriter writer = profileLogWriters.get(profile.getId());
            if (writer != null) {
                checkAndRotateLogIfNeeded(profile);
                writer.println(formatLogMessage("DEBUG", message));
            }
        }
    }
    
    /**
     * Log a WARN message
     * 
     * @param message The message to log
     */
    public void warn(String message) {
        // Log to the main logger
        logger.warn(message);
        
        // Also log to the profile log file if a profile is set
        if (profile != null) {
            PrintWriter writer = profileLogWriters.get(profile.getId());
            if (writer != null) {
                checkAndRotateLogIfNeeded(profile);
                writer.println(formatLogMessage("WARN", message));
            }
        }
    }
    
    /**
     * Log an ERROR message
     * 
     * @param message The message to log
     */
    public void error(String message) {
        // Log to the main logger
        logger.error(message);
        
        // Also log to the profile log file if a profile is set
        if (profile != null) {
            PrintWriter writer = profileLogWriters.get(profile.getId());
            if (writer != null) {
                checkAndRotateLogIfNeeded(profile);
                writer.println(formatLogMessage("ERROR", message));
            }
        }
    }
    
    /**
     * Log an ERROR message with an exception
     * 
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void error(String message, Throwable throwable) {
        // Log to the main logger
        logger.error(message, throwable);
        
        // Also log to the profile log file if a profile is set
        if (profile != null) {
            PrintWriter writer = profileLogWriters.get(profile.getId());
            if (writer != null) {
                checkAndRotateLogIfNeeded(profile);
                writer.println(formatLogMessage("ERROR", message));
                throwable.printStackTrace(writer);
            }
        }
    }
    
    /**
     * Close all log writers
     * This should be called when the application is shutting down
     */
    public static void closeAllLogWriters() {
        for (PrintWriter writer : profileLogWriters.values()) {
            writer.close();
        }
        profileLogWriters.clear();
    }
}