package cl.camodev.wosbot.serv.task.helper;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;

public record TemplateSearchHelper(EmulatorManager emuManager, String emulatorNumber) {


    public DTOImageSearchResult searchTemplate(EnumTemplates template, SearchConfig config) {
        DTOImageSearchResult result = null;
        int attempts = 0;

        while (attempts < config.getMaxAttempts()) {
            attempts++;

            result = executeSearch(emulatorNumber, template, config);

            // Si encontró el template, retornar inmediatamente
            if (result != null && result.isFound()) {
                return result;
            }

            // Si no es el último intento, esperar el delay
            if (attempts < config.getMaxAttempts()) {
                sleep(config.getDelayBetweenAttempts());
            }
        }

        return result;
    }


    private DTOImageSearchResult executeSearch(String emulatorNumber, EnumTemplates template, SearchConfig config) {
        if (config.hasArea()) {
            return emuManager.searchTemplate(emulatorNumber, template,
                    config.getArea().topLeft(),config.getArea().bottomRight(), config.getThreshold());
        } else if (config.hasCoordinates()) {
            return emuManager.searchTemplate(emulatorNumber, template,
                    config.getStartPoint(), config.getEndPoint(), config.getThreshold());
        } else {
            return emuManager.searchTemplate(emulatorNumber, template, config.getThreshold());
        }
    }

    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class SearchConfig {
        private final int maxAttempts;
        private final long delayBetweenAttempts;
        private final int threshold;
        private final DTOPoint startPoint;
        private final DTOPoint endPoint;
        private final DTOArea area;

        private SearchConfig(Builder builder) {
            this.maxAttempts = builder.maxAttempts;
            this.delayBetweenAttempts = builder.delayBetweenAttempts;
            this.threshold = builder.threshold;
            this.startPoint = builder.startPoint;
            this.endPoint = builder.endPoint;
            this.area = builder.area;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private int maxAttempts = 1;
            private long delayBetweenAttempts = 1000;
            private int threshold = 90;
            private DTOPoint startPoint = null;
            private DTOPoint endPoint = null;
            private DTOArea area = null;

            public Builder withMaxAttempts(int attempts) {
                this.maxAttempts = attempts;
                return this;
            }

            public Builder withDelay(long milliseconds) {
                this.delayBetweenAttempts = milliseconds;
                return this;
            }

            public Builder withThreshold(int threshold) {
                this.threshold = threshold;
                return this;
            }

            public Builder withArea(DTOArea area) {
                this.area = area;
                this.startPoint = null;
                this.endPoint = null;
                return this;
            }

            public Builder withCoordinates(DTOPoint start, DTOPoint end) {
                this.startPoint = start;
                this.endPoint = end;
                this.area = null;
                return this;
            }

            public SearchConfig build() {
                return new SearchConfig(this);
            }
        }

        // Getters
        public int getMaxAttempts() { return maxAttempts; }
        public long getDelayBetweenAttempts() { return delayBetweenAttempts; }
        public int getThreshold() { return threshold; }
        public DTOPoint getStartPoint() { return startPoint; }
        public DTOPoint getEndPoint() { return endPoint; }
        public DTOArea getArea() { return area; }

        public boolean hasCoordinates() {
            return startPoint != null && endPoint != null;
        }

        public boolean hasArea() {
            return area != null;
        }
    }
}

