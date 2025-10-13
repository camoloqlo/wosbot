package cl.camodev.wosbot.ot;

public class DTOQueueProfileState {

        private Long profileId;
        private String profileName;
        private boolean paused;

        public DTOQueueProfileState() {
        }

        public DTOQueueProfileState(Long profileId, String profileName, boolean paused) {
                this.profileId = profileId;
                this.profileName = profileName;
                this.paused = paused;
        }

        public Long getProfileId() {
                return profileId;
        }

        public void setProfileId(Long profileId) {
                this.profileId = profileId;
        }

        public String getProfileName() {
                return profileName;
        }

        public void setProfileName(String profileName) {
                this.profileName = profileName;
        }

        public boolean isPaused() {
                return paused;
        }

        public void setPaused(boolean paused) {
                this.paused = paused;
        }
}
