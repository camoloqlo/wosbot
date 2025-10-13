package cl.camodev.wosbot.serv;

import cl.camodev.wosbot.ot.DTOQueueState;

public interface IQueueStateListener {

	public void onQueueStateChange(DTOQueueState queueState);

}
