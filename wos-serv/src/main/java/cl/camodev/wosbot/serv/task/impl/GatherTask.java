package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import cl.camodev.wosbot.almac.entity.DailyTask;
import cl.camodev.wosbot.almac.repo.DailyTaskRepository;
import cl.camodev.wosbot.almac.repo.IDailyTaskRepository;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
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

public class GatherTask extends DelayedTask {
    private final GatherType gatherType;
    private final IDailyTaskRepository iDailyTaskRepository = DailyTaskRepository.getRepository();

    //@formatter:on
    private DTOPoint[][] queues = {
            {new DTOPoint(10, 342), new DTOPoint(435, 407), new DTOPoint(152, 378)},
            {new DTOPoint(10, 415), new DTOPoint(435, 480), new DTOPoint(152, 451)},
            {new DTOPoint(10, 488), new DTOPoint(435, 553), new DTOPoint(152, 524)},
            {new DTOPoint(10, 561), new DTOPoint(435, 626), new DTOPoint(152, 597)},
            {new DTOPoint(10, 634), new DTOPoint(435, 699), new DTOPoint(152, 670)},
            {new DTOPoint(10, 707), new DTOPoint(435, 772), new DTOPoint(152, 743)},
    };

    public GatherTask(DTOProfiles profile, TpDailyTaskEnum tpTask, GatherType gatherType) {
        super(profile, tpTask);
        this.gatherType = gatherType;
    }

    public static LocalDateTime parseRemaining(String timeStr) {
        if (timeStr == null) {
            return LocalDateTime.now();
        }
        // Elimina saltos de línea y espacios extra
        timeStr = timeStr.replaceAll("\\r?\\n", "").trim();

        // Se espera el formato hh:mm:ss
        String[] parts = timeStr.split(":");
        if (parts.length != 3) {
            return LocalDateTime.now();
        }

        try {
            int hours = Integer.parseInt(parts[0].trim());
            int minutes = Integer.parseInt(parts[1].trim());
            int seconds = Integer.parseInt(parts[2].trim());

            LocalDateTime now = LocalDateTime.now();
            // Se suma el tiempo parseado al instante actual
            return now.plusHours(hours).plusMinutes(minutes).plusSeconds(seconds);
        } catch (NumberFormatException e) {
            // Si ocurre algún error durante el parseo, se retorna LocalDateTime.now()
            return LocalDateTime.now().plusMinutes(3);
        }
    }

    @Override
    protected void execute() {
        // Check if IntelligenceTask is not processed yet or reschedule time is lower
        // than 60 minutes
        long intelRemainingMinutes = isIntelligenceTaskReadyForGathering();
        if (profile.getConfig(EnumConfigurationKey.INTEL_BOOL, Boolean.class) && intelRemainingMinutes > 0) {
            logInfo("Waiting for IntelligenceTask to be processed or reschedule time to exceed intel remaining minutes");
            reschedule(LocalDateTime.now().plusMinutes(intelRemainingMinutes)); // Check again in intelRemaining minutes
            return;
        }

        // Check if GatherSpeedTask is not processed yet
        if (profile.getConfig(EnumConfigurationKey.GATHER_SPEED_BOOL, Boolean.class)
                && !isGatherSpeedTaskReadyForGathering()) {
            logInfo("Waiting for GatherSpeedTask to be processed or reschedule time to exceed 5 minutes");
            reschedule(LocalDateTime.now().plusMinutes(2)); // Check again in 2 minutes
            return;
        }

        DTOImageSearchResult homeResult = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.GAME_HOME_FURNACE.getTemplate(), 90);
        DTOImageSearchResult worldResult = emuManager.searchTemplate(EMULATOR_NUMBER,
                EnumTemplates.GAME_HOME_WORLD.getTemplate(), 90);

