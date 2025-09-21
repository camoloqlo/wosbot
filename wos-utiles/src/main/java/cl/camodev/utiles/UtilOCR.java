package cl.camodev.utiles;

import java.awt.image.BufferedImage;

import cl.camodev.wosbot.ot.DTOPoint;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class UtilOCR {

    /**
     * Performs OCR on a specified region of a BufferedImage using Tesseract.
     *
     * @param image    Buffered image to process.
     * @param p1       Top-left point that defines the region.
     * @param p2       Bottom-right point that defines the region.
     * @param language Language code for Tesseract (e.g., "eng" for English, "spa" for Spanish).
     * @return Extracted text from the specified region.
     * @throws TesseractException       If an error occurs during OCR processing.
     * @throws IllegalArgumentException If the image is null or the specified region is invalid.
     *
     */
    public static String ocrFromRegion(BufferedImage image, DTOPoint p1, DTOPoint p2, String language) throws TesseractException {
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

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("lib/tesseract");
        tesseract.setLanguage(language);

        return tesseract.doOCR(subImage);
    }
}

