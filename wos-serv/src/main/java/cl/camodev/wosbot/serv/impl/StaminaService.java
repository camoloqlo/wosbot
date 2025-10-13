package cl.camodev.wosbot.serv.impl;

import cl.camodev.wosbot.serv.IStaminaChangeListener;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Singleton service that manages stamina for profiles.
 * Automatically regenerates 1 stamina every 5 minutes when stamina is below 200.
 */
public class StaminaService {

    private static StaminaService instance;

    // Thread-safe map to store stamina by profile ID
    private final ConcurrentHashMap<Long, Integer> staminaMap;

    // Thread-safe map to store last update timestamp by profile ID
    private final ConcurrentHashMap<Long, LocalDateTime> lastUpdateMap;

    // Scheduled executor for automatic stamina regeneration
    private final ScheduledExecutorService regenerationScheduler;

    // List of stamina change listeners (thread-safe)
    private final List<IStaminaChangeListener> listeners;

    // Maximum stamina threshold for regeneration
    private static final int MAX_STAMINA_FOR_REGEN = 200;

    // Regeneration amount per cycle
    private static final int REGEN_AMOUNT = 1;

    // Regeneration interval in minutes
    private static final long REGEN_INTERVAL_MINUTES = 5;

    /**
     * Private constructor to enforce singleton pattern.
     * Initializes the stamina map, timestamp map, and starts the regeneration scheduler.
     */
    private StaminaService() {
        this.staminaMap = new ConcurrentHashMap<>();
        this.lastUpdateMap = new ConcurrentHashMap<>();
        this.listeners = new ArrayList<>();
        this.regenerationScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "StaminaRegeneration");
            thread.setDaemon(true);
            return thread;
        });

        // Start the regeneration task
        startRegenerationTask();
    }

    /**
     * Gets the singleton instance of StaminaService.
     *
     * @return the singleton instance
     */
    public static synchronized StaminaService getServices() {
        if (instance == null) {
            instance = new StaminaService();
        }
        return instance;
    }

    /**
     * Adds a listener to be notified of stamina changes.
     *
     * @param listener the listener to add
     */
    public synchronized void addStaminaChangeListener(IStaminaChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a listener from stamina change notifications.
     *
     * @param listener the listener to remove
     */
    public synchronized void removeStaminaChangeListener(IStaminaChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notifies all listeners of a stamina change.
     *
     * @param profileId the profile ID
     * @param newStamina the new stamina value
     */
    private void notifyStaminaChange(Long profileId, int newStamina) {
        List<IStaminaChangeListener> listenersCopy;
        synchronized (this) {
            listenersCopy = new ArrayList<>(listeners);
        }

        for (IStaminaChangeListener listener : listenersCopy) {
            try {
                listener.onStaminaChanged(profileId, newStamina);
            } catch (Exception e) {
                System.err.println("Error notifying stamina listener: " + e.getMessage());
            }
        }
    }

    /**
     * Sets the stamina for a profile and updates the last update timestamp.
     *
     * @param profileId the profile ID
     * @param stamina the stamina amount to set
     */
    public void setStamina(Long profileId, int stamina) {
        if (profileId == null) {
            throw new IllegalArgumentException("Profile ID cannot be null");
        }
        int newStamina = Math.max(0, stamina);
        staminaMap.put(profileId, newStamina);
        lastUpdateMap.put(profileId, LocalDateTime.now());
        notifyStaminaChange(profileId, newStamina);
    }

    /**
     * Adds stamina to a profile and updates the last update timestamp.
     *
     * @param profileId the profile ID
     * @param amount the amount to add
     */
    public void addStamina(Long profileId, int amount) {
        if (profileId == null) {
            throw new IllegalArgumentException("Profile ID cannot be null");
        }
        int[] newStamina = new int[1];
        staminaMap.compute(profileId, (id, currentStamina) -> {
            int current = (currentStamina == null) ? 0 : currentStamina;
            newStamina[0] = Math.max(0, current + amount);
            return newStamina[0];
        });
        notifyStaminaChange(profileId, newStamina[0]);
        //lastUpdateMap.put(profileId, LocalDateTime.now());
    }

    /**
     * Subtracts stamina from a profile and updates the last update timestamp.
     *
     * @param profileId the profile ID
     * @param amount the amount to subtract
     */
    public void subtractStamina(Long profileId, int amount) {
        if (profileId == null) {
            throw new IllegalArgumentException("Profile ID cannot be null");
        }
        int[] newStamina = new int[1];
        staminaMap.compute(profileId, (id, currentStamina) -> {
            int current = (currentStamina == null) ? 0 : currentStamina;
            newStamina[0] = Math.max(0, current - amount);
            return newStamina[0];
        });
        notifyStaminaChange(profileId, newStamina[0]);
        //lastUpdateMap.put(profileId, LocalDateTime.now());
    }

    /**
     * Gets the current stamina for a profile.
     *
     * @param profileId the profile ID
     * @return the current stamina amount, or 0 if not set
     */
    public int getCurrentStamina(Long profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("Profile ID cannot be null");
        }
        return staminaMap.getOrDefault(profileId, 0);
    }


    /**
     * Checks if a profile requires an update.
     * A profile requires an update if the last update was more than 1 hour ago,
     * or if it has never been updated.
     *
     * @param profileId the profile ID
     * @return true if the profile requires an update, false otherwise
     */
    public boolean requiresUpdate(Long profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("Profile ID cannot be null");
        }

        LocalDateTime lastUpdate = lastUpdateMap.get(profileId);

        // If never updated, it requires an update
        if (lastUpdate == null) {
            return true;
        }

        // Check if more than 30 minutes has passed since last update
        long minutesSinceUpdate = ChronoUnit.MINUTES.between(lastUpdate, LocalDateTime.now());
        return minutesSinceUpdate >= 30;
    }

    /**
     * Starts the background task that regenerates stamina every 5 minutes.
     */
    private void startRegenerationTask() {
        regenerationScheduler.scheduleAtFixedRate(() -> {
            try {
                regenerateStamina();
            } catch (Exception e) {
                // Log error but don't stop the scheduler
                System.err.println("Error during stamina regeneration: " + e.getMessage());
            }
        }, REGEN_INTERVAL_MINUTES, REGEN_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Regenerates stamina for all profiles that have stamina below the threshold
     * and updates their last update timestamps.
     */
    private void regenerateStamina() {
        staminaMap.forEach((profileId, currentStamina) -> {
            if (currentStamina < MAX_STAMINA_FOR_REGEN) {
                int[] newStamina = new int[1];
                staminaMap.compute(profileId, (id, stamina) -> {
                    int current = (stamina == null) ? 0 : stamina;
                    if (current < MAX_STAMINA_FOR_REGEN) {
                        newStamina[0] = current + REGEN_AMOUNT;
                        return newStamina[0];
                    }
                    return current;
                });
                if (newStamina[0] > 0) {
                    notifyStaminaChange(profileId, newStamina[0]);
                }
            }
        });
    }

}