        if (homeResult.isFound() || worldResult.isFound()) {
            if (worldResult.isFound()) {
                emuManager.tapAtPoint(EMULATOR_NUMBER, worldResult.getPoint());
                sleepTask(4000);
            }

            // verificar marchas activas
            emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(2, 550));
            sleepTask(500);
            emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(340, 265));
            sleepTask(500);
            logInfo("looking for " + gatherType);

            // Get active march queue setting and calculate search region
            int activeMarchQueues = profile.getConfig(EnumConfigurationKey.GATHER_ACTIVE_MARCH_QUEUE_INT,
                    Integer.class);
            int maxY = queues[Math.min(activeMarchQueues - 1, queues.length - 1)][1].getY(); // Use the Y coordinate of
            // the last active queue

            DTOImageSearchResult resource = emuManager.searchTemplate(EMULATOR_NUMBER, gatherType.getTemplate(), new DTOPoint(10,
                    342), new DTOPoint(415, maxY), 90);

            if (resource.isFound()) {
                logInfo("Resource found, getting remaining time");
                int index = obtenerIndice(resource.getPoint());
                if (index != -1) {
                    try {
                        String time = emuManager.ocrRegionText(EMULATOR_NUMBER, queues[index][2],
                                new DTOPoint(queues[index][2].getX() + 140, queues[index][2].getY() + 19));
                        LocalDateTime nextSchedule = parseRemaining(time).plusMinutes(2);
                        this.reschedule(nextSchedule);
                    } catch (Exception e) {

                    }
                }
                emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(110, 270));
                emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(464, 551));
            } else {
                emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(110, 270));
                emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(464, 551));
                homeResult = emuManager.searchTemplate(EMULATOR_NUMBER, EnumTemplates.GAME_HOME_FURNACE.getTemplate(), 90);
                if (homeResult.isFound()) {
                    emuManager.tapAtPoint(EMULATOR_NUMBER, homeResult.getPoint());
                    sleepTask(3000);
                }
                // debo mandar un escuadro a recojer recursos
                // ir a la lupa
                emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(25, 850), new DTOPoint(67, 898));
                sleepTask(2000);

                // hacer swipe a la izquierda
                emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(678, 913), new DTOPoint(40, 913));
                sleepTask(300);

                // verify if
                DTOImageSearchResult tile = null;
                int attempts = 0;
                while (attempts < 4) {
                    tile = emuManager.searchTemplate(EMULATOR_NUMBER, gatherType.getTile(), 90);
                    if (tile.isFound()) {
                        break;
                    }
                    attempts++;
                    emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(678, 913), new DTOPoint(40, 913));
                    sleepTask(500);
                }
                if (tile.isFound()) {
                    emuManager.tapAtPoint(EMULATOR_NUMBER, tile.getPoint());
                    // regresar al nivel 1
                    sleepTask(500);
                    emuManager.executeSwipe(EMULATOR_NUMBER, new DTOPoint(435, 1052), new DTOPoint(40, 1052));
                    sleepTask(300);
                    emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(487, 1055), new DTOPoint(487, 1055),
                            (profile.getConfig(gatherType.getConfig(), Integer.class) - 1), 100);

                    DTOImageSearchResult tick = emuManager.searchTemplate(EMULATOR_NUMBER,
                            EnumTemplates.GAME_HOME_SHORTCUTS_FARM_TICK.getTemplate(), 90);
                    if (!tick.isFound()) {
                        emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(183, 1140));

                    }

                    // click Search
                    emuManager.tapAtRandomPoint(EMULATOR_NUMBER, new DTOPoint(301, 1200), new DTOPoint(412, 1229));
                    sleepTask(3000);
                    // click gather

                    DTOImageSearchResult gather = emuManager.searchTemplate(EMULATOR_NUMBER,
                            EnumTemplates.GAME_HOME_SHORTCUTS_FARM_GATHER.getTemplate(), 90);
                    if (gather.isFound()) {
                        emuManager.tapAtPoint(EMULATOR_NUMBER, gather.getPoint());
                        sleepTask(500);

                        // verificar el heroe y remover restantes
                        logInfo("Removing unused heroes");
                        emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(171, 430));
                        sleepTask(200);
                        // remover restantes
                        emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(560, 348));

                        sleepTask(200);
                        DTOImageSearchResult remove = emuManager.searchTemplate(EMULATOR_NUMBER,
                                EnumTemplates.RALLY_REMOVE_HERO_BUTTON.getTemplate(), 90);
                        if (remove.isFound()) {
                            emuManager.tapAtPoint(EMULATOR_NUMBER, remove.getPoint());
                            sleepTask(200);
                        }

                        emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(362, 348));
                        sleepTask(200);
                        remove = emuManager.searchTemplate(EMULATOR_NUMBER,
                                EnumTemplates.RALLY_REMOVE_HERO_BUTTON.getTemplate(), 90);
                        if (remove.isFound()) {
                            emuManager.tapAtPoint(EMULATOR_NUMBER, remove.getPoint());
                            sleepTask(200);
                        }
                        emuManager.tapBackButton(EMULATOR_NUMBER);
                        // falta fverificar si esta el heroe adecuado
                        sleepTask(200);

                        // Click equalize

                        DTOImageSearchResult equalizeButton = emuManager.searchTemplate(EMULATOR_NUMBER,
                                EnumTemplates.RALLY_EQUALIZE_BUTTON.getTemplate(), 90);

                        if (equalizeButton.isFound()) {
                            emuManager.tapAtPoint(EMULATOR_NUMBER, equalizeButton.getPoint());
                        } else {
                            //hard coded coords
                            emuManager.tapAtPoint(EMULATOR_NUMBER, new DTOPoint(198, 1188));
                            sleepTask(500);
                        }


                        // click gather
                        DTOImageSearchResult gatherButton = emuManager.searchTemplate(EMULATOR_NUMBER,
                                EnumTemplates.RALLY_GATHER_BUTTON.getTemplate(), 90);
                        if (gatherButton.isFound()) {
                            emuManager.tapAtPoint(EMULATOR_NUMBER, gatherButton.getPoint());
                            sleepTask(200);
                            // verificar si ya hay un marcha en curso
                            DTOImageSearchResult march = emuManager.searchTemplate(EMULATOR_NUMBER,
                                    EnumTemplates.RALLY_GATHER_ALREADY_MARCHING.getTemplate(), 90);
                            if (march.isFound()) {
                                emuManager.tapBackButton(EMULATOR_NUMBER);
                                emuManager.tapBackButton(EMULATOR_NUMBER);
                                reschedule(LocalDateTime.now());
                                logInfo("Tile is already being gathered, rescheduling task");
                            } else {
                                logInfo("March started");
                                reschedule(LocalDateTime.now().plusMinutes(5));
                            }
                        }

                    } else {
                        emuManager.tapBackButton(EMULATOR_NUMBER);
                        reschedule(LocalDateTime.now().plusMinutes(5));
                        logWarning("Tile not found");
                    }
                } else {
                    emuManager.tapBackButton(EMULATOR_NUMBER);
                    reschedule(LocalDateTime.now().plusMinutes(5));
                    logError("resource not found");
                }
            }

        } else {
            emuManager.tapBackButton(EMULATOR_NUMBER);
            reschedule(LocalDateTime.now());

        }

    }

    public int obtenerIndice(DTOPoint punto) {
        // Get active march queue setting and limit the search to only active queues
        int activeMarchQueues = profile.getConfig(EnumConfigurationKey.GATHER_ACTIVE_MARCH_QUEUE_INT, Integer.class);
        int maxQueues = Math.min(activeMarchQueues, queues.length);

        for (int i = 0; i < maxQueues; i++) {
            // Obtener los límites del rango (para asegurar el orden correcto, usamos
            // Math.min y Math.max)
            int minX = Math.min(queues[i][0].getX(), queues[i][1].getX());
            int maxX = Math.max(queues[i][0].getX(), queues[i][1].getX());
            int minY = Math.min(queues[i][0].getY(), queues[i][1].getY());
            int maxY = Math.max(queues[i][0].getY(), queues[i][1].getY());

            // Verificar si el punto está dentro de los límites
            if (punto.getX() >= minX && punto.getX() <= maxX && punto.getY() >= minY && punto.getY() <= maxY) {
                return i; // Retorna el índice del par encontrado
            }
        }
        return -1; // Retorna -1 si el punto no se encuentra en ninguno de los rangos
    }

    private long isIntelligenceTaskReadyForGathering() {
        try {
            DailyTask intelligenceTask = iDailyTaskRepository.findByProfileIdAndTaskName(profile.getId(),
                    TpDailyTaskEnum.INTEL);

            if (intelligenceTask == null) {
                // IntelligenceTask has never been executed, so gathering should wait
                logDebug("IntelligenceTask has never been executed, should wait");
                return (long) 2; // Wait for 2 minutes
            }

            LocalDateTime nextSchedule = intelligenceTask.getNextSchedule();
            if (nextSchedule == null) {
                // If there's no next schedule, check again in 2 minutes
                logDebug("IntelligenceTask has no next schedule, should wait");
                return (long) 2; // Wait for 2 minutes
            }

            // Check if the next schedule is more than 60 minutes from now
            long minutesUntilNextSchedule = ChronoUnit.MINUTES.between(LocalDateTime.now(), nextSchedule);

            if (minutesUntilNextSchedule <= 0) {
                // If the next schedule is in the past, check again in 2 minutes
                logDebug("IntelligenceTask next schedule is in the past and will start soon, should wait");
                return (long) 2; // Wait for 2 minutes
            }
            if (minutesUntilNextSchedule > 300) {
                logDebug("IntelligenceTask next schedule is in " + minutesUntilNextSchedule
                        + " minutes, gathering can start");
                return (long) 0; // Allow gathering to proceed
            } else {
                logDebug("IntelligenceTask next schedule is in " + minutesUntilNextSchedule + " minutes, should wait");
                return minutesUntilNextSchedule; // Wait for the remaining minutes
            }

        } catch (Exception e) {
            logDebug("Error checking IntelligenceTask status: " + e.getMessage() + ", should wait");
            return (long) 2; // Wait for 2 minutes
        }
    }

    private boolean isGatherSpeedTaskReadyForGathering() {
        try {
            DailyTask gatherSpeedTask = iDailyTaskRepository.findByProfileIdAndTaskName(profile.getId(),
                    TpDailyTaskEnum.GATHER_BOOST);

            if (gatherSpeedTask == null) {
                // GatherSpeedTask has never been executed, so gathering should wait
                logDebug("GatherSpeedTask has never been executed, should wait");
                return false;
            }

            LocalDateTime nextSchedule = gatherSpeedTask.getNextSchedule();
            if (nextSchedule == null) {
                // If there's no next schedule, check again in 5 minutes
                logDebug("GatherSpeedTask has no next schedule, should wait");
                return false;
            }

            // Check if the next schedule is more than 10 minutes from now
            long minutesUntilNextSchedule = ChronoUnit.MINUTES.between(LocalDateTime.now(), nextSchedule);

            // FIX: Sometimes for whatever reason, ServScheduler doesn't update the next
            // schedule correctly
            // Due to this error, minutesUntilNextSchedule can be less than 0 even though
            // task already run
            // We will skip if its less than 0 to make sure gathering can start
            if (minutesUntilNextSchedule > 0 && minutesUntilNextSchedule < 5) {
                logDebug("GatherSpeedTask next schedule is in " + minutesUntilNextSchedule + " minutes, should wait");
                return false;
            } else {
                logDebug("GatherSpeedTask next schedule is in " + minutesUntilNextSchedule
                        + " minutes, gathering can start");
                return true;
            }

        } catch (Exception e) {
            logError("Error checking GatherSpeedTask status: " + e.getMessage() + ", should wait");
            return false; // Wait for 2 minutes
        }
    }

    @Override
    protected Object getDistinctKey() {
        return gatherType;
    }

    @Override
    public boolean provideDailyMissionProgress() {
        return true;
    }

    //@formatter:off
	public enum GatherType {
		MEAT( EnumTemplates.GAME_HOME_SHORTCUTS_MEAT, EnumTemplates.GAME_HOME_SHORTCUTS_FARM_MEAT, EnumConfigurationKey.GATHER_MEAT_LEVEL_INT),
		WOOD( EnumTemplates.GAME_HOME_SHORTCUTS_WOOD, EnumTemplates.GAME_HOME_SHORTCUTS_FARM_WOOD, EnumConfigurationKey.GATHER_WOOD_LEVEL_INT),
		COAL( EnumTemplates.GAME_HOME_SHORTCUTS_COAL, EnumTemplates.GAME_HOME_SHORTCUTS_FARM_COAL, EnumConfigurationKey.GATHER_COAL_LEVEL_INT),
		IRON( EnumTemplates.GAME_HOME_SHORTCUTS_IRON, EnumTemplates.GAME_HOME_SHORTCUTS_FARM_IRON, EnumConfigurationKey.GATHER_IRON_LEVEL_INT);


		EnumTemplates template;
		EnumTemplates tile;
		EnumConfigurationKey level;

		GatherType(EnumTemplates enumTemplate,EnumTemplates tile, EnumConfigurationKey level) {
		this.template = enumTemplate;
		this.tile = tile;
		this.level = level;
		}

		public String getTemplate() {
            return template.getTemplate();
		}

		public String getTile() {
            return tile.getTemplate();
		}

		public EnumConfigurationKey getConfig() {
            return level;
        }


	}

}
