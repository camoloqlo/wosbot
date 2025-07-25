package cl.camodev.wosbot.updater;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class AutoUpdater {
    private static final Logger logger = LoggerFactory.getLogger(AutoUpdater.class);

    private static final String DOWNLOAD_NAME = "update.zip";
    private static final Path BACKUP_DIR = Paths.get("backup");

    public boolean handleUpdateFlow(String[] args) {
        try {
            if (args.length > 0 && args[0].equals("--post-update")) {
                Path updateSubdir = Paths.get(args[1]);
                logger.info("Relocating new version to root from: {}", updateSubdir);
                moveNewVersionToRoot(updateSubdir);

                logger.info("Launching final cleanup from updated root...");
                ProcessBuilder pb = new ProcessBuilder("java", "-jar", getUpdatedJarName(), "--final-cleanup", updateSubdir.toString());
                pb.inheritIO();
                pb.start();

                System.exit(0);
                return true;
            }

            if (args.length > 0 && args[0].equals("--final-cleanup")) {
                Path updateSubdir = Paths.get(args[1]);
                logger.info("Cleaning up old update directory and backup...");
                cleanUp(updateSubdir, Paths.get("update.zip"));

                return false;
            }

            if (args.length == 0) {
                checkForUpdatesAndRun();
            }

        } catch (Exception e) {
            logger.error("Update handling failed: {}", e.getMessage(), e);
            System.exit(1);
        }

        return false;
    }

    private String getUpdatedJarName() throws IOException {
        try (Stream<Path> stream = Files.list(Paths.get("."))) {
            return stream
                    .filter(p -> p.toString().endsWith(".jar"))
                    .findFirst()
                    .map(Path::toString)
                    .orElseThrow(() -> new FileNotFoundException("Updated JAR not found in root directory."));
        }
    }

    public boolean checkForUpdatesAndRun() {
        try {
            String version = getManifestAttribute("Implementation-Version");
            String repoUrl = getManifestAttribute("Repository-Url");

            if (version == null || repoUrl == null) {
                logger.warn("Missing version or repository URL in manifest. Skipping update.");
                return false;
            }

            logger.info("Current version: v{}", version);

            String[] parts = repoUrl.replace("https://github.com/", "").split("/");
            if (parts.length < 2) {
                logger.warn("Invalid GitHub repo URL: {}", repoUrl);
                return false;
            }

            URI apiUri = new URI("https", "api.github.com", "/repos/" + parts[0] + "/" + parts[1] + "/releases/latest", null);
            HttpURLConnection conn = (HttpURLConnection) apiUri.toURL().openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            reader.close();

            JSONObject release = new JSONObject(json.toString());
            String latestVersion = release.getString("tag_name");

            if (latestVersion.replaceFirst("^v", "").equals(version)) {
                logger.info("No updates available.");
                return false;
            }

            // Prompt user for update using custom FXML dialog
            boolean userWantsUpdate = showUpdatePromptDialog("A new version (" + latestVersion + ") is available. Update now?");
            if (!userWantsUpdate) {
                logger.info("User declined update.");
                return false;
            }

            logger.info("New version available: {}", latestVersion);
            String downloadUrl = release.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");

            logger.info("Downloading update from {}", downloadUrl);
            try (InputStream in = URI.create(downloadUrl).toURL().openStream()) {
                Files.copy(in, Paths.get(DOWNLOAD_NAME), StandardCopyOption.REPLACE_EXISTING);
            }

            extractZip(Paths.get(DOWNLOAD_NAME), Paths.get("."));
            backupCurrentInstallation();

            Path subDir = findExtractedSubdir();
            String newJar = findJarName(subDir);
            logger.info("Launching new version from subdir: {}", subDir);

            ProcessBuilder pb = new ProcessBuilder("java", "-jar", subDir.resolve(newJar).toString(), "--post-update", subDir.getFileName().toString());
            pb.inheritIO();
            pb.start();

            System.exit(0);
        } catch (Exception e) {
            logger.error("Auto update process failed", e);
        }

        return false;
    }

    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolvedPath = targetDir.resolve(entry.getName()).normalize();

                if (!resolvedPath.toAbsolutePath().normalize().startsWith(targetDir.toAbsolutePath().normalize())) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolvedPath);
                } else {
                    Files.createDirectories(resolvedPath.getParent());
                    Files.copy(zis, resolvedPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private Path findExtractedSubdir() throws IOException {
        try (Stream<Path> stream = Files.list(Paths.get("."))) {
            return stream
                .filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith("wos-bot-"))
                .findFirst()
                .orElseThrow(() -> new FileNotFoundException("No extracted version subdirectory found."));
        }
    }

    private String getManifestAttribute(String name) {
        try (InputStream input = AutoUpdater.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (input == null) return null;
            Manifest manifest = new Manifest(input);
            Attributes attributes = manifest.getMainAttributes();
            return attributes.getValue(name);
        } catch (IOException e) {
            logger.warn("Error reading manifest attribute '{}'", name, e);
            return null;
        }
    }

    private String findJarName(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files
                .filter(p -> Files.isRegularFile(p))
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith("wos-bot") && name.endsWith(".jar");
                })
                .map(p -> dir.relativize(p).toString())
                .findFirst()
                .orElseThrow(() -> new FileNotFoundException("No JAR file starting with 'wos-bot' found in extracted directory."));
        }
    }


    private void backupCurrentInstallation() throws IOException, URISyntaxException {
        Files.createDirectories(BACKUP_DIR);

        Path jarPath = getRunningJarPath();
        Files.copy(jarPath, BACKUP_DIR.resolve(jarPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);

        if (Files.exists(Paths.get("lib"))) {
            copyDirectory(Paths.get("lib"), BACKUP_DIR.resolve("lib"));
        }

        Path db = Paths.get("database.db");
        if (Files.exists(db)) {
            Files.copy(db, BACKUP_DIR.resolve("database.db"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void moveNewVersionToRoot(Path fromSubdir) throws IOException {
        // Remove old root .jar files (not the current one in fromSubdir)
        try (Stream<Path> jarStream = Files.list(Paths.get("."))) {
            jarStream
                .filter(p -> p.toString().endsWith(".jar"))
                .filter(p -> !p.startsWith(fromSubdir)) // Avoid deleting currently running JAR
                .forEach(jar -> {
                    try {
                        Files.delete(jar);
                        logger.info("Deleted old JAR: {}", jar.getFileName());
                    } catch (IOException e) {
                        logger.warn("Failed to delete old JAR: {}", jar.getFileName(), e);
                    }
                });
        }

        // Remove old lib directory
        if (Files.exists(Paths.get("lib"))) {
            deleteRecursively(Paths.get("lib"));
            logger.info("Deleted old lib directory.");
        }

        // Copy all files from subdir into root
        Files.walk(fromSubdir).forEach(path -> {
            try {
                Path relative = fromSubdir.relativize(path);
                Path dest = Paths.get(".").resolve(relative);

                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        logger.info("Copied files from {} to root directory.", fromSubdir);
    }


    public void cleanUp(Path... items) {
        for (Path item : items) {
            try {
                if (Files.isDirectory(item)) {
                    Files.walk(item)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } else {
                    Files.deleteIfExists(item);
                }
                logger.info("Deleted {}", item);
            } catch (IOException e) {
                logger.warn("Cleanup failed for {}", item, e);
            }
        }
    }

    private Path getRunningJarPath() throws URISyntaxException {
        URI uri = AutoUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        return Paths.get(uri);
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path dest = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> entries = Files.walk(path)) {
                entries.sorted(Comparator.reverseOrder())
                       .map(Path::toFile)
                       .forEach(File::delete);
            }
        } else {
            Files.deleteIfExists(path);
        }
    }

    private boolean showUpdatePromptDialog(String message) {
        try {
            return showUpdatePromptDialogInternal(message);
        } catch (Exception e) {
            logger.error("Failed to show update prompt dialog", e);
            return false;
        }
    }

    private boolean showUpdatePromptDialogInternal(String message) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/UpdatePromptDialog.fxml"));
            Parent root = loader.load();
            UpdatePromptDialogController controller = loader.getController();
            controller.setMessage(message);

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Update Available");
            // Set default app icon
            dialogStage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/icons/appIcon.png")));
            Scene scene = new Scene(root);
            // Apply stylesheet
            scene.getStylesheets().add(cl.camodev.wosbot.launcher.view.ILauncherConstants.getCssPath());
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);
            dialogStage.showAndWait();

            return controller.getUserChoice();
        } catch (Exception e) {
            logger.error("Failed to show update prompt dialog (internal)", e);
            return false;
        }
    }
}
