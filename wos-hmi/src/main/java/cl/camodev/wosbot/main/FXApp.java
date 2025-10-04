package cl.camodev.wosbot.main;

import cl.camodev.wosbot.launcher.view.ILauncherConstants;
import cl.camodev.wosbot.launcher.view.LauncherLayoutController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.util.Objects;
import java.util.prefs.Preferences;

public class FXApp extends Application {
	private static final Logger logger = LoggerFactory.getLogger(FXApp.class);
	private static final String KEY_X      = "windowX";
	private static final String KEY_Y      = "windowY";
	private static final String KEY_W      = "windowWidth";
	private static final String KEY_H      = "windowHeight";
	private static final double DEFAULT_W  = 900;
	private static final double DEFAULT_H  = 500;

	private Preferences prefs;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) throws IOException {
		// Inicializar Preferences
		Image appIcon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/appIcon.png")));
		prefs = Preferences.userRoot().node(FXApp.class.getName());

		// Cargar FXML y controlador
		FXMLLoader fxmlLoader = new FXMLLoader(
				LauncherLayoutController.class.getResource("LauncherLayout.fxml")
		);
		LauncherLayoutController controller = new LauncherLayoutController(stage);
		fxmlLoader.setController(controller);
		Parent root = fxmlLoader.load();

		// Crear escena con tamaño por defecto primero
		Scene scene = new Scene(root, DEFAULT_W, DEFAULT_H);
		scene.getStylesheets().add(ILauncherConstants.getCssPath());
		stage.setScene(scene);
		stage.getIcons().add(appIcon);
		stage.setTitle("Launcher");

        // set minimum height and width so the "run" button is always visible
        stage.setMinHeight(750);
        stage.setMinWidth(1050);
		// Mostrar la ventana primero para que JavaFX calcule los tamaños correctamente
		stage.show();

		// Ahora restaurar el tamaño y posición guardados
		double savedWidth = prefs.getDouble(KEY_W, DEFAULT_W);
		double savedHeight = prefs.getDouble(KEY_H, DEFAULT_H);
		double savedX = prefs.getDouble(KEY_X, Double.NaN);
		double savedY = prefs.getDouble(KEY_Y, Double.NaN);

		// Establecer tamaño
		stage.setWidth(savedWidth);
		stage.setHeight(savedHeight);

		// Restaurar posición si existe y es válida
		if (!Double.isNaN(savedX) && !Double.isNaN(savedY)) {
			if (isPositionValidOnAnyScreen(savedX, savedY, savedWidth, savedHeight)) {
				stage.setX(savedX);
				stage.setY(savedY);
			} else {
				// La posición guardada no es válida (monitor desconectado), usar monitor principal
				positionOnPrimaryScreen(stage, savedWidth, savedHeight);
			}
		}

		// Antes de cerrar, guardar posición y tamaño
		stage.setOnCloseRequest(event -> {
			prefs.putDouble(KEY_X, stage.getX());
			prefs.putDouble(KEY_Y, stage.getY());
			prefs.putDouble(KEY_W, stage.getWidth());
			prefs.putDouble(KEY_H, stage.getHeight());

			// Kill adb.exe process
			try {
				new ProcessBuilder("taskkill", "/F", "/IM", "adb.exe").start();
				logger.info("adb.exe process terminated.");
			} catch (IOException e) {
				logger.error("Failed to terminate adb.exe process: " + e.getMessage(), e);
			}

			System.exit(0);
		});
	}

	/**
	 * Verifica si la posición guardada es válida en alguno de los monitores disponibles.
	 */
	private boolean isPositionValidOnAnyScreen(double x, double y, double width, double height) {
		for (Screen screen : Screen.getScreens()) {
			Rectangle2D bounds = screen.getVisualBounds();

			// Verificar si al menos una parte significativa de la ventana está visible en este monitor
			// La ventana debe tener al menos 100x100 píxeles visibles en el monitor
			double visibleLeft = Math.max(x, bounds.getMinX());
			double visibleTop = Math.max(y, bounds.getMinY());
			double visibleRight = Math.min(x + width, bounds.getMaxX());
			double visibleBottom = Math.min(y + height, bounds.getMaxY());

			double visibleWidth = Math.max(0, visibleRight - visibleLeft);
			double visibleHeight = Math.max(0, visibleBottom - visibleTop);

			// Si hay al menos 100x100 píxeles visibles, consideramos la posición válida
			if (visibleWidth >= 100 && visibleHeight >= 100) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Posiciona la ventana en el monitor principal manteniendo el tamaño guardado.
	 */
	private void positionOnPrimaryScreen(Stage stage, double savedWidth, double savedHeight) {
		Screen primaryScreen = Screen.getPrimary();
		Rectangle2D bounds = primaryScreen.getVisualBounds();

		// Centrar la ventana en el monitor principal
		double centerX = bounds.getMinX() + (bounds.getWidth() - savedWidth) / 2;
		double centerY = bounds.getMinY() + (bounds.getHeight() - savedHeight) / 2;

		// Asegurar que la ventana no se salga de los límites del monitor
		double finalX = Math.max(bounds.getMinX(), Math.min(centerX, bounds.getMaxX() - savedWidth));
		double finalY = Math.max(bounds.getMinY(), Math.min(centerY, bounds.getMaxY() - savedHeight));

		stage.setX(finalX);
		stage.setY(finalY);

		logger.info("Window positioned on primary screen due to invalid saved position");
	}
}
