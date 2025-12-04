package cl.camodev.wosbot.ot;

import java.time.Duration;
import java.time.LocalDateTime;

public class DTOTaskQueueStatus {
    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean needsReconnect;
    private volatile boolean readyToReconnect;
    private volatile boolean idleTimeExceeded;
    private Integer idleTimeLimit;
    private int backgroundChecks = 0;
    private int backgroundChecksInterval = 60; // every 60 loops

    private volatile LocalDateTime pausedAt;
    private volatile LocalDateTime delayUntil;
    private volatile LocalDateTime reconnectAt;

    private LoopState loopState = new LoopState();
    private volatile Thread reconnectThread;

    public DTOTaskQueueStatus() {
        this.running = false;
        this.paused = false;
        this.needsReconnect = false;
        this.readyToReconnect = false;
        this.pausedAt = LocalDateTime.MIN;
        this.delayUntil = LocalDateTime.now();
        this.idleTimeLimit = 15; // default 15 minutes
    }

    public LoopState getLoopState() {
        return this.loopState;
    }

    public boolean isIdleTimeExceeded() {
        return this.idleTimeExceeded;
    }

    public void setIdleTimeExceeded(boolean idleTimeExceeded) {
        this.idleTimeExceeded = idleTimeExceeded;
    }

    /**
     * Sets the idle time limit in minutes. When the delay exceeds this limit,
     * the idleTimeExceeded flag will be set.
     *
     * @param idleTimeLimit The maximum idle time in minutes
     */
    public void setIdleTimeLimit(Integer idleTimeLimit) {
        this.idleTimeLimit = idleTimeLimit;
    }

    /**
     * Determines if background checks should be run based on a counter.
     * Uses a default interval of 60 loops between checks.
     * Increments internal counter and resets it when the interval is reached.
     *
     * @return true if background checks should run, false otherwise
     */
    public boolean shouldRunBackgroundChecks() {
        this.backgroundChecks++;
        if (this.backgroundChecks >= this.backgroundChecksInterval) {
            this.backgroundChecks = 0;
            return true;
        }
        return false;
    }

    public void setBackgroundChecksInterval(Integer backgroundChecksInterval) {
        this.backgroundChecksInterval = backgroundChecksInterval;
    }

    /**
     * Sets the reconnection time and schedules a delayed reconnection.
     * Updates both the delay until the next action and the reconnection timestamp.
     *
     * @param reconnectionTime The time to wait before reconnecting, in minutes
     */
    public void setReconnectAt(long reconnectionTime) {
        this.setDelayUntil(LocalDateTime.now().plusMinutes(reconnectionTime));
        this.setReconnectAt(LocalDateTime.now().plusMinutes(reconnectionTime));
    }

    public void setReconnectAt(LocalDateTime reconnectAt) {
        this.pause();
        this.setNeedsReconnect(true);
        this.reconnectAt = reconnectAt;
        this.reconnectThread = Thread.startVirtualThread(() -> {
            try {
                if (Duration.between(LocalDateTime.now(), (reconnectAt)).toMillis() > 0) {
                    Thread.sleep(Duration.between(LocalDateTime.now(), (reconnectAt)).toMillis());
                    this.readyToReconnect = true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
    }

    public void cancelReconnectThread() {
        if (this.reconnectThread != null) {
            this.reconnectThread.interrupt();
        }
    }

    public LocalDateTime getReconnectAt() {
        return this.reconnectAt;
    }

    public void loopStarted() {
        this.loopState = new LoopState();
    }

    public void reset() {
        this.running = false;
        this.paused = false;
        this.needsReconnect = false;
        this.pausedAt = LocalDateTime.MIN;
        this.delayUntil = LocalDateTime.now();
    }

    public boolean isPaused() {
        return this.paused;
    }

    public void pause() {
        this.setPaused(true);
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused)
            this.pausedAt = LocalDateTime.now();
    }

    public boolean needsReconnect() {
        return this.needsReconnect;
    }

    public void setNeedsReconnect(boolean needsReconnect) {
        this.needsReconnect = needsReconnect;
    }

    public LocalDateTime getPausedAt() {
        return this.pausedAt;
    }

    public LocalDateTime getDelayUntil() {
        return this.delayUntil;
    }

    /**
     * Sets delay to a specific time from now
     *
     * @param delayUntil delay in seconds
     */
    public void setDelayUntil(long delayUntil) {
        this.delayUntil = LocalDateTime.now().plusSeconds(delayUntil);
    }

    public boolean checkIdleTimeExceeded() {
        return LocalDateTime.now().plusMinutes(this.idleTimeLimit).isBefore(this.getDelayUntil());
    }

    public void setDelayUntil(LocalDateTime delayUntil) {
        this.delayUntil = delayUntil;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean isReadyToReconnect() {
        return this.readyToReconnect;
    }

    public static class LoopState {
        private final long startTime;
        private long endTime;

        private boolean executedTask = false;

        public LoopState() {
            this.startTime = System.currentTimeMillis();
        }

        public void endLoop() {
            this.endTime = System.currentTimeMillis();
        }

        public long getDuration() {
            // If endTime is 0, endLoop() hasn't been called yet, return current duration
            if (this.endTime == 0) {
                return System.currentTimeMillis() - this.startTime;
            }
            return this.endTime - this.startTime;
        }

        public boolean isExecutedTask() {
            return this.executedTask;
        }

        public void setExecutedTask(boolean executedTask) {
            this.executedTask = executedTask;
        }
    }
}
