package cl.camodev.wosbot.serv.task.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.DelayedTask;
import net.sourceforge.tess4j.TesseractException;

public class BeastSlayTask extends DelayedTask {

	private final EmulatorManager emuManager = EmulatorManager.getInstance();
	private final ServLogs servLogs = ServLogs.getServices();

	private int stamina = 0;
	private int availableQueues = 0;
	private int maxQueues = 3;

	public BeastSlayTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
		super(profile, tpTask);
	}

	public static LocalDateTime calculateFullStaminaTime(int currentStamina, int maxStamina, int regenRateMinutes) {
		if (currentStamina >= maxStamina) {
			return LocalDateTime.now(); // Ya está lleno
		}

		int staminaNeeded = maxStamina - currentStamina;
		int minutesToFull = staminaNeeded * regenRateMinutes;

		return LocalDateTime.now().plus(minutesToFull, ChronoUnit.MINUTES);
	}

	@Override
	protected void execute() {

		DTOImageSearchResult homeResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_FURNACE.getTemplate(), 90);
		DTOImageSearchResult worldResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_WORLD.getTemplate(), 90);

		if (homeResult.isFound() || worldResult.isFound()) {
			if (homeResult.isFound()) {
				emuManager.tapAtPoint(EMULATOR_NUMBER, homeResult.getPoint());
				sleepTask(3000);
				servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Going to beast slay");
			}

			// ir al perfil a ver la stamina
			emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(50, 50));
			sleepTask(500);
			// ir al menu stamina
			emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(220, 1100), new DTOPoint(250, 1125));
			sleepTask(2000);
			// hacer ocr a la stamina 350,270 490,300

			try {
				String staminaText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(350, 270), new DTOPoint(490, 300));
				System.out.println(staminaText);
				emuManager.tapBackButton(EMULATOR_NUMBER);
				emuManager.tapBackButton(EMULATOR_NUMBER);

				stamina = extractFirstNumber(staminaText);

				if (stamina < 10) {
					LocalDateTime fullStaminaTime = calculateFullStaminaTime(stamina, 100, 5);
//					servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Stamina is less than 10, rescheduling to " + UtilTime.localDateTimeToDDHHMMSS(fullStaminaTime));
					this.reschedule(fullStaminaTime);
					ServScheduler.getServices().updateDailyTaskStatus(profile, tpTask, fullStaminaTime);
					return;
				}

                        } catch (IOException | TesseractException e) {
                                ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Error checking stamina: " + e.getMessage());
                        }

			// deberoa obtener la cantidad de colas disponibles para atacar bestias

			try {
				// ir al perfil
				emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(50, 50));
				sleepTask(1000);

				// ir al menu de colas
				emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(210, 1190), new DTOPoint(330, 1250));
				sleepTask(1000);

				String queueText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(280, 230), new DTOPoint(340, 252));
				System.out.println(queueText);
				emuManager.tapBackButton(EMULATOR_NUMBER);
				emuManager.tapBackButton(EMULATOR_NUMBER);

				availableQueues = extractFirstNumber(queueText);

				if (stamina < 10) {
//					LocalDateTime fullStaminaTime = calculateFullStaminaTime(stamina, 100, 5);
//					UtilTime.localDateTimeToDDHHMMSS(fullStaminaTime);
//					servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), "Stamina is less than 10, rescheduling to " + UtilTime.localDateTimeToDDHHMMSS(fullStaminaTime));
					return;
				}

                        } catch (IOException | TesseractException e) {
                                ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), "Error checking stamina: " + e.getMessage());
                        }

			// si llego hasta aqui, tengo mas de 10 de stamina y deberia atacar bestias hasta que la stamina sea menor a 10, el consumo es de 8-10 por
			// ataque
			int beastLevel = 30;
			List<Long> activeBeasts = new ArrayList<>(); // Lista de tiempos de finalización de las bestias
			System.out.println("Atacando bestias");

			while (stamina >= 10) {

				// Revisar si alguna bestia ha terminado su tiempo
				long currentTime = System.currentTimeMillis();
				Iterator<Long> iterator = activeBeasts.iterator();
				while (iterator.hasNext()) {
					if (currentTime >= iterator.next()) {
						iterator.remove(); // Eliminar bestia que ya terminó su tiempo
						availableQueues++; // Liberar una queue
						System.out.println("Una bestia ha terminado su tiempo. Queue disponible: " + availableQueues);
					}
				}

				// Solo atacar si hay una queue disponible
				if (availableQueues > 0) {
					for (int i = 0; i < maxQueues; i++) {
						if (availableQueues <= 0)
							break; // Si no hay más queues, salir del loop

						sleepTask(6000);
						// ir a la bestia
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(25, 850), new DTOPoint(67, 898));
						sleepTask(1000);

						emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(20, 910), new DTOPoint(70, 915));
						sleepTask(1000);
						// beast button
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(70, 880), new DTOPoint(120, 930));
						sleepTask(1000);
						// ir a nivel 1
						emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(180, 1050), new DTOPoint(1, 1050));

						// select beast level
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(470, 1040), new DTOPoint(500, 1070), beastLevel - 1, 100);
						sleepTask(1000);
						// click search
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(301, 1200), new DTOPoint(412, 1229));
						sleepTask(6000);

						// click attack
						emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(270, 600), new DTOPoint(460, 630));
						sleepTask(6000);

						try {
							// Obtener stamina y tiempo restante via OCR

							String timeText = emuManager.ocrRegionText(EMULATOR_NUMBER, new DTOPoint(519, 1141), new DTOPoint(618, 1164));
							System.out.println("Time remaining: " + timeText);

							timeText = timeText.trim().replaceAll("[^0-9:]", ""); // Solo dejar números y ":"

							// atacar
							emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(450, 1183), new DTOPoint(640, 1240));

							stamina -= 10;
							availableQueues--;

							int totalSeconds = 0;
							String[] timeParts = timeText.split(":");

							if (timeParts.length == 3) {
								// Formato HH:mm:ss
								totalSeconds = Integer.parseInt(timeParts[0]) * 3600 + Integer.parseInt(timeParts[1]) * 60 + Integer.parseInt(timeParts[2]);
							} else if (timeParts.length == 2) {
								// Formato mm:ss
								totalSeconds = Integer.parseInt(timeParts[0]) * 60 + Integer.parseInt(timeParts[1]);
							} else {
								// Formato inválido o incompleto, asignar un valor por defecto
								totalSeconds = 10; // Por defecto 10 segundos de espera
							}

							// Calcular el tiempo de finalización de la bestia
							long finishTime = System.currentTimeMillis() + ((totalSeconds * 1000L) * 2);
							activeBeasts.add(finishTime);
							System.out.println("Bestia atacada, finalizará en " + (totalSeconds * 2) + " segundos.");

						} catch (Exception e) {
							System.out.println("Error al obtener información de la bestia.");
						}
					}
				} else {
					// Si no hay queues disponibles, esperar un poco antes de volver a verificar
					System.out.println("Esperando a que una bestia termine...");
					sleepTask(5000); // Espera de 5 segundos antes de revisar de nuevo
				}
			}

		}

	}

	public int extractFirstNumber(String ocrText) {
		if (ocrText == null || ocrText.isEmpty()) {
			throw new IllegalArgumentException("El texto OCR no puede ser nulo o vacío.");
		}

		// Normalizar el texto OCR (reemplazo de posibles errores comunes)
		String normalizedText = ocrText.replaceAll("[oO]", "0") // Reemplazar 'o' o 'O' por '0'
				.replaceAll("[^0-9/]", "") // Eliminar caracteres que no sean números o '/'
				.trim(); // Eliminar espacios en los extremos

		// Expresión regular para capturar la parte antes del "/"
		Pattern pattern = Pattern.compile("^(\\d+)/\\d+$");
		Matcher matcher = pattern.matcher(normalizedText);

		if (matcher.find()) {
			return Integer.parseInt(matcher.group(1)); // Extrae la primera parte de la fracción como entero
		} else {
			throw new NumberFormatException("No se encontró un formato válido en el texto OCR: " + normalizedText);
		}
	}

}
