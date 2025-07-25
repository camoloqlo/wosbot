package cl.camodev.utiles;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

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
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import org.slf4j.*;

public class ImageSearchUtil {
	private static final Logger logger = LoggerFactory.getLogger(ImageSearchUtil.class);

	// Cache thread-safe para templates precargados
	private static final ConcurrentHashMap<String, Mat> templateCache = new ConcurrentHashMap<>();

	// Pool de threads personalizado para operaciones de OpenCV
	private static final ForkJoinPool openCVThreadPool = new ForkJoinPool(
		Math.min(Runtime.getRuntime().availableProcessors(), 4)
	);

	// Cache para byte arrays de templates
	private static final ConcurrentHashMap<String, byte[]> templateBytesCache = new ConcurrentHashMap<>();

	// Estado de inicialización del cache
	private static volatile boolean cacheInitialized = false;

	static {
		// Inicialización automática del cache en background
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			openCVThreadPool.shutdown();
			// Limpiar cache y liberar memoria de OpenCV
			templateCache.values().forEach(Mat::release);
			templateCache.clear();
			templateBytesCache.clear();
		}));

		// Precargar todos los templates del enum en background
		initializeTemplateCache();
	}

	/**
	 * Inicializa el cache de templates cargando todos los templates del enum EnumTemplates
	 */
	private static void initializeTemplateCache() {
		if (cacheInitialized) return;

		openCVThreadPool.submit(() -> {
			try {
				logger.info("Caching templates...");

				// Precargar todos los templates del enum
				for (EnumTemplates enumTemplate : EnumTemplates.values()) {
					String templatePath = enumTemplate.getTemplate();
					try {
						loadTemplateOptimized(templatePath);
						logger.debug("Template {} cached successfully", templatePath);
					} catch (Exception e) {
						logger.warn("Error precargando template {}: {}", templatePath, e.getMessage());
					}
				}

				cacheInitialized = true;
				logger.info("Template cache initialized with {} templates", templateCache.size());

			} catch (Exception e) {
				logger.error("Error initializing template cache: {}", e.getMessage());
			}
		});
	}

	/**
	 * Realiza la búsqueda de un template (plantilla) dentro de una imagen principal.
	 * <p>
	 * La imagen principal se carga desde una ruta externa, mientras que el template se obtiene de los recursos del jar. Se define una región de
	 * interés (ROI) en la imagen principal para limitar la búsqueda. La coincidencia se realiza utilizando el método TM_CCOEFF_NORMED de
	 * OpenCV. El porcentaje de coincidencia se obtiene multiplicando el valor máximo de la coincidencia por 100, y se compara con el umbral
	 * proporcionado.
	 * </p>
	 *
	 * @param templateResourcePath Ruta del template dentro de los recursos del jar.
	 * @param topLeftCorner        Punto de la esquina superior izquierda del ROI.
	 * @param bottomRightCorner    Punto de la esquina inferior derecha del ROI.
	 * @param thresholdPercentage  Umbral de coincidencia en porcentaje (0 a 100). Si el porcentaje de coincidencia es menor que este valor, se
	 *                             considerará que no hay coincidencia suficiente.
	 * @return Un objeto {@link DTOImageSearchResult} que contiene:
	 *         <ul>
	 *         <li>El estado de la búsqueda (true si se encontró una coincidencia adecuada, false en caso contrario).</li>
	 *         <li>La posición de la coincidencia (como {@link DTOPoint}) en la imagen principal, ajustada al sistema de coordenadas de la
	 *         misma.</li>
	 *         <li>El porcentaje de coincidencia obtenido.</li>
	 *         </ul>
	 */

	public static DTOImageSearchResult buscarTemplate(byte[] image, String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage) {
		// Delegar al método optimizado manteniendo la misma firma
		return buscarTemplateOptimized(image, templateResourcePath, topLeftCorner, bottomRightCorner, thresholdPercentage);
	}

	/**
	 * Performs the search for multiple matches of a template within a main image.
	 * <p>
	 * The main image is loaded from an external path, while the template is obtained from the jar resources. A region of
	 * interest (ROI) is defined in the main image to limit the search. Matching is performed using OpenCV's TM_CCOEFF_NORMED method.
	 * All matches that exceed the specified threshold are searched for.
	 * </p>
	 *
	 * @param image                Byte array of the main image.
	 * @param templateResourcePath Path of the template within the jar resources.
	 * @param topLeftCorner        Point of the upper left corner of the ROI.
	 * @param bottomRightCorner    Point of the lower right corner of the ROI.
	 * @param thresholdPercentage  Match threshold as a percentage (0 to 100). Only matches that exceed this value will be included.
	 * @param maxResults           Maximum number of results to return. If 0 or negative, returns all results.
	 * @return A list of {@link DTOImageSearchResult} objects containing all found matches that exceed the threshold.
	 */
	public static List<DTOImageSearchResult> searchTemplateMultiple(byte[] image, String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage, int maxResults) {
		// Delegar al método optimizado manteniendo la misma firma
		return searchTemplateMultipleOptimized(image, templateResourcePath, topLeftCorner, bottomRightCorner, thresholdPercentage, maxResults);
	}

	/**
	 * Método optimizado para carga y cache de templates
	 */
	private static Mat loadTemplateOptimized(String templateResourcePath) {
		// Intentar obtener del cache primero
		Mat cachedTemplate = templateCache.get(templateResourcePath);
		if (cachedTemplate != null && !cachedTemplate.empty()) {
			return cachedTemplate.clone(); // Retornar copia para thread safety
		}

		try {
			// Cargar bytes del cache o del recurso
			byte[] templateBytes = templateBytesCache.computeIfAbsent(templateResourcePath, path -> {
				try (InputStream is = ImageSearchUtil.class.getResourceAsStream(path)) {
					if (is == null) {
						logger.error("Template resource not found: {}", path);
						return null;
					}
					return is.readAllBytes();
				} catch (IOException e) {
					logger.error("Error loading template bytes for: {}", path, e);
					return null;
				}
			});

			if (templateBytes == null) {
				return new Mat(); // Mat vacío
			}

			// Decodificar template
			MatOfByte templateMatOfByte = new MatOfByte(templateBytes);
			Mat template = Imgcodecs.imdecode(templateMatOfByte, Imgcodecs.IMREAD_COLOR);

			if (!template.empty()) {
				// Guardar en cache (clone para evitar modificaciones)
				templateCache.put(templateResourcePath, template.clone());
			}

			return template;

		} catch (Exception e) {
			logger.error("Exception loading template: {}", templateResourcePath, e);
			return new Mat();
		}
	}

	/**
	 * Versión optimizada del método buscarTemplate con cache y mejor gestión de memoria
	 */
	public static DTOImageSearchResult buscarTemplateOptimized(byte[] image, String templateResourcePath,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage) {

		Mat imagenPrincipal = null;
		Mat template = null;
		Mat imagenROI = null;
		Mat resultado = null;

		try {
			// Validación rápida de ROI
			int roiX = topLeftCorner.getX();
			int roiY = topLeftCorner.getY();
			int roiWidth = bottomRightCorner.getX() - topLeftCorner.getX();
			int roiHeight = bottomRightCorner.getY() - topLeftCorner.getY();

			if (roiWidth <= 0 || roiHeight <= 0) {
				logger.error("Invalid ROI dimensions");
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Decodificación de imagen principal (reutilizable)
			MatOfByte matOfByte = new MatOfByte(image);
			imagenPrincipal = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);

			if (imagenPrincipal.empty()) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Cargar template optimizado con cache
			template = loadTemplateOptimized(templateResourcePath);
			if (template.empty()) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Validación de ROI vs imagen
			if (roiX + roiWidth > imagenPrincipal.cols() || roiY + roiHeight > imagenPrincipal.rows()) {
				logger.error("ROI exceeds image dimensions");
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Crear ROI
			Rect roi = new Rect(roiX, roiY, roiWidth, roiHeight);
			imagenROI = new Mat(imagenPrincipal, roi);

			// Verificación de tamaño optimizada
			int resultCols = imagenROI.cols() - template.cols() + 1;
			int resultRows = imagenROI.rows() - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				return new DTOImageSearchResult(false, null, 0.0);
			}

			// Template matching
			resultado = new Mat(resultRows, resultCols, CvType.CV_32FC1);
			Imgproc.matchTemplate(imagenROI, template, resultado, Imgproc.TM_CCOEFF_NORMED);

			// Búsqueda del mejor match
			Core.MinMaxLocResult mmr = Core.minMaxLoc(resultado);
			double matchPercentage = mmr.maxVal * 100.0;

			if (matchPercentage < thresholdPercentage) {
				logger.warn("Template {} match percentage {} below threshold {}", templateResourcePath, matchPercentage, thresholdPercentage);
				return new DTOImageSearchResult(false, null, matchPercentage);
			}

			// Calcular coordenadas del centro
			Point matchLoc = mmr.maxLoc;
			double centerX = matchLoc.x + roi.x + (template.cols() / 2.0);
			double centerY = matchLoc.y + roi.y + (template.rows() / 2.0);

			return new DTOImageSearchResult(true, new DTOPoint((int) centerX, (int) centerY), matchPercentage);

		} catch (Exception e) {
			logger.error("Exception during optimized template search", e);
			return new DTOImageSearchResult(false, null, 0.0);
		} finally {
			// Liberación explícita de memoria OpenCV
			if (imagenPrincipal != null) imagenPrincipal.release();
			if (template != null) template.release();
			if (imagenROI != null) imagenROI.release();
			if (resultado != null) resultado.release();
		}
	}

	/**
	 * Versión optimizada para búsqueda múltiple con paralelización
	 */
	public static CompletableFuture<List<DTOImageSearchResult>> searchTemplateMultipleAsync(
			byte[] image, String templateResourcePath, DTOPoint topLeftCorner,
			DTOPoint bottomRightCorner, double thresholdPercentage, int maxResults) {

		return CompletableFuture.supplyAsync(() -> {
			return searchTemplateMultipleOptimized(image, templateResourcePath, topLeftCorner,
				bottomRightCorner, thresholdPercentage, maxResults);
		}, openCVThreadPool);
	}

	/**
	 * Versión optimizada de búsqueda múltiple con mejor gestión de memoria
	 */
	public static List<DTOImageSearchResult> searchTemplateMultipleOptimized(byte[] image,
			String templateResourcePath, DTOPoint topLeftCorner, DTOPoint bottomRightCorner,
			double thresholdPercentage, int maxResults) {

		List<DTOImageSearchResult> results = new ArrayList<>();
		Mat mainImage = null;
		Mat template = null;
		Mat imageROI = null;
		Mat matchResult = null;
		Mat resultCopy = null;

		try {
			// Validación rápida de ROI
			int roiX = topLeftCorner.getX();
			int roiY = topLeftCorner.getY();
			int roiWidth = bottomRightCorner.getX() - topLeftCorner.getX();
			int roiHeight = bottomRightCorner.getY() - topLeftCorner.getY();

			if (roiWidth <= 0 || roiHeight <= 0) {
				return results;
			}

			// Decodificación optimizada
			MatOfByte matOfByte = new MatOfByte(image);
			mainImage = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);

			if (mainImage.empty()) {
				return results;
			}

			// Cargar template con cache
			template = loadTemplateOptimized(templateResourcePath);
			if (template.empty()) {
				return results;
			}

			// Validaciones
			if (roiX + roiWidth > mainImage.cols() || roiY + roiHeight > mainImage.rows()) {
				return results;
			}

			// Crear ROI
			Rect roi = new Rect(roiX, roiY, roiWidth, roiHeight);
			imageROI = new Mat(mainImage, roi);

			int resultCols = imageROI.cols() - template.cols() + 1;
			int resultRows = imageROI.rows() - template.rows() + 1;
			if (resultCols <= 0 || resultRows <= 0) {
				return results;
			}

			// Template matching
			matchResult = new Mat(resultRows, resultCols, CvType.CV_32FC1);
			Imgproc.matchTemplate(imageROI, template, matchResult, Imgproc.TM_CCOEFF_NORMED);

			// Búsqueda optimizada de múltiples matches
			double thresholdDecimal = thresholdPercentage / 100.0;
			resultCopy = matchResult.clone();
			int templateWidth = template.cols();
			int templateHeight = template.rows();

			// Pre-calcular para optimización
			int halfTemplateWidth = templateWidth / 2;
			int halfTemplateHeight = templateHeight / 2;

			while (results.size() < maxResults || maxResults <= 0) {
				Core.MinMaxLocResult mmr = Core.minMaxLoc(resultCopy);
				double matchValue = mmr.maxVal;

				if (matchValue < thresholdDecimal) {
					break;
				}

				Point matchLoc = mmr.maxLoc;
				double centerX = matchLoc.x + roi.x + halfTemplateWidth;
				double centerY = matchLoc.y + roi.y + halfTemplateHeight;

				results.add(new DTOImageSearchResult(true,
					new DTOPoint((int) centerX, (int) centerY), matchValue * 100.0));

				// Supresión optimizada
				int suppressX = Math.max(0, (int)matchLoc.x - halfTemplateWidth);
				int suppressY = Math.max(0, (int)matchLoc.y - halfTemplateHeight);
				int suppressWidth = Math.min(templateWidth, resultCopy.cols() - suppressX);
				int suppressHeight = Math.min(templateHeight, resultCopy.rows() - suppressY);

				if (suppressWidth > 0 && suppressHeight > 0) {
					Rect suppressRect = new Rect(suppressX, suppressY, suppressWidth, suppressHeight);
					Mat suppressArea = new Mat(resultCopy, suppressRect);
					suppressArea.setTo(new org.opencv.core.Scalar(0));
					suppressArea.release();
				}
			}

		} catch (Exception e) {
			logger.error("Exception during optimized multiple template search", e);
		} finally {
			// Liberación explícita de memoria
			if (mainImage != null) mainImage.release();
			if (template != null) template.release();
			if (imageROI != null) imageROI.release();
			if (matchResult != null) matchResult.release();
			if (resultCopy != null) resultCopy.release();
		}

		return results;
	}

	/**
	 * Método para precarga de templates comunes
	 */
	public static void preloadTemplate(String templateResourcePath) {
		openCVThreadPool.submit(() -> loadTemplateOptimized(templateResourcePath));
	}

	/**
	 * Método para limpiar cache manualmente
	 */
	public static void clearCache() {
		templateCache.values().forEach(Mat::release);
		templateCache.clear();
		templateBytesCache.clear();
		cacheInitialized = false;
	}

	/**
	 * Buscar template usando directamente el enum EnumTemplates
	 */
	public static DTOImageSearchResult buscarTemplate(byte[] image, EnumTemplates enumTemplate,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage) {
		return buscarTemplate(image, enumTemplate.getTemplate(), topLeftCorner, bottomRightCorner, thresholdPercentage);
	}

	/**
	 * Buscar múltiples templates usando directamente el enum EnumTemplates
	 */
	public static List<DTOImageSearchResult> searchTemplateMultiple(byte[] image, EnumTemplates enumTemplate,
			DTOPoint topLeftCorner, DTOPoint bottomRightCorner, double thresholdPercentage, int maxResults) {
		return searchTemplateMultiple(image, enumTemplate.getTemplate(), topLeftCorner, bottomRightCorner, thresholdPercentage, maxResults);
	}

	/**
	 * Versión asíncrona usando enum
	 */
	public static CompletableFuture<List<DTOImageSearchResult>> searchTemplateMultipleAsync(
			byte[] image, EnumTemplates enumTemplate, DTOPoint topLeftCorner,
			DTOPoint bottomRightCorner, double thresholdPercentage, int maxResults) {
		return searchTemplateMultipleAsync(image, enumTemplate.getTemplate(), topLeftCorner, bottomRightCorner, thresholdPercentage, maxResults);
	}

	/**
	 * Verifica si el cache está completamente inicializado
	 */
	public static boolean isCacheInitialized() {
		return cacheInitialized;
	}

	/**
	 * Obtiene estadísticas del cache
	 */
	public static String getCacheStats() {
		return String.format("Templates en cache: %d/%d, Bytes cache: %d",
			templateCache.size(), EnumTemplates.values().length, templateBytesCache.size());
	}

	public static void loadNativeLibrary(String resourcePath) throws IOException {
		// Obtener el nombre del archivo a partir de la ruta del recurso
		String[] parts = resourcePath.split("/");
		String libFileName = parts[parts.length - 1];

		// Crear el directorio lib/opencv si no existe
		File libDir = new File("lib/opencv");
		if (!libDir.exists()) {
			libDir.mkdirs();
		}

		// Crear el archivo destino en lib/opencv
		File destLib = new File(libDir, libFileName);

		// Abrir el recurso como stream
		try (InputStream in = ImageSearchUtil.class.getResourceAsStream(resourcePath); OutputStream out = new FileOutputStream(destLib)) {
			if (in == null) {
				logger.error("Resource not found: {}", resourcePath);
				throw new IOException("Resource not found: " + resourcePath);
			}
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}
		} catch (IOException e) {
			logger.error("Error extracting native library: {}", e.getMessage());
			throw e;
		}

		// Cargar la librería usando la ruta absoluta del archivo destino
		System.load(destLib.getAbsolutePath());
		logger.info("Native library loaded from: {}", destLib.getAbsolutePath());
	}
}
