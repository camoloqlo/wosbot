package cl.camodev.wosbot.serv.task;

public class WaitingThread implements Comparable<WaitingThread> {
	final Thread thread;
	final Long priority;
	final Long arrivalTime;

	public WaitingThread(Thread thread, Long priority) {
		this.thread = thread;
		this.priority = priority;
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

}