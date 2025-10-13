package cl.camodev.wosbot.ot;

import java.util.List;

public class DTOQueueState {

        private Long profileId;
        private boolean paused;
        private List<DTOQueueProfileState> activeQueues;

        public DTOQueueState() {
        }

        public DTOQueueState(Long profileId, boolean paused) {
                this(profileId, paused, null);
        }

        public DTOQueueState(Long profileId, boolean paused, List<DTOQueueProfileState> activeQueues) {
                this.profileId = profileId;
                this.paused = paused;
                this.activeQueues = activeQueues;
        }

        public Long getProfileId() {
                return profileId;
        }

        public void setProfileId(Long profileId) {
                this.profileId = profileId;
        }

        public boolean isPaused() {
                return paused;
        }

        public void setPaused(boolean paused) {
                this.paused = paused;
        }

        public List<DTOQueueProfileState> getActiveQueues() {
                return activeQueues;
        }

        public void setActiveQueues(List<DTOQueueProfileState> activeQueues) {
                this.activeQueues = activeQueues;
        }
}
