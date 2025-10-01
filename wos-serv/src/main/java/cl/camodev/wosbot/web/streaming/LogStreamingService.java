package cl.camodev.wosbot.web.streaming;

import cl.camodev.wosbot.console.list.ILogListener;
import cl.camodev.wosbot.ot.DTOLogMessage;
import cl.camodev.wosbot.web.config.JsonSerializerConfig;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service responsible for streaming logs to web clients via Server-Sent Events (SSE).
 */
@Service
@RestController
@RequestMapping("/logs")
public class LogStreamingService implements ILogListener {

    private static final Logger logger = LoggerFactory.getLogger(LogStreamingService.class);
    private static final int MAX_LOG_HISTORY = 1000;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Queue<DTOLogMessage> logHistory = new ConcurrentLinkedQueue<>();
    private final Gson gson;

    public LogStreamingService() {
        this.gson = JsonSerializerConfig.getGson();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        
        // Send existing log history to new client
        for (DTOLogMessage log : logHistory) {
            sendLogToClient(emitter, log);
        }
        
        logger.info("New client connected to log stream. Total clients: {}", emitters.size());
        return emitter;
    }

    @Override
    public void onLogReceived(DTOLogMessage message) {
        logger.debug("Received log: {} - {}", message.getSeverity(), message.getMessage());
        
        // Add to history (with size limit)
        logHistory.offer(message);
        while (logHistory.size() > MAX_LOG_HISTORY) {
            logHistory.poll();
        }

        // Broadcast to all connected clients
        for (SseEmitter emitter : emitters) {
            sendLogToClient(emitter, message);
        }
    }

    private void sendLogToClient(SseEmitter emitter, DTOLogMessage log) {
        try {
            String json = gson.toJson(log);
            emitter.send(SseEmitter.event().name("log").data(json));
        } catch (Exception e) {
            logger.error("Error sending log to client: {}", e.getMessage());
            emitters.remove(emitter);
        }
    }

    public void shutdown() {
        emitters.clear();
        logHistory.clear();
        logger.info("Log streaming service shut down");
    }
}
