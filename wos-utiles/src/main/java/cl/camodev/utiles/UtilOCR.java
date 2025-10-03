package cl.camodev.utiles;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UtilOCR {

    private static final Logger log = LoggerFactory.getLogger(UtilOCR.class);

    /**
     * Performs OCR on a specified region of a BufferedImage using Tesseract.
     * 
     * @param image    Buffered image to process.
     * @param p1       Top-left point that defines the region.
     * @param p2       Bottom-right point that defines the region.
     * @param language Language code for Tesseract (e.g., "eng" for English, "spa"
     *                 for Spanish).
     * @return Extracted text from the specified region.
     * @throws TesseractException       If an error occurs during OCR processing.
     * @throws IllegalArgumentException If the image is null or the specified region
     *                                  is invalid.
     */
    public static String ocrFromRegion(BufferedImage image, DTOPoint p1, DTOPoint p2, String language)
            throws TesseractException {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null.");
        }

        int x = (int) Math.min(p1.getX(), p2.getX());
        int y = (int) Math.min(p1.getY(), p2.getY());
        int width = (int) Math.abs(p1.getX() - p2.getX());
        int height = (int) Math.abs(p1.getY() - p2.getY());

        if (x + width > image.getWidth() || y + height > image.getHeight()) {
            throw new IllegalArgumentException("Specified region exceeds image bounds.");
        }

        BufferedImage subImage = image.getSubimage(x, y, width, height);

        // Upscale x2 for clarity
        BufferedImage resizedImage = new BufferedImage(width * 2, height * 2, subImage.getType());
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(subImage, 0, 0, width * 2, height * 2, null);
        g2d.dispose();

        //Optional: dump debug images to project temp directory
//        try {
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            ImageIO.write(resizedImage, "png", baos);
//
//            // Create temp directory in project root if it doesn't exist
//            java.nio.file.Path projectRoot = java.nio.file.Paths.get(System.getProperty("user.dir"));
//            java.nio.file.Path tempDir = projectRoot.resolve("temp");
//            if (!java.nio.file.Files.exists(tempDir)) {
//                java.nio.file.Files.createDirectories(tempDir);
//            }
//
//            // Generate unique filename with timestamp
//            String timestamp = String.valueOf(System.currentTimeMillis());
//            java.nio.file.Path outputPath = tempDir.resolve("img_cut_resized-" + timestamp + ".png");
//            java.nio.file.Files.write(outputPath, baos.toByteArray());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("lib/tesseract");
        tesseract.setLanguage(language);

        // Configurations to improve numeric OCR
        tesseract.setPageSegMode(7); // single line
        tesseract.setOcrEngineMode(1); // LSTM only

        return tesseract.doOCR(resizedImage).replace("\n", "").replace("\r", "").trim();
    }

    /**
     * Performs OCR on a specified region of a BufferedImage using Tesseract with custom settings.
     * 
     * @param image    Buffered image to process.
     * @param p1       Top-left point that defines the region.
     * @param p2       Bottom-right point that defines the region.
     * @param settings DTOTesseractSettings containing OCR configuration.
     * @return Extracted text from the specified region.
     * @throws TesseractException       If an error occurs during OCR processing.
     * @throws IllegalArgumentException If the image is null or the specified region
     *                                  is invalid.
     */
    public static String ocrFromRegion(BufferedImage image, DTOPoint p1, DTOPoint p2, DTOTesseractSettings settings)
            throws TesseractException {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null.");
        }

        int x = (int) java.lang.Math.min(p1.getX(), p2.getX());
        int y = (int) java.lang.Math.min(p1.getY(), p2.getY());
        int width = (int) java.lang.Math.abs(p1.getX() - p2.getX());
        int height = (int) java.lang.Math.abs(p1.getY() - p2.getY());

        if (x + width > image.getWidth() || y + height > image.getHeight()) {
            throw new IllegalArgumentException("Specified region exceeds image bounds.");
        }

        // Stage 0: Raw captured region
        BufferedImage rawImage = image.getSubimage(x, y, width, height);

        // Stage 1: Cutted/Upscaled x2 for clarity
        BufferedImage cuttedImage = new BufferedImage(width * 2, height * 2, rawImage.getType());
        Graphics2D g2d = cuttedImage.createGraphics();
        g2d.drawImage(rawImage, 0, 0, width * 2, height * 2, null);
        g2d.dispose();

        // Stage 2: Processed image (with background removal if requested)
        BufferedImage processedImage = cuttedImage;
        if (settings.isRemoveBackground()) {
            processedImage = removeBackground(cuttedImage, settings.getTextColor());
        }

        // Optional: dump debug images to project temp directory if debug flag is enabled
        if (settings.isDebug()) {
            try {
                // Create temp directory in project root if it doesn't exist
                Path projectRoot = Paths.get(System.getProperty("user.dir"));
                Path tempDir = projectRoot.resolve("temp");
                if (!Files.exists(tempDir)) {
                    Files.createDirectories(tempDir);
                }
                
                // Generate unique filename with timestamp
                String timestamp = String.valueOf(System.currentTimeMillis());
                
                // Save stage 0: raw
                ByteArrayOutputStream baos0 = new java.io.ByteArrayOutputStream();
                ImageIO.write(rawImage, "png", baos0);
                Path outputPath0 = tempDir.resolve(timestamp + "_0_raw.png");
                Files.write(outputPath0, baos0.toByteArray());
                
                // Save stage 1: cutted
                ByteArrayOutputStream baos1 = new java.io.ByteArrayOutputStream();
                ImageIO.write(cuttedImage, "png", baos1);
                Path outputPath1 = tempDir.resolve(timestamp + "_1_cutted.png");
                Files.write(outputPath1, baos1.toByteArray());
                
                // Save stage 2: processed
                ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                ImageIO.write(processedImage, "png", baos2);
                Path outputPath2 = tempDir.resolve(timestamp + "_2_processed.png");
                Files.write(outputPath2, baos2.toByteArray());
            } catch (IOException e) {
                log.error("Failed to save debug images: {}", e.getMessage());
            }
        }

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("lib/tesseract");
        tesseract.setLanguage("eng");

        // Apply optional settings if provided
        if (settings.hasPageSegMode()) {
            tesseract.setPageSegMode(settings.getPageSegMode());
        }

        if (settings.hasOcrEngineMode()) {
            tesseract.setOcrEngineMode(settings.getOcrEngineMode());
        }

        // Apply allowed characters whitelist if provided
        if (settings.hasAllowedChars()) {
            tesseract.setVariable("tessedit_char_whitelist", settings.getAllowedChars());
        }

        return tesseract.doOCR(processedImage).replace("\n", "").replace("\r", "").trim();
    }

    /**
     * Removes background from an image using OpenCV.
     * Converts pixels matching the target color to black, and everything else to white.
     * 
     * @param image     Image to process.
     * @param textColor Target text color (java.awt.Color with RGB values).
     * @return Processed image with target color converted to black and background to white.
     */
    private static BufferedImage removeBackground(BufferedImage image, Color textColor) {
        if (textColor == null) {
            return image;
        }

        try {
            // Convert BufferedImage to OpenCV Mat
            Mat src = bufferedImageToMat(image);
            
            // Extract RGB values from the Color object
            int targetR = textColor.getRed();
            int targetG = textColor.getGreen();
            int targetB = textColor.getBlue();
            
            // Create a result Mat (BGR format for OpenCV)
            Mat result = new Mat(src.rows(), src.cols(), CvType.CV_8UC3);
            
            // Threshold for color similarity (adjust as needed)
            int threshold = 50;
            
            // Process each pixel
            for (int y = 0; y < src.rows(); y++) {
                for (int x = 0; x < src.cols(); x++) {
                    double[] pixel = src.get(y, x);
                    
                    // OpenCV uses BGR format, so we need to convert
                    int b = (int) pixel[0];
                    int g = (int) pixel[1];
                    int r = (int) pixel[2];
                    
                    // Calculate color distance
                    int distance = Math.abs(r - targetR) + Math.abs(g - targetG) + Math.abs(b - targetB);
                    
                    if (distance < threshold) {
                        // This pixel matches the target color - convert to BLACK
                        result.put(y, x, new double[]{0, 0, 0});
                    } else {
                        // This pixel is background - convert to WHITE
                        result.put(y, x, new double[]{255, 255, 255});
                    }
                }
            }
            
            // Convert Mat back to BufferedImage
            return matToBufferedImage(result);
            
        } catch (Exception e) {
            // If processing fails, return original image
            e.printStackTrace();
            return image;
        }
    }
    
    /**
     * Converts a BufferedImage to an OpenCV Mat.
     * 
     * @param image BufferedImage to convert.
     * @return OpenCV Mat representation.
     */
    private static Mat bufferedImageToMat(BufferedImage image) {
        // Convert to BGR format (OpenCV's default)
        BufferedImage convertedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = convertedImage.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        
        byte[] pixels = ((DataBufferByte) convertedImage.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(convertedImage.getHeight(), convertedImage.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, pixels);
        
        return mat;
    }
    
    /**
     * Converts an OpenCV Mat to a BufferedImage.
     * 
     * @param mat OpenCV Mat to convert.
     * @return BufferedImage representation.
     */
    private static BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] data = new byte[mat.cols() * mat.rows() * (int) mat.elemSize()];
        mat.get(0, 0, data);
        image.getRaster().setDataElements(0, 0, mat.cols(), mat.rows(), data);
        
        return image;
    }
}
