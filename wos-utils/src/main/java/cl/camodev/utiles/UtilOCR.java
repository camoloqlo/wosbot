package cl.camodev.utiles;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import cl.camodev.wosbot.ot.DTOPoint;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class UtilOCR {

        /**
         * Performs OCR on a specific region of an image.
         *
         * @param imagePath Path to the image on disk.
         * @param p1        First point defining the region (e.g. top-left corner).
         * @param p2        Second point defining the region (e.g. bottom-right corner).
         * @return The text recognized in the region.
         * @throws IOException              If the image cannot be loaded.
         * @throws TesseractException       If an error occurs during OCR.
         * @throws IllegalArgumentException If the specified region exceeds image bounds.
         */
	public static String ocrFromRegion(String imagePath, DTOPoint p1, DTOPoint p2) throws IOException, TesseractException {
                // Load image from the provided path
		File imageFile = new File(imagePath);
		BufferedImage image = ImageIO.read(imageFile);
		if (image == null) {
                        throw new IOException("Could not load image: " + imagePath);
		}

                // Calculate the region to extract:
                // Determine the top-left point (x, y) and compute width and height
		int x = (int) Math.min(p1.getX(), p2.getX());
		int y = (int) Math.min(p1.getY(), p2.getY());
		int width = (int) Math.abs(p1.getX() - p2.getX());
		int height = (int) Math.abs(p1.getY() - p2.getY());

                // Validate that the region stays within the image bounds
		if (x + width > image.getWidth() || y + height > image.getHeight()) {
                        throw new IllegalArgumentException("Specified region is outside the image bounds.");
		}

                // Extract the subimage (region of interest)
		BufferedImage subImage = image.getSubimage(x, y, width, height);

                // Configure Tesseract
		Tesseract tesseract = new Tesseract();
                // Set the path to the tessdata folder
		tesseract.setDatapath("/lib/tesseract");
                // Set the language (e.g. "eng")
		tesseract.setLanguage("eng");

                // Run OCR on the subimage and return the result
		return tesseract.doOCR(subImage);
	}

}
