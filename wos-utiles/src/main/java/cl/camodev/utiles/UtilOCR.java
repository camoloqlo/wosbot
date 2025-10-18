package cl.camodev.utiles;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import javax.imageio.ImageIO;

import cl.camodev.wosbot.ot.DTORawImage;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UtilOCR {

    private static final Logger log = LoggerFactory.getLogger(UtilOCR.class);

    /**
     * Performs OCR on a specified region of a DTORawImage using Tesseract.
     * This is the most efficient method as it works directly with raw image data.
     *
     * @param rawImage Raw image data from screenshot capture
     * @param p1       Top-left point that defines the region
     * @param p2       Bottom-right point that defines the region
     * @param language Language code for Tesseract
     * @return Extracted text from the specified region
     * @throws TesseractException If an error occurs during OCR processing
     */
    public static String ocrFromRegion(DTORawImage rawImage, DTOPoint p1, DTOPoint p2, String language)
            throws TesseractException {
        if (rawImage == null) {
            throw new IllegalArgumentException("Raw image cannot be null.");
        }

        int x = (int) Math.min(p1.getX(), p2.getX());
        int y = (int) Math.min(p1.getY(), p2.getY());
        int width = (int) Math.abs(p1.getX() - p2.getX());
        int height = (int) Math.abs(p1.getY() - p2.getY());

        if (x + width > rawImage.getWidth() || y + height > rawImage.getHeight()) {
            throw new IllegalArgumentException("Specified region exceeds image bounds.");
        }

        // Extract the region directly from raw data and upscale
        BufferedImage processedImage = extractAndUpscaleRegion(rawImage, x, y, width, height, 4);

        Tesseract tesseract = new Tesseract();
        tesseract.setConfigs(Collections.singletonList("quiet"));
        tesseract.setDatapath("lib/tesseract");
        tesseract.setLanguage(language);
        tesseract.setPageSegMode(7); // single line
        tesseract.setOcrEngineMode(1); // LSTM only

        return tesseract.doOCR(processedImage).replace("\n", "").replace("\r", "").trim();
    }

    /**
     * Performs OCR on a specified region of a DTORawImage using Tesseract with custom settings.
     * This is the most efficient method as it works directly with raw image data.
     *
     * @param rawImage Raw image data from screenshot capture
     * @param p1       Top-left point that defines the region
     * @param p2       Bottom-right point that defines the region
     * @param settings DTOTesseractSettings containing OCR configuration
     * @return Extracted text from the specified region
     * @throws TesseractException If an error occurs during OCR processing
     */
    public static String ocrFromRegion(DTORawImage rawImage, DTOPoint p1, DTOPoint p2, DTOTesseractSettings settings)
            throws TesseractException {
        long startTime = System.currentTimeMillis();
        log.debug("=== OCR Process Started ===");

        if (rawImage == null) {
            throw new IllegalArgumentException("Raw image cannot be null.");
        }

        int x = (int) Math.min(p1.getX(), p2.getX());
        int y = (int) Math.min(p1.getY(), p2.getY());
        int width = (int) Math.abs(p1.getX() - p2.getX());
        int height = (int) Math.abs(p1.getY() - p2.getY());

        if (x + width > rawImage.getWidth() || y + height > rawImage.getHeight()) {
            throw new IllegalArgumentException("Specified region exceeds image bounds.");
        }

        log.debug("Region: x={}, y={}, width={}, height={}", x, y, width, height);
        log.debug("Settings: removeBackground={}, textColor={}", settings.isRemoveBackground(), settings.getTextColor());

        // Extract, upscale and process region directly from raw data in a single pass
        long extractStartTime = System.currentTimeMillis();
        BufferedImage processedImage = extractAndProcessRegion(
                rawImage, x, y, width, height, 4,
                settings.isRemoveBackground(), settings.getTextColor()
        );
        long extractEndTime = System.currentTimeMillis();
        log.debug("Image extraction and processing took: {} ms", (extractEndTime - extractStartTime));

        // Configure Tesseract
        long tesseractConfigStartTime = System.currentTimeMillis();
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("lib/tesseract");
        tesseract.setLanguage("eng");

        if (settings.hasPageSegMode()) {
            tesseract.setPageSegMode(settings.getPageSegMode());
        }

        if (settings.hasOcrEngineMode()) {
            tesseract.setOcrEngineMode(settings.getOcrEngineMode());
        }

        if (settings.hasAllowedChars()) {
            tesseract.setVariable("tessedit_char_whitelist", settings.getAllowedChars());
        }
        long tesseractConfigEndTime = System.currentTimeMillis();
        log.debug("Tesseract configuration took: {} ms", (tesseractConfigEndTime - tesseractConfigStartTime));

        // Perform OCR
        long ocrStartTime = System.currentTimeMillis();
        String result = tesseract.doOCR(processedImage).replace("\n", "").replace("\r", "").trim();
        long ocrEndTime = System.currentTimeMillis();
        log.debug("Tesseract OCR execution took: {} ms", (ocrEndTime - ocrStartTime));

        // Optional: dump debug image
        if (settings.isDebug()) {
            long debugStartTime = System.currentTimeMillis();
            try {
                Path projectRoot = Paths.get(System.getProperty("user.dir"));
                Path tempDir = projectRoot.resolve("temp");
                if (!Files.exists(tempDir)) {
                    Files.createDirectories(tempDir);
                }

                String timestamp = String.valueOf(System.currentTimeMillis());

                // Get full image
                BufferedImage fullImage = convertRawImageToBufferedImage(rawImage);

                // Build configuration text
                StringBuilder configText = new StringBuilder();
                configText.append("Tesseract Configuration:");
                configText.append("\n  Language: eng");
                configText.append("\n  Page Seg Mode: ").append(settings.hasPageSegMode() ? settings.getPageSegMode() : "Default");
                configText.append("\n  OCR Engine Mode: ").append(settings.hasOcrEngineMode() ? settings.getOcrEngineMode() : "Default");
                configText.append("\n  Allowed Chars: ").append(settings.hasAllowedChars() ? settings.getAllowedChars() : "All");
                configText.append("\n  Remove Background: ").append(settings.isRemoveBackground());
                configText.append("\n  Text Color: ").append(settings.getTextColor() != null ? settings.getTextColor() : "Auto");
                configText.append("\n  Upscale Factor: 4x");
                configText.append("\n\nDetected Text: \"").append(result).append("\"");

                // Calculate dimensions
                int padding = 20;
                int titleHeight = 40;
                int configBoxHeight = 200;

                // Right side width: max between processed image and config box
                int rightSideWidth = Math.max(processedImage.getWidth(), 500);

                int combinedWidth = fullImage.getWidth() + padding + rightSideWidth;
                int combinedHeight = Math.max(
                        fullImage.getHeight() + titleHeight,
                        processedImage.getHeight() + titleHeight + configBoxHeight + padding
                );

                // Create combined image
                BufferedImage combinedImage = new BufferedImage(
                        combinedWidth,
                        combinedHeight,
                        BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D g2d = combinedImage.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Fill background with white
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, combinedWidth, combinedHeight);

                // === LEFT SIDE: Full image with red rectangle and text ===
                BufferedImage fullImageWithOverlay = new BufferedImage(
                        fullImage.getWidth(),
                        fullImage.getHeight(),
                        BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D g2dFull = fullImageWithOverlay.createGraphics();
                g2dFull.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2dFull.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw the original full image
                g2dFull.drawImage(fullImage, 0, 0, null);

                // Configure graphics for rectangle and text
                g2dFull.setColor(Color.RED);
                g2dFull.setStroke(new BasicStroke(3));

                // Draw red rectangle around the search region
                g2dFull.drawRect(x, y, width, height);

                // Configure font for text
                g2dFull.setFont(new Font("Arial", Font.BOLD, 20));
                FontMetrics fm = g2dFull.getFontMetrics();
                int textWidth = fm.stringWidth(result);
                int textHeight = fm.getHeight();

                // Determine text position (above or below rectangle based on available space)
                int textX = x + 5;
                int textY;

                if (y > textHeight + 10) {
                    // Draw text above the rectangle
                    textY = y - 10;
                } else {
                    // Draw text below the rectangle
                    textY = y + height + textHeight;
                }

                // Draw background for text (semi-transparent black for better readability)
                g2dFull.setColor(new Color(0, 0, 0, 180));
                g2dFull.fillRect(textX - 5, textY - textHeight + 5, textWidth + 10, textHeight);

                // Draw the detected text
                g2dFull.setColor(Color.RED);
                g2dFull.drawString(result, textX, textY);
                g2dFull.dispose();

                // Draw title for left side
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("Arial", Font.BOLD, 16));
                g2d.drawString("Full Image with Region", 10, 20);

                // Draw full image with overlay
                g2d.drawImage(fullImageWithOverlay, 0, titleHeight, null);

                // === RIGHT SIDE: Processed image and configuration ===
                int rightStartX = fullImage.getWidth() + padding;

                // Draw title for processed image
                g2d.drawString("Processed Region", rightStartX + 10, 20);

                // Draw processed image on the right
                g2d.drawImage(processedImage, rightStartX, titleHeight, null);

                // Draw configuration box below processed image
                int configBoxY = titleHeight + processedImage.getHeight() + padding;

                // Draw configuration box background
                g2d.setColor(new Color(240, 240, 240));
                g2d.fillRect(rightStartX, configBoxY, rightSideWidth, configBoxHeight);

                // Draw configuration box border
                g2d.setColor(Color.GRAY);
                g2d.setStroke(new BasicStroke(1));
                g2d.drawRect(rightStartX, configBoxY, rightSideWidth, configBoxHeight);

                // Draw configuration text
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("Monospaced", Font.PLAIN, 12));

                String[] configLines = configText.toString().split("\n");
                int lineY = configBoxY + 20;
                for (String line : configLines) {
                    g2d.drawString(line, rightStartX + 10, lineY);
                    lineY += 18;
                }

                // Draw separator line between left and right
                g2d.setColor(Color.GRAY);
                g2d.setStroke(new BasicStroke(2));
                g2d.drawLine(fullImage.getWidth() + padding/2, 0, fullImage.getWidth() + padding/2, combinedHeight);

                g2d.dispose();

                // Save combined image
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(combinedImage, "png", baos);
                Path outputPath = tempDir.resolve(timestamp + "_debug.png");
                Files.write(outputPath, baos.toByteArray());

                long debugEndTime = System.currentTimeMillis();
                log.debug("Debug image saved took: {} ms", (debugEndTime - debugStartTime));
            } catch (IOException e) {
                log.error("Failed to save debug image: {}", e.getMessage());
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.debug("=== OCR Process Completed === Total time: {} ms, Result: '{}'", totalTime, result);

        return result;
    }

    /**
     * Extracts a region from DTORawImage and upscales it directly without intermediate conversions.
     * This is highly optimized for performance.
     *
     * @param rawImage Raw image data
     * @param x X coordinate of region
     * @param y Y coordinate of region
     * @param width Width of region
     * @param height Height of region
     * @param scaleFactor Scale factor for upscaling
     * @return Upscaled BufferedImage of the region
     */
    private static BufferedImage extractAndUpscaleRegion(DTORawImage rawImage, int x, int y, int width, int height, int scaleFactor) {
        byte[] data = rawImage.getData();
        int bpp = rawImage.getBpp();
        int imageWidth = rawImage.getWidth();

        // Create upscaled image directly
        int newWidth = width * scaleFactor;
        int newHeight = height * scaleFactor;
        BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[newWidth * newHeight];

        if (bpp == 16) {
            // RGB565 format
            for (int dy = 0; dy < newHeight; dy++) {
                for (int dx = 0; dx < newWidth; dx++) {
                    // Map back to source pixel
                    int srcX = x + (dx / scaleFactor);
                    int srcY = y + (dy / scaleFactor);
                    int srcOffset = (srcY * imageWidth + srcX) * 2;

                    int pixel = ((data[srcOffset + 1] & 0xFF) << 8) | (data[srcOffset] & 0xFF);
                    int r = ((pixel >> 11) & 0x1F) << 3;
                    int g = ((pixel >> 5) & 0x3F) << 2;
                    int b = (pixel & 0x1F) << 3;

                    pixels[dy * newWidth + dx] = (r << 16) | (g << 8) | b;
                }
            }
        } else {
            // 32 bpp - RGBA format
            for (int dy = 0; dy < newHeight; dy++) {
                for (int dx = 0; dx < newWidth; dx++) {
                    // Map back to source pixel
                    int srcX = x + (dx / scaleFactor);
                    int srcY = y + (dy / scaleFactor);
                    int srcOffset = (srcY * imageWidth + srcX) * 4;

                    int r = data[srcOffset] & 0xFF;
                    int g = data[srcOffset + 1] & 0xFF;
                    int b = data[srcOffset + 2] & 0xFF;

                    pixels[dy * newWidth + dx] = (r << 16) | (g << 8) | b;
                }
            }
        }

        result.setRGB(0, 0, newWidth, newHeight, pixels, 0, newWidth);
        return result;
    }

    /**
     * Converts a full DTORawImage to BufferedImage.
     * Used only for debug purposes.
     *
     * @param rawImage Raw image data
     * @return BufferedImage
     */
    public static BufferedImage convertRawImageToBufferedImage(DTORawImage rawImage) {
        BufferedImage image = new BufferedImage(rawImage.getWidth(), rawImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[rawImage.getWidth() * rawImage.getHeight()];

        byte[] data = rawImage.getData();
        int bpp = rawImage.getBpp();
        int index = 0;

        if (bpp == 16) {
            // RGB565 format
            for (int y = 0; y < rawImage.getHeight(); y++) {
                for (int x = 0; x < rawImage.getWidth(); x++) {
                    int offset = index * 2;
                    int pixel = ((data[offset + 1] & 0xFF) << 8) | (data[offset] & 0xFF);
                    int r = ((pixel >> 11) & 0x1F) << 3;
                    int g = ((pixel >> 5) & 0x3F) << 2;
                    int b = (pixel & 0x1F) << 3;
                    pixels[index] = (r << 16) | (g << 8) | b;
                    index++;
                }
            }
        } else {
            // 32 bpp - RGBA format
            for (int y = 0; y < rawImage.getHeight(); y++) {
                for (int x = 0; x < rawImage.getWidth(); x++) {
                    int offset = index * 4;
                    int r = data[offset] & 0xFF;
                    int g = data[offset + 1] & 0xFF;
                    int b = data[offset + 2] & 0xFF;
                    pixels[index] = (r << 16) | (g << 8) | b;
                    index++;
                }
            }
        }

        image.setRGB(0, 0, rawImage.getWidth(), rawImage.getHeight(), pixels, 0, rawImage.getWidth());
        return image;
    }

    /**
     * Extracts a region from DTORawImage, upscales it and optionally processes it (background removal)
     * in a SINGLE PASS directly from raw data without intermediate conversions.
     * This is the most optimized method for performance.
     *
     * @param rawImage Raw image data
     * @param x X coordinate of region
     * @param y Y coordinate of region
     * @param width Width of region
     * @param height Height of region
     * @param scaleFactor Scale factor for upscaling
     * @param removeBackground Whether to remove background
     * @param textColor Expected text color (used when removeBackground is true)
     * @return Processed BufferedImage ready for OCR
     */
    private static BufferedImage extractAndProcessRegion(DTORawImage rawImage, int x, int y,
                                                         int width, int height, int scaleFactor,
                                                         boolean removeBackground, Color textColor) {
        byte[] data = rawImage.getData();
        int bpp = rawImage.getBpp();
        int imageWidth = rawImage.getWidth();

        // Create upscaled image directly
        int newWidth = width * scaleFactor;
        int newHeight = height * scaleFactor;
        BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[newWidth * newHeight];

        // Define color threshold for background removal (if enabled)
        int targetR = 0, targetG = 0, targetB = 0;
        int threshold = 50; // Color similarity threshold

        if (removeBackground && textColor != null) {
            targetR = textColor.getRed();
            targetG = textColor.getGreen();
            targetB = textColor.getBlue();
        }

        if (bpp == 16) {
            // RGB565 format - extract, upscale and process in single pass
            for (int dy = 0; dy < newHeight; dy++) {
                for (int dx = 0; dx < newWidth; dx++) {
                    // Map back to source pixel
                    int srcX = x + (dx / scaleFactor);
                    int srcY = y + (dy / scaleFactor);
                    int srcOffset = (srcY * imageWidth + srcX) * 2;

                    int pixel = ((data[srcOffset + 1] & 0xFF) << 8) | (data[srcOffset] & 0xFF);
                    int r = ((pixel >> 11) & 0x1F) << 3;
                    int g = ((pixel >> 5) & 0x3F) << 2;
                    int b = (pixel & 0x1F) << 3;

                    // Apply background removal if enabled
                    if (removeBackground && textColor != null) {
                        int diffR = Math.abs(r - targetR);
                        int diffG = Math.abs(g - targetG);
                        int diffB = Math.abs(b - targetB);

                        // If pixel is similar to text color, keep it black; otherwise white
                        if (diffR <= threshold && diffG <= threshold && diffB <= threshold) {
                            pixels[dy * newWidth + dx] = 0x000000; // Black
                        } else {
                            pixels[dy * newWidth + dx] = 0xFFFFFF; // White
                        }
                    } else {
                        pixels[dy * newWidth + dx] = (r << 16) | (g << 8) | b;
                    }
                }
            }
        } else {
            // 32 bpp - RGBA format - extract, upscale and process in single pass
            for (int dy = 0; dy < newHeight; dy++) {
                for (int dx = 0; dx < newWidth; dx++) {
                    // Map back to source pixel
                    int srcX = x + (dx / scaleFactor);
                    int srcY = y + (dy / scaleFactor);
                    int srcOffset = (srcY * imageWidth + srcX) * 4;

                    int r = data[srcOffset] & 0xFF;
                    int g = data[srcOffset + 1] & 0xFF;
                    int b = data[srcOffset + 2] & 0xFF;

                    // Apply background removal if enabled
                    if (removeBackground && textColor != null) {
                        int diffR = Math.abs(r - targetR);
                        int diffG = Math.abs(g - targetG);
                        int diffB = Math.abs(b - targetB);

                        // If pixel is similar to text color, keep it black; otherwise white
                        if (diffR <= threshold && diffG <= threshold && diffB <= threshold) {
                            pixels[dy * newWidth + dx] = 0x000000; // Black
                        } else {
                            pixels[dy * newWidth + dx] = 0xFFFFFF; // White
                        }
                    } else {
                        pixels[dy * newWidth + dx] = (r << 16) | (g << 8) | b;
                    }
                }
            }
        }

        result.setRGB(0, 0, newWidth, newHeight, pixels, 0, newWidth);
        return result;
    }

}
