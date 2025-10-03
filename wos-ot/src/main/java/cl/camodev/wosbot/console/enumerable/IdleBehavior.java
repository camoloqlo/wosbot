package cl.camodev.wosbot.console.enumerable;

public enum IdleBehavior {
    CLOSE_EMULATOR("Close Emulator", false),
    SEND_TO_BACKGROUND("Close Game", true);

    private final String displayName;
    private final boolean sendToBackground;

    IdleBehavior(String displayName, boolean sendToBackground) {
        this.displayName = displayName;
        this.sendToBackground = sendToBackground;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean shouldSendToBackground() {
        return sendToBackground;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static IdleBehavior fromBoolean(boolean sendToBackground) {
        return sendToBackground ? SEND_TO_BACKGROUND : CLOSE_EMULATOR;
    }
}
