package cl.camodev.utiles;

import java.io.IOException;
import java.io.InputStream;


import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import org.slf4j.*;

public class ImageSearchUtil {
	private static final Logger logger = LoggerFactory.getLogger(ImageSearchUtil.class);

        /**
         * Searches for a template within an image.
         * <p>
         * The main image is loaded from an external path while the template is read from the jar resources. A region of interest (ROI) is
         * defined to limit the search area. Matching is performed using the TM_CCOEFF_NORMED method from OpenCV. The match percentage is
         * the maximum match value multiplied by 100 and compared against the provided threshold.
         * </p>
         *
         * @param templateResourcePath Path to the template within the jar resources.
         * @param topLeftCorner        Top-left corner of the ROI.
         * @param bottomRightCorner    Bottom-right corner of the ROI.
         * @param thresholdPercentage  Match threshold percentage (0 to 100). Matches below this value are ignored.
         * @return A {@link DTOImageSearchResult} containing:
         *         <ul>
         *         <li>Whether a valid match was found.</li>
         *         <li>The match position as a {@link DTOPoint} using the same coordinate system as the main image.</li>
         *         <li>The match percentage.</li>
         *         </ul>
         */

        public static DTOImageSearchResult findTemplate(byte[] image, String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage) {
		try {
                        // Calculate ROI from the corners
			int roiX = topLeftCorner.getX();
			int roiY = topLeftCorner.getY();
			int roiWidth = bottomRightCorner.getX() - topLeftCorner.getX();
			int roiHeight = bottomRightCorner.getY() - topLeftCorner.getY();

                        // Validate that the coordinates form a valid rectangle
			if (roiWidth <= 0 || roiHeight <= 0) {
				logger.error("Invalid ROI: bottomRightCorner must be greater than topLeftCorner in both dimensions.");
				return new DTOImageSearchResult(false, null, 0.0);
			}

                        // Decode the main image directly from the byte array
			MatOfByte matOfByte = new MatOfByte(image);
                        Mat mainImage = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);

                        if (mainImage.empty()) {
				logger.error("Error while loading image from byte array.");
				return new DTOImageSearchResult(false, null, 0.0);
			}

                        // Load the template from resources
			InputStream is = ImageSearchUtil.class.getResourceAsStream(templateResourcePath);
			if (is == null) {
                logger.error("Template resource not found: {}", templateResourcePath);
				return new DTOImageSearchResult(false, null, 0.0);
			}

                        // Read bytes from the template
			byte[] templateBytes = is.readAllBytes();
			is.close();

                        // Decode the template into a Mat
			MatOfByte templateMatOfByte = new MatOfByte(templateBytes);
			Mat template = Imgcodecs.imdecode(templateMatOfByte, Imgcodecs.IMREAD_COLOR);

			if (template.empty()) {
				logger.error("Error decoding template.");
				return new DTOImageSearchResult(false, null, 0.0);
			}

                        // Validate the ROI
                        if (roiX + roiWidth > mainImage.cols() || roiY + roiHeight > mainImage.rows()) {
                                logger.error("ROI exceeds image dimensions. Image size: {}x{}, ROI: {}x{} at ({}, {})",
                                                mainImage.cols(), mainImage.rows(), roiWidth, roiHeight, roiX, roiY);
				return new DTOImageSearchResult(false, null, 0.0);
			}

                        // Create the ROI
                        Rect roi = new Rect(roiX, roiY, roiWidth, roiHeight);
                        Mat roiImage = new Mat(mainImage, roi);

                        // Verify size
                        int resultCols = roiImage.cols() - template.cols() + 1;
                        int resultRows = roiImage.rows() - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				logger.error("Template size is larger than ROI size. Template size: {}x{}, ROI size: {}x{}",
                                                template.cols(), template.rows(), roiImage.cols(), roiImage.rows());
				return new DTOImageSearchResult(false, null, 0.0);
			}

                        // Template matching
                        Mat result = new Mat(resultRows, resultCols, CvType.CV_32FC1);
                        Imgproc.matchTemplate(roiImage, template, result, Imgproc.TM_CCOEFF_NORMED);

                        // Obtain the best match
                        Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
			double matchPercentage = mmr.maxVal * 100.0;

			if (matchPercentage < thresholdPercentage) {
				logger.info("Template {} not found, the match percentage is {}%, which is below the threshold of {}%.",
						templateResourcePath, matchPercentage, thresholdPercentage);
				return new DTOImageSearchResult(false, null, matchPercentage);
			}

                        // Adjust coordinates to the center of the match
			Point matchLoc = mmr.maxLoc;
			double centerX = matchLoc.x + roi.x + (template.cols() / 2.0);
			double centerY = matchLoc.y + roi.y + (template.rows() / 2.0);

			return new DTOImageSearchResult(true, new DTOPoint((int) centerX, (int) centerY), matchPercentage);

		} catch (IOException e) {
			logger.error("Exception during template search.", e);
			return new DTOImageSearchResult(false, null, 0.0);
		}
	}

}
