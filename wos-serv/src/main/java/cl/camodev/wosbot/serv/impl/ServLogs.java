package cl.camodev.wosbot.serv.impl;

import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.list.ILogListener;
import cl.camodev.wosbot.ot.DTOLogMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServLogs {

	private static ServLogs instance;

	private final List<ILogListener> logListeners = new CopyOnWriteArrayList<>();

	private ServLogs() {

	}

	public static ServLogs getServices() {
		if (instance == null) {
			instance = new ServLogs();
		}
		return instance;
	}

	/**
	 * Sets a log listener (for backward compatibility).
	 * This will add the listener to the list if not already present.
	 * @param listener The log listener to set
	 */
	public void setLogListener(ILogListener listener) {
		if (listener != null && !logListeners.contains(listener)) {
			logListeners.add(listener);
		}
	}

	/**
	 * Adds a log listener to receive log messages.
	 * @param listener The log listener to add
	 */
	public void addLogListener(ILogListener listener) {
		if (listener != null && !logListeners.contains(listener)) {
			logListeners.add(listener);
		}
	}

	/**
	 * Removes a log listener.
	 * @param listener The log listener to remove
	 */
	public void removeLogListener(ILogListener listener) {
		logListeners.remove(listener);
	}

	public void appendLog(EnumTpMessageSeverity severity, String task, String profile, String message) {

		DTOLogMessage logMessage = new DTOLogMessage(severity, message, task, profile);
//		ServDiscord.getServices().sendLog(logMessage);

		// Notify all registered listeners
		for (ILogListener listener : logListeners) {
			try {
				listener.onLogReceived(logMessage);
			} catch (Exception e) {
				// Log the error but don't let one failing listener affect others
				System.err.println("Error notifying log listener: " + e.getMessage());
			}
		}
	}
}
