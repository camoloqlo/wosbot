package cl.camodev.wosbot.ot;

import java.awt.Color;

/**
 * DTO for Tesseract OCR configuration settings.
 * Uses builder pattern for flexible configuration.
 */
public class DTOTesseractSettings {
	
	/**
	 * Tesseract Page Segmentation Modes
	 */
	public enum PageSegMode {
		OSD_ONLY(0),                    // Orientation and script detection (OSD) only
		AUTO_OSD(1),                    // Automatic page segmentation with OSD
		AUTO_ONLY(2),                   // Automatic page segmentation, but no OSD, or OCR
		AUTO(3),                        // Fully automatic page segmentation, but no OSD (Default)
		SINGLE_COLUMN(4),               // Assume a single column of text of variable sizes
		SINGLE_BLOCK_VERT_TEXT(5),      // Assume a single uniform block of vertically aligned text
		SINGLE_BLOCK(6),                // Assume a single uniform block of text
		SINGLE_LINE(7),                 // Treat the image as a single text line
		SINGLE_WORD(8),                 // Treat the image as a single word
		CIRCLE_WORD(9),                 // Treat the image as a single word in a circle
		SINGLE_CHAR(10),                // Treat the image as a single character
		SPARSE_TEXT(11),                // Sparse text. Find as much text as possible in no particular order
		SPARSE_TEXT_OSD(12),            // Sparse text with OSD
		RAW_LINE(13);                   // Raw line. Treat the image as a single text line, bypassing hacks that are Tesseract-specific
		
		private final int value;
		
		PageSegMode(int value) {
			this.value = value;
		}
		
		public int getValue() {
			return value;
		}
	}
	
	/**
	 * Tesseract OCR Engine Modes
	 */
	public enum OcrEngineMode {
		LEGACY(0),                      // Legacy engine only
		LSTM(1),                        // Neural nets LSTM engine only
		LEGACY_LSTM(2),                 // Legacy + LSTM engines
		DEFAULT(3);                     // Default, based on what is available
		
		private final int value;
		
		OcrEngineMode(int value) {
			this.value = value;
		}
		
		public int getValue() {
			return value;
		}
	}
	
	private PageSegMode pageSegMode;
	private OcrEngineMode ocrEngineMode;
	private boolean removeBackground;
	private Color textColor;
	private boolean debug;
	private String allowedChars;

	private DTOTesseractSettings(Builder builder) {
		this.pageSegMode = builder.pageSegMode;
		this.ocrEngineMode = builder.ocrEngineMode;
		this.removeBackground = builder.removeBackground;
		this.textColor = builder.textColor;
		this.debug = builder.debug;
		this.allowedChars = builder.allowedChars;
	}

	public Integer getPageSegMode() {
		return pageSegMode != null ? pageSegMode.getValue() : null;
	}

	public Integer getOcrEngineMode() {
		return ocrEngineMode != null ? ocrEngineMode.getValue() : null;
	}

	public boolean isRemoveBackground() {
		return removeBackground;
	}

	public Color getTextColor() {
		return textColor;
	}

	public boolean isDebug() {
		return debug;
	}

	public boolean hasPageSegMode() {
		return pageSegMode != null;
	}

	public boolean hasOcrEngineMode() {
		return ocrEngineMode != null;
	}

	public String getAllowedChars() {
		return allowedChars;
	}

	public boolean hasAllowedChars() {
		return allowedChars != null && !allowedChars.isEmpty();
	}

	@Override
	public String toString() {
		return "DTOTesseractSettings [pageSegMode=" + pageSegMode + ", ocrEngineMode=" + ocrEngineMode
				+ ", removeBackground=" + removeBackground + ", textColor=" + textColor + ", debug=" + debug 
				+ ", allowedChars=" + allowedChars + "]";
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private PageSegMode pageSegMode;
		private OcrEngineMode ocrEngineMode;
		private boolean removeBackground;
		private Color textColor;
		private boolean debug;
		private String allowedChars;

		public Builder setPageSegMode(PageSegMode pageSegMode) {
			this.pageSegMode = pageSegMode;
			return this;
		}

		public Builder setOcrEngineMode(OcrEngineMode ocrEngineMode) {
			this.ocrEngineMode = ocrEngineMode;
			return this;
		}

		public Builder setRemoveBackground(boolean removeBackground) {
			this.removeBackground = removeBackground;
			return this;
		}

		public Builder setTextColor(Color textColor) {
			this.textColor = textColor;
			return this;
		}

		public Builder setDebug(boolean debug) {
			this.debug = debug;
			return this;
		}

		public Builder setAllowedChars(String allowedChars) {
			this.allowedChars = allowedChars;
			return this;
		}

		public DTOTesseractSettings build() {
			return new DTOTesseractSettings(this);
		}
	}
}
