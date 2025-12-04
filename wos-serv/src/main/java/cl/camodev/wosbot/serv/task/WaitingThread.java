package cl.camodev.wosbot.serv.task;

import cl.camodev.wosbot.ot.DTOProfiles;

public class WaitingThread implements Comparable<WaitingThread> {
	final Thread thread;
	final Long priority;
	final Long arrivalTime;
	final Long profileId;

	public WaitingThread(Thread thread, DTOProfiles profile) {
		this.thread = thread;
		this.priority = profile.getPriority();
		this.profileId = profile.getId();
		this.arrivalTime = System.nanoTime(); // Timestamp for tiebreaking
	}

	@Override
	public int compareTo(WaitingThread other) {
		// Order from highest to lowest priority (higher value = higher priority)
		int cmp = Long.compare(other.priority, this.priority);
		if (cmp == 0) {
			// If they have the same priority, the one that arrived first takes precedence.
			cmp = Long.compare(this.arrivalTime, other.arrivalTime);
		}
		return cmp;
	}

	public Long getProfileId() {
		return profileId;
	}

	public Thread getThread() {
		return thread;
	}
}