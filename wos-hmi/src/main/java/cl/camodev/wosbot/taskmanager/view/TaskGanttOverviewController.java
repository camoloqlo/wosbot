package cl.camodev.wosbot.taskmanager.view;

import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTODailyTaskStatus;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.taskmanager.model.TaskManagerAux;
import cl.camodev.wosbot.serv.IStaminaChangeListener;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.taskmanager.controller.TaskManagerActionController;
import cl.camodev.wosbot.taskmanager.ITaskStatusChangeListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.shape.Rectangle;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class TaskGanttOverviewController implements ITaskStatusChangeListener, IStaminaChangeListener {
    @FXML
    private VBox vboxAccounts;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private HBox timeAxisHeader;

    @FXML
    private ToggleButton toggleViewButton;

    @FXML
    private ToggleButton toggleInactiveTasksButton;

    private TaskManagerActionController taskManagerActionController;
    private javafx.animation.Timeline autoRefreshTimeline;
    private String taskFilter = "";
    private List<DTOProfiles> lastLoadedProfiles = new ArrayList<>();
    private final Map<Long, Label> profileStaminaLabels = new HashMap<>();

    private enum ViewMode {
        TWO_HOURS("2 Hours", 120, 5),
        TWENTY_FOUR_HOURS("24 Hours", 1500, 5),
        WEEK("Week", 11520, 60);

        private final String label;
        private final int windowMinutes;
        private final int minBarDurationMinutes;

        ViewMode(String label, int windowMinutes, int minBarDurationMinutes) {
            this.label = label;
            this.windowMinutes = windowMinutes;
            this.minBarDurationMinutes = minBarDurationMinutes;
        }

        String getLabel() {
            return label;
        }

        int getWindowMinutes() {
            return windowMinutes;
        }

        double getMinBarWidthMinutes() {
            return minBarDurationMinutes;
        }

        ViewMode next() {
            return switch (this) {
                case TWO_HOURS -> TWENTY_FOUR_HOURS;
                case TWENTY_FOUR_HOURS -> WEEK;
                case WEEK -> TWO_HOURS;
            };
        }
    }

    private static final double TWO_HOUR_PIVOT_RATIO = 30d / 120d;

    private static final class TimelineMetrics {
        private enum ScaleType { LINEAR, PURE_LOG, HYBRID_LOG_LINEAR }

        private final ScaleType scaleType;
        private final LocalDateTime viewStart;
        private final LocalDateTime viewEnd;
        private final double width;
        private final double windowMinutes;

        // Linear
        private final double linearPixelsPerMinute;

        // Pure logarithmic
        private final double pureLogDenominator;

        // Hybrid log-linear
        private final double hybridLeftSpanMinutes;
        private final double hybridPivotX;
        private final double hybridLeftLogDenominator;
        private final double hybridRightPixelsPerMinute;

        private TimelineMetrics(
            ScaleType scaleType,
            LocalDateTime viewStart,
            LocalDateTime viewEnd,
            double width,
            double windowMinutes,
            double linearPixelsPerMinute,
            double pureLogDenominator,
            double hybridLeftSpanMinutes,
            double hybridPivotX,
            double hybridLeftLogDenominator,
            double hybridRightPixelsPerMinute
        ) {
            this.scaleType = scaleType;
            this.viewStart = viewStart;
            this.viewEnd = viewEnd;
            this.width = width;
            this.windowMinutes = windowMinutes;
            this.linearPixelsPerMinute = linearPixelsPerMinute;
            this.pureLogDenominator = pureLogDenominator;
            this.hybridLeftSpanMinutes = hybridLeftSpanMinutes;
            this.hybridPivotX = hybridPivotX;
            this.hybridLeftLogDenominator = hybridLeftLogDenominator;
            this.hybridRightPixelsPerMinute = hybridRightPixelsPerMinute;
        }

        static TimelineMetrics linear(LocalDateTime viewStart, double width, int windowMinutes) {
            LocalDateTime viewEnd = viewStart.plusMinutes(windowMinutes);
            double effectiveMinutes = Math.max(1d, ChronoUnit.SECONDS.between(viewStart, viewEnd) / 60d);
            double pixelsPerMinute = width / effectiveMinutes;
            return new TimelineMetrics(
                ScaleType.LINEAR,
                viewStart,
                viewEnd,
                width,
                effectiveMinutes,
                pixelsPerMinute,
                0d,
                0d,
                0d,
                0d,
                pixelsPerMinute
            );
        }

        static TimelineMetrics hybridLogLinear(LocalDateTime viewStart, LocalDateTime pivotTime, LocalDateTime viewEnd, double width, double pivotRatio) {
            double totalMinutes = Math.max(1d, ChronoUnit.SECONDS.between(viewStart, viewEnd) / 60d);
            double leftSpan = Math.max(0d, ChronoUnit.SECONDS.between(viewStart, pivotTime) / 60d);
            double rightSpan = Math.max(0d, totalMinutes - leftSpan);
            double clampedRatio = Math.max(0d, Math.min(1d, pivotRatio));
            double pivotX = width * clampedRatio;
            double leftLogDenominator = leftSpan > 0d ? Math.log1p(leftSpan) : 1d;
            double rightPixelsPerMinute = rightSpan > 0d ? (width - pivotX) / rightSpan : 0d;
            return new TimelineMetrics(
                ScaleType.HYBRID_LOG_LINEAR,
                viewStart,
                viewEnd,
                width,
                totalMinutes,
                rightPixelsPerMinute,
                0d,
                leftSpan,
                pivotX,
                leftLogDenominator,
                rightPixelsPerMinute
            );
        }

        double toX(LocalDateTime time) {
            double minutes = ChronoUnit.SECONDS.between(viewStart, time) / 60.0;
            minutes = Math.max(0d, Math.min(minutes, windowMinutes));

            switch (scaleType) {
                case LINEAR:
                    return minutes * linearPixelsPerMinute;
                case PURE_LOG:
                    if (pureLogDenominator == 0d) {
                        return 0d;
                    }
                    return width * Math.log1p(minutes) / pureLogDenominator;
                case HYBRID_LOG_LINEAR:
                    if (minutes <= hybridLeftSpanMinutes) {
                        if (hybridLeftSpanMinutes <= 0d || hybridLeftLogDenominator == 0d) {
                            return 0d;
                        }
                        double distanceFromPivot = Math.max(0d, hybridLeftSpanMinutes - minutes);
                        double normalized = Math.log1p(distanceFromPivot) / hybridLeftLogDenominator;
                        return hybridPivotX * (1d - normalized);
                    }
                    double rightMinutes = minutes - hybridLeftSpanMinutes;
                    return hybridPivotX + rightMinutes * hybridRightPixelsPerMinute;
                default:
                    throw new IllegalStateException("Unexpected scale type: " + scaleType);
            }
        }

        double widthBetween(LocalDateTime start, double minutesSpan) {
            double clampedSpan = Math.max(0d, minutesSpan);
            Duration duration = Duration.ofMillis((long) Math.round(clampedSpan * 60_000d));
            LocalDateTime end = start.plus(duration);
            if (end.isAfter(viewEnd)) {
                end = viewEnd;
            }
            double startX = toX(start);
            double endX = toX(end);
            return Math.max(0d, endX - startX);
        }
    }

    private ViewMode viewMode = ViewMode.TWO_HOURS;
    
    // Stores tasks per profile for stable ordering
    private Map<Long, List<TaskManagerAux>> profileTasksMap = new LinkedHashMap<>();
    
    // Dynamic width calculation based on window size
    private double availableWidth = 720; // Default
    private static final double ACCOUNT_LABEL_WIDTH = 128;
    private static final double TIME_AXIS_LABEL_WIDTH = 60; // Width of "Local/UTC" label column
    private boolean showInactiveTasks = false;

    public void initialize() {
        taskManagerActionController = new TaskManagerActionController(null);
        
        // Register as listener for live task updates
        ServTaskManager.getInstance().addTaskStatusChangeListener(this);
        StaminaService.getServices().addStaminaChangeListener(this);
        
        // Configure VBox for compact display
        if (vboxAccounts != null) {
            vboxAccounts.setSpacing(0); // No spacing between account rows
            vboxAccounts.setFillWidth(true);
        }
        
        // Initialize toggle button
        if (toggleViewButton != null) {
            toggleViewButton.setText(viewMode.getLabel());
        }

        if (toggleInactiveTasksButton != null) {
            toggleInactiveTasksButton.setSelected(true);
            toggleInactiveTasksButton.setText("Show inactive");
        }
        
        // Listener for window size changes
        if (scrollPane != null) {
            scrollPane.widthProperty().addListener((obs, oldWidth, newWidth) -> {
                updateAvailableWidth(newWidth.doubleValue());
                Platform.runLater(() -> {
                    buildTimeAxis();
                    loadAccounts();
                });
            });
        }
        
        Platform.runLater(() -> {
            updateAvailableWidth(scrollPane != null ? scrollPane.getWidth() : 800);
            buildTimeAxis();
            loadAccounts();
        });
        
        // Real-time refresh every 5 seconds - update both time axis and tasks
        autoRefreshTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), e -> {
                buildTimeAxis(); // Update time axis (important for 2h view)
                loadAccounts();  // Reload tasks
            })
        );
        autoRefreshTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        autoRefreshTimeline.play();
    }

    /**
     * Applies the task name filter used by the table view to the timeline.
     */
    public void setTaskFilter(String filterText) {
        String normalized = filterText == null ? "" : filterText.trim().toLowerCase(Locale.ENGLISH);
        if (Objects.equals(normalized, taskFilter)) {
            return;
        }

        taskFilter = normalized;

        if (!profileTasksMap.isEmpty() && !lastLoadedProfiles.isEmpty()) {
            List<DTOProfiles> snapshot = new ArrayList<>(lastLoadedProfiles);
            Platform.runLater(() -> rebuildUI(snapshot));
        }
    }
    
    /**
     * Updates the available width based on the ScrollPane width
     */
    private void updateAvailableWidth(double scrollPaneWidth) {
        // Calculate available width: ScrollPane - Account Label - Padding/Margins
        double minWidth = 400; // Minimum for readability
        double maxWidth = 2000; // Maximum for performance
        availableWidth = Math.max(minWidth, Math.min(maxWidth, scrollPaneWidth - ACCOUNT_LABEL_WIDTH - 80));
    }
    
    @Override
    public void onTaskStatusChange(Long profileId, int taskNameId, DTOTaskState taskState) {
        // Update task status in real-time when a task is executed
        Platform.runLater(() -> {
            List<TaskManagerAux> tasks = profileTasksMap.get(profileId);
            if (tasks != null) {
                for (TaskManagerAux task : tasks) {
                    if (task.getTaskEnum().getId() == taskNameId) {
                        task.setExecuting(taskState.isExecuting());
                        task.setLastExecution(taskState.getLastExecutionTime());
                        task.setNextExecution(taskState.getNextExecutionTime());
                        task.setScheduled(taskState.isScheduled());
                        
                        // Trigger UI refresh for ALL enabled profiles (not just the affected one)
                        List<DTOProfiles> allEnabledProfiles = ServProfiles.getServices().getProfiles().stream()
                            .filter(p -> p.getEnabled() != null && p.getEnabled())
                            .sorted(Comparator.comparing(DTOProfiles::getName))
                            .collect(Collectors.toList());
                        
                        if (!allEnabledProfiles.isEmpty()) {
                            rebuildUI(allEnabledProfiles);
                        }
                        break;
                    }
                }
            }
        });
    }

    @FXML
    private void toggleTimeView() {
        viewMode = viewMode.next();
        if (toggleViewButton != null) {
            toggleViewButton.setText(viewMode.getLabel());
        }
        buildTimeAxis();
        loadAccounts();
    }

    @FXML
    private void toggleInactiveTasks() {
        boolean hideInactive = toggleInactiveTasksButton != null && toggleInactiveTasksButton.isSelected();
        showInactiveTasks = !hideInactive;
        if (toggleInactiveTasksButton != null) {
            toggleInactiveTasksButton.setText(hideInactive ? "Show inactive" : "Hide inactive");
        }

        if (!lastLoadedProfiles.isEmpty()) {
            rebuildUI(new ArrayList<>(lastLoadedProfiles));
        } else {
            loadAccounts();
        }
    }

    private void buildTimeAxis() {
        if (timeAxisHeader == null) return;

        timeAxisHeader.getChildren().clear();
        LocalDateTime now = LocalDateTime.now();

        timeAxisHeader.getChildren().add(buildTimeZoneHeader());

        javafx.scene.layout.Pane axisPane = new javafx.scene.layout.Pane();
        axisPane.setPrefWidth(availableWidth);
        axisPane.setMinWidth(availableWidth);
        axisPane.setMaxWidth(availableWidth);
        timeAxisHeader.getChildren().add(axisPane);

        switch (viewMode) {
            case TWENTY_FOUR_HOURS -> buildTwentyFourHourAxis(axisPane, now);
            case WEEK -> buildWeekAxis(axisPane, now);
            case TWO_HOURS -> buildTwoHourAxis(axisPane, now);
        }

        timeAxisHeader.setPrefWidth(TIME_AXIS_LABEL_WIDTH + availableWidth);
    }

    private javafx.scene.layout.VBox buildTimeZoneHeader() {
        javafx.scene.layout.VBox tzLabel = new javafx.scene.layout.VBox(0);
        tzLabel.setAlignment(javafx.geometry.Pos.CENTER);
        tzLabel.setPrefWidth(60);
        tzLabel.setMinWidth(60);
        tzLabel.setMaxWidth(60);

        Label lblLocalHeader = new Label("Local");
        lblLocalHeader.setStyle("-fx-text-fill: #ffb347; -fx-font-size: 9; -fx-font-weight: bold;");
        lblLocalHeader.setAlignment(javafx.geometry.Pos.CENTER);

        Label lblUtcHeader = new Label("UTC");
        lblUtcHeader.setStyle("-fx-text-fill: #ffb347; -fx-font-size: 8; -fx-font-weight: bold;");
        lblUtcHeader.setAlignment(javafx.geometry.Pos.CENTER);

        tzLabel.getChildren().addAll(lblLocalHeader, lblUtcHeader);
        return tzLabel;
    }

    private void buildTwentyFourHourAxis(javafx.scene.layout.Pane axisPane, LocalDateTime now) {
        LocalDateTime startTime = resolveViewStart(ViewMode.TWENTY_FOUR_HOURS, now);
        double hourWidth = availableWidth / 25.0;
        double labelWidth = Math.max(48, Math.min(120, hourWidth + 24));

        for (int h = 0; h < 25; h++) {
            LocalDateTime hourTime = startTime.plusHours(h);
            java.time.ZonedDateTime utcHour = hourTime.atZone(java.time.ZoneId.systemDefault())
                .withZoneSameInstant(java.time.ZoneOffset.UTC);

            javafx.scene.layout.VBox timeBox = new javafx.scene.layout.VBox(0);
            timeBox.setAlignment(javafx.geometry.Pos.CENTER);
            timeBox.setPrefWidth(labelWidth);
            timeBox.setMinWidth(labelWidth);
            timeBox.setMaxWidth(labelWidth);

            Label lblLocal = new Label(String.format("%02d", hourTime.getHour()));
            lblLocal.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: %d;",
                (h % 6 == 0) ? "#888888" : "#666666",
                (h % 6 == 0) ? 10 : 9
            ));

            Label lblUtc = new Label(String.format("%02d", utcHour.getHour()));
            lblUtc.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 8;",
                (h % 6 == 0) ? "#666666" : "#555555"
            ));

            timeBox.getChildren().addAll(lblLocal, lblUtc);
            double centerX = h * hourWidth;
            double desiredX = centerX - (labelWidth / 2.0);
            double maxX = availableWidth - labelWidth;
            double layoutX = Math.max(0, Math.min(desiredX, maxX));
            javafx.geometry.Pos alignment = javafx.geometry.Pos.CENTER;
            javafx.geometry.Insets padding = javafx.geometry.Insets.EMPTY;
            if (layoutX <= 0.5) {
                alignment = javafx.geometry.Pos.CENTER_LEFT;
                padding = new javafx.geometry.Insets(0, 0, 0, 2);
            } else if (layoutX >= maxX - 0.5) {
                alignment = javafx.geometry.Pos.CENTER_RIGHT;
                padding = new javafx.geometry.Insets(0, 2, 0, 0);
            }
            timeBox.setAlignment(alignment);
            lblLocal.setAlignment(alignment);
            lblUtc.setAlignment(alignment);
            timeBox.setPadding(padding);
            timeBox.setLayoutX(layoutX);
            axisPane.getChildren().add(timeBox);
        }
    }

    private void buildWeekAxis(javafx.scene.layout.Pane axisPane, LocalDateTime now) {
        LocalDateTime startTime = resolveViewStart(ViewMode.WEEK, now);
        double dayWidth = availableWidth / 8.0;
        double labelWidth = Math.max(100, Math.min(160, dayWidth + 40));

        for (int d = 0; d < 8; d++) {
            LocalDateTime dayTime = startTime.plusDays(d);
            java.time.ZonedDateTime utcDay = dayTime.withHour(12).atZone(java.time.ZoneId.systemDefault())
                .withZoneSameInstant(java.time.ZoneOffset.UTC);

            javafx.scene.layout.VBox timeBox = new javafx.scene.layout.VBox(0);
            timeBox.setAlignment(javafx.geometry.Pos.CENTER);
            timeBox.setPrefWidth(labelWidth);
            timeBox.setMinWidth(labelWidth);
            timeBox.setMaxWidth(labelWidth);

            String localLabel = d == 0 ? "Today" : String.format("%02d.%02d",
                dayTime.getDayOfMonth(), dayTime.getMonthValue());

            String utcLabel;
            int localDay = dayTime.getDayOfMonth();
            int utcDayNum = utcDay.getDayOfMonth();
            int localMonth = dayTime.getMonthValue();
            int utcMonth = utcDay.getMonthValue();

            if (localDay == utcDayNum && localMonth == utcMonth) {
                utcLabel = String.format("%02d.%02d", utcDayNum, utcMonth);
            } else if (utcDay.toLocalDate().isBefore(dayTime.toLocalDate())) {
                utcLabel = String.format("%02d.%02d(-1)", utcDayNum, utcMonth);
            } else {
                utcLabel = String.format("%02d.%02d(+1)", utcDayNum, utcMonth);
            }

            Label lblLocal = new Label(localLabel);
            lblLocal.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: %d; -fx-font-weight: %s;",
                (d == 0) ? "#ffb347" : "#888888",
                (d == 0) ? 11 : 10,
                (d == 0) ? "bold" : "normal"
            ));

            Label lblUtc = new Label(utcLabel);
            lblUtc.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 7;",
                (d == 0) ? "#ffaa44" : "#666666"
            ));

            timeBox.getChildren().addAll(lblLocal, lblUtc);
            double centerX = d * dayWidth;
            double desiredX = centerX - (labelWidth / 2.0);
            double maxX = availableWidth - labelWidth;
            double layoutX = Math.max(0, Math.min(desiredX, maxX));
            javafx.geometry.Pos alignment = javafx.geometry.Pos.CENTER;
            javafx.geometry.Insets padding = javafx.geometry.Insets.EMPTY;
            if (layoutX <= 0.5) {
                alignment = javafx.geometry.Pos.CENTER_LEFT;
                padding = new javafx.geometry.Insets(0, 0, 0, 2);
            } else if (layoutX >= maxX - 0.5) {
                alignment = javafx.geometry.Pos.CENTER_RIGHT;
                padding = new javafx.geometry.Insets(0, 2, 0, 0);
            }
            timeBox.setAlignment(alignment);
            lblLocal.setAlignment(alignment);
            lblUtc.setAlignment(alignment);
            timeBox.setPadding(padding);
            timeBox.setLayoutX(layoutX);
            axisPane.getChildren().add(timeBox);
        }
    }

    private void buildTwoHourAxis(javafx.scene.layout.Pane axisPane, LocalDateTime now) {
        LocalDateTime viewStart = resolveViewStart(ViewMode.TWO_HOURS, now);
        LocalDateTime viewEnd = resolveViewEnd(ViewMode.TWO_HOURS, now);
    TimelineMetrics metrics = TimelineMetrics.hybridLogLinear(viewStart, now, viewEnd, availableWidth, TWO_HOUR_PIVOT_RATIO);

        List<LocalDateTime> tickTimes = buildTwoHourTickTimes(viewStart, now, viewEnd);
        if (tickTimes.isEmpty()) {
            return;
        }

        double labelWidth = 104;

        for (int i = 0; i < tickTimes.size(); i++) {
            LocalDateTime time = tickTimes.get(i);
            ZonedDateTime localZone = time.atZone(ZoneId.systemDefault());
            ZonedDateTime utcTime = localZone.withZoneSameInstant(ZoneOffset.UTC);

            boolean isNow = time.isEqual(now);
            boolean isPast = time.isBefore(now);

            String localLabel = String.format("%02d:%02d", localZone.getHour(), localZone.getMinute());
            String utcLabel = String.format("%02d:%02d", utcTime.getHour(), utcTime.getMinute());

            String localColor = isNow ? "#ffb347" : (isPast ? "#888888" : "#666666");
            String utcColor = isNow ? "#ffb347" : (isPast ? "#666666" : "#555555");

            Label lblLocal = new Label(localLabel);
            lblLocal.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 10; -fx-font-weight: %s;", localColor, isNow ? "bold" : "normal"));

            Label lblUtc = new Label(utcLabel);
            lblUtc.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 8; -fx-font-weight: %s;", utcColor, isNow ? "bold" : "normal"));

            javafx.scene.layout.VBox timeBox = new javafx.scene.layout.VBox(lblLocal, lblUtc);
            timeBox.setAlignment(javafx.geometry.Pos.CENTER);
            timeBox.setPrefWidth(labelWidth);
            timeBox.setMinWidth(labelWidth);
            timeBox.setMaxWidth(labelWidth);

            double centerX = metrics.toX(time);
            double layoutX = centerX - (labelWidth / 2.0);
            double maxX = availableWidth - labelWidth;
            javafx.geometry.Pos alignment = javafx.geometry.Pos.CENTER;
            javafx.geometry.Insets padding = javafx.geometry.Insets.EMPTY;

            if (i == 0) {
                layoutX = 0;
                alignment = javafx.geometry.Pos.CENTER_LEFT;
                padding = new javafx.geometry.Insets(0, 0, 0, 2);
            } else if (i == tickTimes.size() - 1) {
                layoutX = maxX;
                alignment = javafx.geometry.Pos.CENTER_RIGHT;
                padding = new javafx.geometry.Insets(0, 2, 0, 0);
            } else {
                layoutX = Math.max(0, Math.min(layoutX, maxX));
            }

            timeBox.setAlignment(alignment);
            lblLocal.setAlignment(alignment);
            lblUtc.setAlignment(alignment);
            timeBox.setPadding(padding);
            timeBox.setLayoutX(layoutX);
            axisPane.getChildren().add(timeBox);
        }
    }

    private LocalDateTime resolveViewStart(ViewMode mode, LocalDateTime now) {
        return switch (mode) {
            case TWO_HOURS -> {
                ZonedDateTime nowUtc = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC);
                ZonedDateTime midnightUtc = nowUtc.toLocalDate().atStartOfDay(ZoneOffset.UTC);
                yield midnightUtc.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            }
            case TWENTY_FOUR_HOURS -> now.minusHours(1).withMinute(0).withSecond(0).withNano(0);
            case WEEK -> now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        };
    }

    private LocalDateTime resolveViewEnd(ViewMode mode, LocalDateTime now) {
        return switch (mode) {
            case TWO_HOURS -> now.plusMinutes(mode.getWindowMinutes());
            case TWENTY_FOUR_HOURS, WEEK -> resolveViewStart(mode, now).plusMinutes(mode.getWindowMinutes());
        };
    }

    private List<LocalDateTime> buildTwoHourTickTimes(LocalDateTime viewStart, LocalDateTime now, LocalDateTime viewEnd) {
        java.util.NavigableSet<LocalDateTime> ticks = new java.util.TreeSet<>();
        ticks.add(viewStart);

        for (int hour = 6; hour <= 18; hour += 6) {
            LocalDateTime candidate = viewStart.plusHours(hour);
            if (!candidate.isBefore(now)) {
                break;
            }
            ticks.add(candidate);
        }

        ticks.add(now);

        int[] futureMinutes = {15, 30, 45, 60, 75, 90, 105, 120};
        for (int minutes : futureMinutes) {
            LocalDateTime candidate = now.plusMinutes(minutes);
            if (!candidate.isAfter(viewEnd)) {
                ticks.add(candidate);
            }
        }

        if (!ticks.contains(viewEnd)) {
            ticks.add(viewEnd);
        }

        return new ArrayList<>(ticks);
    }

    private TimelineMetrics drawTimelineBackground(javafx.scene.layout.Pane timelinePane, ViewMode mode, double width, int rowHeight, LocalDateTime now) {
        switch (mode) {
            case TWENTY_FOUR_HOURS -> {
                double hourWidth = width / 25.0;
                for (int h = 0; h < 25; h++) {
                    double x = h * hourWidth;
                    javafx.scene.shape.Line line = new javafx.scene.shape.Line(x, 0, x, 36);
                    if (h % 6 == 0) {
                        line.setStroke(javafx.scene.paint.Color.web("#555555"));
                        line.setStrokeWidth(1.5);
                    } else {
                        line.setStroke(javafx.scene.paint.Color.web("#3a3a3a"));
                        line.setStrokeWidth(0.5);
                    }
                    timelinePane.getChildren().add(line);
                }

                LocalDateTime viewStart = resolveViewStart(ViewMode.TWENTY_FOUR_HOURS, now);
                TimelineMetrics metrics = TimelineMetrics.linear(viewStart, width, mode.getWindowMinutes());
                double nowX = metrics.toX(now);

                javafx.scene.shape.Line nowLine = new javafx.scene.shape.Line(nowX, 0, nowX, rowHeight);
                nowLine.setStroke(javafx.scene.paint.Color.web("#ff4444"));
                nowLine.setStrokeWidth(2);
                nowLine.getStrokeDashArray().addAll(5.0, 3.0);
                timelinePane.getChildren().add(nowLine);
                return metrics;
            }
            case WEEK -> {
                double dayWidth = width / 8.0;
                for (int d = 0; d < 8; d++) {
                    double x = d * dayWidth;
                    javafx.scene.shape.Line line = new javafx.scene.shape.Line(x, 0, x, rowHeight);
                    if (d == 0) {
                        line.setStroke(javafx.scene.paint.Color.web("#666666"));
                        line.setStrokeWidth(2.0);
                    } else if (d % 2 == 0) {
                        line.setStroke(javafx.scene.paint.Color.web("#555555"));
                        line.setStrokeWidth(1.0);
                    } else {
                        line.setStroke(javafx.scene.paint.Color.web("#3a3a3a"));
                        line.setStrokeWidth(0.5);
                    }
                    timelinePane.getChildren().add(line);
                }

                LocalDateTime viewStart = resolveViewStart(ViewMode.WEEK, now);
                TimelineMetrics metrics = TimelineMetrics.linear(viewStart, width, mode.getWindowMinutes());
                double nowX = metrics.toX(now);

                javafx.scene.shape.Line nowLine = new javafx.scene.shape.Line(nowX, 0, nowX, rowHeight);
                nowLine.setStroke(javafx.scene.paint.Color.web("#ff4444"));
                nowLine.setStrokeWidth(2);
                nowLine.getStrokeDashArray().addAll(5.0, 3.0);
                timelinePane.getChildren().add(nowLine);
                return metrics;
            }
            case TWO_HOURS -> {
                LocalDateTime viewStart = resolveViewStart(mode, now);
                LocalDateTime viewEnd = resolveViewEnd(mode, now);
                TimelineMetrics metrics = TimelineMetrics.hybridLogLinear(viewStart, now, viewEnd, width, TWO_HOUR_PIVOT_RATIO);
                List<LocalDateTime> tickTimes = buildTwoHourTickTimes(viewStart, now, viewEnd);
                for (LocalDateTime tickTime : tickTimes) {
                    if (tickTime.isEqual(now)) {
                        continue; // Dedicated red line handles the current time marker.
                    }
                    double x = metrics.toX(tickTime);
                    javafx.scene.shape.Line line = new javafx.scene.shape.Line(x, 0, x, rowHeight);
                    boolean major;
                    if (tickTime.isBefore(now)) {
                        long hoursFromStart = Math.max(0, ChronoUnit.HOURS.between(viewStart, tickTime));
                        major = hoursFromStart == 0 || hoursFromStart % 6 == 0;
                    } else {
                        long minutesFromNow = ChronoUnit.MINUTES.between(now, tickTime);
                        major = minutesFromNow % 60 == 0;
                    }
                    line.setStroke(javafx.scene.paint.Color.web(major ? "#555555" : "#3a3a3a"));
                    line.setStrokeWidth(major ? 1.5 : 0.5);
                    timelinePane.getChildren().add(line);
                }
                double nowX = metrics.toX(now);

                javafx.scene.shape.Line nowLine = new javafx.scene.shape.Line(nowX, 0, nowX, rowHeight);
                nowLine.setStroke(javafx.scene.paint.Color.web("#ff4444"));
                nowLine.setStrokeWidth(2);
                nowLine.getStrokeDashArray().addAll(5.0, 3.0);
                timelinePane.getChildren().add(nowLine);
                return metrics;
            }
            default -> throw new IllegalStateException("Unsupported view mode: " + mode);
        }
    }

    private LocalDateTime resolveDisplayTime(TaskManagerAux task, LocalDateTime now) {
        if (task.isExecuting()) {
            return now;
        }
        if (task.getNextExecution() != null) {
            return task.getNextExecution();
        }
        return task.getLastExecution();
    }

    private LocalDateTime snapToAxis(ViewMode mode, LocalDateTime time) {
        if (time == null) {
            return null;
        }
        if (mode != ViewMode.TWENTY_FOUR_HOURS) {
            return time;
        }

        int seconds = time.getSecond();
        if (seconds <= 30 && time.getMinute() == 0) {
            return time.withSecond(0).withNano(0);
        }
        if (seconds >= 30 && time.getMinute() == 59) {
            return time.plusMinutes(1).withMinute(0).withSecond(0).withNano(0);
        }
        return time;
    }

    private void loadAccounts() {
        List<DTOProfiles> profiles = ServProfiles.getServices().getProfiles();
        
        // Filter only enabled profiles and sort alphabetically
        List<DTOProfiles> sortedProfiles = profiles.stream()
            .filter(p -> p.getEnabled() != null && p.getEnabled()) // Only enabled accounts
            .sorted(Comparator.comparing(DTOProfiles::getName))
            .collect(Collectors.toList());
        
        profileTasksMap.clear();
        
        // Counter for completed loading operations
        final int[] loadedCount = {0};
        final int totalProfiles = sortedProfiles.size();
        
        // Load tasks for all profiles asynchronously
        for (DTOProfiles profile : sortedProfiles) {
            loadAccountTasks(profile, () -> {
                loadedCount[0]++;
                // When all profiles are loaded, build UI in sorted order
                if (loadedCount[0] == totalProfiles) {
                    Platform.runLater(() -> {
                        lastLoadedProfiles = new ArrayList<>(sortedProfiles);
                        rebuildUI(sortedProfiles);
                    });
                }
            });
        }
    }
    
    private void rebuildUI(List<DTOProfiles> sortedProfiles) {
        vboxAccounts.getChildren().clear();
        profileStaminaLabels.clear();
        lastLoadedProfiles = new ArrayList<>(sortedProfiles);
        
        // Use the dynamically calculated available width
        double uniformWidth = availableWidth;
        
        // Build UI in alphabetical order with uniform width
        for (DTOProfiles profile : sortedProfiles) {
            List<TaskManagerAux> tasks = profileTasksMap.get(profile.getId());
            if (tasks != null) {
                List<TaskManagerAux> visibleTasks = tasks.stream()
                    .filter(this::matchesTaskFilter)
                    .filter(task -> showInactiveTasks || !isInactiveTask(task))
                    .collect(Collectors.toList());

                if (!taskFilter.isEmpty() && visibleTasks.isEmpty()) {
                    continue;
                }

                createAccountRow(profile, visibleTasks, uniformWidth);
            }
        }
    }
    // Optional: Stop the timer when closing
    public void stopAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
        StaminaService.getServices().removeStaminaChangeListener(this);
    }
    
    /**
     * Calculates tracks (lanes) for overlapping tasks.
     * Tasks that overlap in time are distributed across different tracks.
     * Only considers tasks in the visible time window!
     */
    private List<List<TaskManagerAux>> calculateTracks(List<TaskManagerAux> tasks, LocalDateTime now) {
        List<List<TaskManagerAux>> tracks = new ArrayList<>();
        
    // Filter tasks by visible time window and sort by start time
    List<TaskManagerAux> sortedTasks = tasks.stream()
        .filter(this::shouldDisplayInTimeline)
        .filter(t -> {
            LocalDateTime displayTime = resolveDisplayTime(t, now);
            return isWithinViewWindow(displayTime, now);
        })
        .sorted(Comparator.comparing(t -> {
            LocalDateTime displayTime = resolveDisplayTime(t, now);
            return displayTime != null ? displayTime : now;
        }))
        .collect(Collectors.toList());        for (TaskManagerAux task : sortedTasks) {
            LocalDateTime taskStart = resolveDisplayTime(task, now);
            if (taskStart == null) {
                continue;
            }
            LocalDateTime taskEnd = taskStart.plusMinutes(5); // 5-minute duration (matches bar width)
            
            // Find a free track
            boolean placed = false;
            for (List<TaskManagerAux> track : tracks) {
                // Check if this track is free (no overlap)
                boolean canPlace = true;
                for (TaskManagerAux existingTask : track) {
                    LocalDateTime existingStart = resolveDisplayTime(existingTask, now);
                    if (existingStart == null) {
                        continue;
                    }
                    LocalDateTime existingEnd = existingStart.plusMinutes(5); // 5-minute duration
                    
                    // Check for overlap
                    if (!(taskEnd.isBefore(existingStart) || taskStart.isAfter(existingEnd))) {
                        canPlace = false;
                        break;
                    }
                }
                
                if (canPlace) {
                    track.add(task);
                    placed = true;
                    break;
                }
            }
            
            // If no free track found, create new one
            if (!placed) {
                List<TaskManagerAux> newTrack = new ArrayList<>();
                newTrack.add(task);
                tracks.add(newTrack);
            }
        }
        
        return tracks;
    }

    private void loadAccountTasks(DTOProfiles profile, Runnable onComplete) {
        taskManagerActionController.loadDailyTaskStatus(profile.getId(), (List<DTODailyTaskStatus> statuses) -> {
            // Get existing tasks for this profile (if available)
            List<TaskManagerAux> existingTasks = profileTasksMap.get(profile.getId());
            Map<Integer, TaskManagerAux> existingTaskMap = new HashMap<>();
            if (existingTasks != null) {
                existingTasks.forEach(t -> existingTaskMap.put(t.getTaskEnum().getId(), t));
            }
            
            List<TaskManagerAux> tasks = Arrays.stream(TpDailyTaskEnum.values()).map(task -> {
                DTODailyTaskStatus s = statuses.stream()
                    .filter(st -> st.getIdTpDailyTask() == task.getId())
                    .findFirst().orElse(null);

                if (s == null) {
                    // Check if existing TaskManagerAux object is available
                    TaskManagerAux existing = existingTaskMap.get(task.getId());
                    if (existing != null) {
                        return existing; // Reuse
                    }
                    return new TaskManagerAux(task.getName(), null, null, task, profile.getId(), Long.MAX_VALUE, false, false, false);
                }

                long diffInSeconds = Long.MAX_VALUE;
                boolean ready = false;
                if (s.getNextSchedule() != null) {
                    diffInSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), s.getNextSchedule());
                    if (diffInSeconds <= 0) {
                        ready = true;
                        diffInSeconds = 0;
                    }
                }

                boolean scheduled = Optional.ofNullable(ServScheduler.getServices().getQueueManager().getQueue(profile.getId()))
                    .map(q -> q.isTaskScheduled(task)).orElse(false);

                // Check if task is currently executing via ServTaskManager
                boolean isExecuting = false;
                cl.camodev.wosbot.ot.DTOTaskState taskState = cl.camodev.wosbot.serv.impl.ServTaskManager.getInstance()
                    .getTaskState(profile.getId(), task.getId());
                if (taskState != null) {
                    isExecuting = taskState.isExecuting();
                }

                // Try to reuse and update existing TaskManagerAux object
                TaskManagerAux existingTask = existingTaskMap.get(task.getId());
                if (existingTask != null) {
                    // Update only the values, but keep the object (important for live updates!)
                    existingTask.setLastExecution(s.getLastExecution());
                    existingTask.setNextExecution(s.getNextSchedule());
                    existingTask.setNearestMinutesUntilExecution(diffInSeconds);
                    existingTask.setHasReadyTask(ready);
                    existingTask.setScheduled(scheduled);
                    // Only update isExecuting if it has changed (listener has priority!)
                    if (existingTask.isExecuting() != isExecuting) {
                        existingTask.setExecuting(isExecuting);
                    }
                    return existingTask;
                }

                // Create new only if no existing object available
                return new TaskManagerAux(task.getName(), s.getLastExecution(), s.getNextSchedule(), task, profile.getId(), diffInSeconds, ready, scheduled, isExecuting);
            })
            .filter(this::shouldDisplayInTimeline)
            .collect(Collectors.toList());

            // Store tasks for this profile
            profileTasksMap.put(profile.getId(), tasks);
            
            // Call callback when done
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private void createAccountRow(DTOProfiles profile, List<TaskManagerAux> tasks, double uniformWidth) {
        LocalDateTime now = LocalDateTime.now();
        
        // Calculate required tracks (lanes) for overlapping tasks
        List<List<TaskManagerAux>> tracks = calculateTracks(tasks, now);
        
        // If no tasks present, show minimal height (32px)
        // Otherwise calculate height based on number of tracks
        int trackCount = tracks.isEmpty() ? 0 : tracks.size();
    int calculatedHeight = trackCount == 0 ? 48 : (24 * trackCount + 12);
    int rowHeight = Math.max(48, calculatedHeight); // Maintain minimum height so labels never get clipped
        
        // HBox for Account row with fixed height
        HBox accountRow = new HBox(8);
    accountRow.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        accountRow.setPrefHeight(rowHeight);
        accountRow.setMinHeight(rowHeight);
        accountRow.setMaxHeight(rowHeight);
        accountRow.setStyle("-fx-padding: 0; -fx-spacing: 8;"); // No padding, only spacing between elements

    Label lblAccount = new Label(profile.getName());
    lblAccount.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 12; -fx-font-weight: bold;");
    lblAccount.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

    int currentStamina = StaminaService.getServices().getCurrentStamina(profile.getId());
    Label staminaLabel = new Label(formatStaminaValue(currentStamina));
    staminaLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 10;");
    staminaLabel.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

    VBox accountInfo = new VBox(0);
    accountInfo.setAlignment(javafx.geometry.Pos.TOP_LEFT);
    accountInfo.setPrefWidth(ACCOUNT_LABEL_WIDTH);
    accountInfo.setMinWidth(ACCOUNT_LABEL_WIDTH);
    accountInfo.setMaxWidth(ACCOUNT_LABEL_WIDTH);
    accountInfo.getChildren().addAll(lblAccount, staminaLabel);
    accountInfo.setSpacing(0);

    profileStaminaLabels.put(profile.getId(), staminaLabel);
    accountRow.getChildren().add(accountInfo);
        
        // Add spacer to align with time axis header (Local/UTC label column)
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        spacer.setPrefWidth(TIME_AXIS_LABEL_WIDTH);
        spacer.setMinWidth(TIME_AXIS_LABEL_WIDTH);
        spacer.setMaxWidth(TIME_AXIS_LABEL_WIDTH);
        accountRow.getChildren().add(spacer);
        
        // Use uniform width for all accounts
        double timelinePaneWidth = uniformWidth;
        
        // Timeline area as Pane for precise positioning
        javafx.scene.layout.Pane timelinePane = new javafx.scene.layout.Pane();
        timelinePane.setPrefWidth(timelinePaneWidth);
        timelinePane.setMinWidth(timelinePaneWidth);
        timelinePane.setMaxWidth(timelinePaneWidth);
        timelinePane.setPrefHeight(rowHeight);
        timelinePane.setMinHeight(rowHeight);
        timelinePane.setMaxHeight(rowHeight);
        timelinePane.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #3a3a3a; -fx-border-width: 1;");

        TimelineMetrics metrics = drawTimelineBackground(timelinePane, viewMode, uniformWidth, rowHeight, now);

        // Position task bars absolutely with track support
        for (int trackIndex = 0; trackIndex < tracks.size(); trackIndex++) {
            List<TaskManagerAux> trackTasks = tracks.get(trackIndex);
            
            for (TaskManagerAux task : trackTasks) {
                LocalDateTime scheduledTime = task.getNextExecution();
                boolean isCurrentlyExecuting = task.isExecuting();
                boolean isScheduled = task.isScheduled();
                boolean isReady = task.hasReadyTask();
                boolean inactive = isInactiveTask(task);
                
                // Determine the timestamp used for positioning within the timeline.
                LocalDateTime displayTime;
                if (isCurrentlyExecuting) {
                    displayTime = now;
                } else if (scheduledTime != null) {
                    displayTime = scheduledTime;
                } else if (task.getLastExecution() != null) {
                    displayTime = task.getLastExecution();
                } else {
                    continue; // No usable timestamp
                }

                // Calculate abbreviation and required text width
                String taskAbbreviation = getTaskAbbreviation(task.getTaskName());
                double textWidth = taskAbbreviation.length() * 9 + 12;

                if (!isWithinViewWindow(displayTime, now)) {
                    continue;
                }

                LocalDateTime alignedTime = snapToAxis(viewMode, displayTime);
                if (alignedTime == null) {
                    continue;
                }
                double minWidth = Math.max(0d, metrics.widthBetween(alignedTime, viewMode.getMinBarWidthMinutes()));
                double barWidth = Math.max(textWidth, Math.max(12, minWidth));
                double xPosition = metrics.toX(alignedTime);
                double maxX = timelinePaneWidth - barWidth;
                if (maxX < 0) {
                    maxX = 0;
                }
                xPosition = Math.max(0, Math.min(xPosition, maxX));

                // StackPane for bar + label
                javafx.scene.layout.StackPane taskStack = new javafx.scene.layout.StackPane();
                taskStack.setLayoutX(xPosition);
                // Y-position based on track: Track 0 = Y:4, Track 1 = Y:28, Track 2 = Y:52, etc.
                taskStack.setLayoutY(4 + (trackIndex * 24));
                
                Rectangle rect = new Rectangle(barWidth, 18); // Reduce height to 18px
                rect.setArcWidth(4);
                rect.setArcHeight(4);

                // Color and highlighting - use task status directly
                String fillColor;
                String strokeColor;
                double strokeWidth;
                String status;
                
                // Use current task status (updated live via listener)
                if (isCurrentlyExecuting) {
                    fillColor = "#FF9800";
                    strokeColor = "#fff200";
                    strokeWidth = 2.5;
                    status = "RUNNING";
                } else if (isScheduled && isReady) {
                    fillColor = "#4CAF50";
                    strokeColor = "#2e7d32";
                    strokeWidth = 1.5;
                    status = "READY";
                } else if (inactive) {
                    fillColor = "#616161";
                    strokeColor = "#8a8a8a";
                    strokeWidth = 1;
                    status = isReady ? "INACTIVE (READY)" : "INACTIVE";
                } else {
                    fillColor = "#47d9ff";
                    strokeColor = "#0288d1";
                    strokeWidth = 1;
                    status = "SCHEDULED";
                }
                
                rect.setFill(javafx.scene.paint.Color.web(fillColor));
                rect.setStroke(javafx.scene.paint.Color.web(strokeColor));
                rect.setStrokeWidth(strokeWidth);

                // Task name as abbreviation on bar (dynamically adjusted width)
                Label taskLabel = new Label(taskAbbreviation);
                taskLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 8; -fx-font-weight: bold;");
                taskLabel.setMaxWidth(barWidth - 4);

                // Tooltip with complete information and click hint
                LocalDateTime tooltipTime = scheduledTime != null
                    ? scheduledTime
                    : (task.getLastExecution() != null ? task.getLastExecution() : displayTime);
                String timeLabel = isCurrentlyExecuting ? "Running since" : "Time";
                String timeDisplay = tooltipTime != null
                    ? String.format("%02d:%02d", tooltipTime.getHour(), tooltipTime.getMinute())
                    : "N/A";
                String actionHint = !isCurrentlyExecuting ? "\n\n[Click to Execute]" : "";
                String tooltipText = String.format("%s\n%s: %s\nStatus: %s%s",
                    task.getTaskName(),
                    timeLabel,
                    timeDisplay,
                    status,
                    actionHint);
                javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(tooltipText);
                // Configure tooltip to show faster and stay visible
                tooltip.setShowDelay(javafx.util.Duration.millis(200)); // Show after 200ms
                tooltip.setHideDelay(javafx.util.Duration.INDEFINITE); // Never auto-hide
                tooltip.setShowDuration(javafx.util.Duration.INDEFINITE); // Keep showing while mouse is over
                tooltip.setAutoHide(false); // Don't auto-hide on mouse movement
                javafx.scene.control.Tooltip.install(taskStack, tooltip);
                
                // Mouse handlers for manual tooltip control
                final javafx.scene.control.Tooltip tooltipRef = tooltip;
                taskStack.setOnMouseEntered(event -> {
                    // Show tooltip when mouse enters
                    javafx.geometry.Bounds bounds = taskStack.localToScreen(taskStack.getBoundsInLocal());
                    if (bounds != null) {
                        tooltipRef.show(taskStack, bounds.getMinX(), bounds.getMaxY() + 5);
                    }
                });
                
                taskStack.setOnMouseExited(event -> {
                    // Hide tooltip only when mouse completely leaves the element
                    tooltipRef.hide();
                });
                
                // Click handler: Execute task immediately on click
                taskStack.setOnMouseClicked(event -> {
                    if (!task.isExecuting()) {
                        taskManagerActionController.executeTaskDirectly(task);
                    }
                });
                
                // Change cursor to indicate clickability (only if not already executing)
                if (!task.isExecuting()) {
                    taskStack.setStyle("-fx-cursor: hand;");
                }

                taskStack.getChildren().addAll(rect, taskLabel);
                timelinePane.getChildren().add(taskStack);
            } // End for TaskManagerAux
        } // End for trackIndex

        accountRow.getChildren().add(timelinePane);
        vboxAccounts.getChildren().add(accountRow);
    }

    @Override
    public void onStaminaChanged(Long profileId, int newStamina) {
        if (profileId == null) {
            return;
        }

        Platform.runLater(() -> {
            Label staminaLabel = profileStaminaLabels.get(profileId);
            if (staminaLabel != null) {
                staminaLabel.setText(formatStaminaValue(newStamina));
            }
        });
    }

    private boolean isWithinViewWindow(LocalDateTime time, LocalDateTime now) {
        if (time == null) {
            return false;
        }

        LocalDateTime viewStart = resolveViewStart(viewMode, now);
        LocalDateTime viewEnd = resolveViewEnd(viewMode, now);
        boolean afterStart = !time.isBefore(viewStart);
        boolean beforeEnd = !time.isAfter(viewEnd);
        return afterStart && beforeEnd;
    }

    private String formatStaminaValue(int stamina) {
        return String.format("Stamina: %d", Math.max(0, stamina));
    }
    
    /**
     * Creates abbreviations for task names to display them on narrow bars
     */
    private boolean shouldDisplayInTimeline(TaskManagerAux task) {
        if (task == null) {
            return false;
        }
        return task.isExecuting()
            || task.hasReadyTask()
            || task.isScheduled()
            || task.getNextExecution() != null
            || task.getLastExecution() != null;
    }

    private boolean isInactiveTask(TaskManagerAux task) {
        if (task == null) {
            return true;
        }
        if (task.isExecuting()) {
            return false;
        }
        return !task.isScheduled();
    }

    private String getTaskAbbreviation(String taskName) {
        // Mapping for specific task names to abbreviations
        return switch (taskName) {
            case "Hero Recruitment" -> "Hero";
            case "Nomadic Merchant" -> "Nomad";
            case "War Academy Shards" -> "WAcad";
            case "Crystal Laboratory" -> "CrLab";
            case "VIP Points" -> "VIP";
            case "Pet Adventure" -> "PetAdv";
            case "Exploration Chest" -> "ExpCh";
            case "Trek Supplies" -> "TrekS";
            case "Life Essence" -> "LifeE";
            case "Life Essence Caring" -> "LfCar";
            case "Labyrinth" -> "Laby";
            case "Tundra Trek Automation" -> "TrekA";
            case "Bank" -> "Bank";
            case "Arena" -> "Arena";
            case "Mail Rewards" -> "Mail";
            case "Daily Missions" -> "Daily";
            case "Storehouse Chest" -> "Store";
            case "Intel" -> "Intel";
            case "Expert Agnes Intel" -> "Agnes";
            case "Expert Romulus Tag" -> "RomT";
            case "Expert Romulus Troops" -> "RomTr";
            case "Expert Skill Training" -> "SkTrn";
            case "Alliance Autojoin" -> "AlJoin";
            case "Alliance Tech" -> "AlTch";
            case "Alliance Pet Treasure" -> "AlPet";
            case "Alliance Chests" -> "AlCh";
            case "Alliance Triumph" -> "AlTri";
            case "Alliance Mobilization" -> "AlMob";
            case "Alliance Shop" -> "AlShp";
            case "Alliance Championship" -> "AlChmp";
            case "Bear Trap Event" -> "Bear";
            case "Pet Skill Stamina" -> "PetSt";
            case "Pet Skill Food" -> "PetFd";
            case "Pet Skill Treasure" -> "PetTr";
            case "Pet Skill Gathering" -> "PetGa";
            case "Training Infantry" -> "TrnInf";
            case "Training Lancer" -> "TrnLnc";
            case "Training Marksman" -> "TrnMrk";
            case "City Upgrade Furnace" -> "Furn";
            case "City Survivors" -> "Surv";
            case "Shop Mystery" -> "Myst";
            case "Chief Order: Rush Job" -> "CoRush";
            case "Chief Order: Urgent Mobilization" -> "CoMob";
            case "Chief Order: Productivity Day" -> "CoProd";
            case "Initialize" -> "Init";
            case "Gather Speed Boost" -> "GthSpd";
            case "Gather Resources" -> "GthRes";
            case "Gather Meat" -> "GthMt";
            case "Gather Wood" -> "GthWd";
            case "Gather Coal" -> "GthCl";
            case "Gather Iron" -> "GthIr";
            case "Tundra Truck Event" -> "TrkEv";
            case "Hero Mission Event" -> "HeroEv";
            case "Mercenary Event" -> "Merc";
            case "Journey of Light Event" -> "JoL";
            case "Polar Terror Hunting" -> "Polar";
            case "Myriad Bazaar Event" -> "Bazaar";
            default -> {
                // Fallback: use the first few letters or the initial of each word
                String[] words = taskName.split(" ");
                if (words.length > 1) {
                    StringBuilder abbr = new StringBuilder();
                    for (String word : words) {
                        if (!word.isEmpty()) {
                            abbr.append(word.charAt(0));
                        }
                    }
                    yield abbr.toString();
                } else {
                    yield taskName.length() > 5 ? taskName.substring(0, 5) : taskName;
                }
            }
        };
    }

    private boolean matchesTaskFilter(TaskManagerAux task) {
        if (taskFilter.isEmpty()) {
            return true;
        }
        String name = task.getTaskName();
        return name != null && name.toLowerCase(Locale.ENGLISH).contains(taskFilter);
    }
}
