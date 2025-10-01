package cl.camodev.wosbot.web.streaming;

import cl.camodev.wosbot.ot.DTOBotState;
import cl.camodev.wosbot.serv.IBotStateListener;
import cl.camodev.wosbot.serv.impl.ServScheduler;
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

import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service responsible for streaming bot state changes via Server-Sent Events (SSE).
 */
@Service
@RestController
@RequestMapping("/api/bot/state")
public class BotStateStreamingService implements IBotStateListener {

    private static final Logger logger = LoggerFactory.getLogger(BotStateStreamingService.class);

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Gson gson;

    public BotStateStreamingService() {
        this.gson = JsonSerializerConfig.getGson();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamBotState() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        
        // Send current bot state to new client
        sendCurrentBotState(emitter);
        
        logger.info("New client connected to bot state stream. Total clients: {}", emitters.size());
        return emitter;
    }

    private void sendCurrentBotState(SseEmitter emitter) {
        try {
            cl.camodev.wosbot.serv.task.TaskQueueManager queueManager = 
                ServScheduler.getServices().getQueueManager();
            boolean hasRunningQueues = queueManager.hasRunningQueues();
            
            DTOBotState currentState = new DTOBotState();
            currentState.setRunning(hasRunningQueues);
            currentState.setPaused(false);
            currentState.setActionTime(LocalDateTime.now());
            
            sendBotStateToClient(emitter, currentState);
        } catch (Exception e) {
            logger.error("Error sending current bot state: {}", e.getMessage());
        }
    }

    @Override
    public void onBotStateChange(DTOBotState botState) {
        logger.debug("Bot state changed: running={}, paused={}", 
            botState.getRunning(), botState.getPaused());
        
        // Broadcast to all connected clients
        for (SseEmitter emitter : emitters) {
            sendBotStateToClient(emitter, botState);
        }
    }

    private void sendBotStateToClient(SseEmitter emitter, DTOBotState botState) {
        try {
            String json = gson.toJson(botState);
            emitter.send(SseEmitter.event().name("botState").data(json));
        } catch (Exception e) {
            logger.error("Error sending bot state to client: {}", e.getMessage());
            emitters.remove(emitter);
        }
    }

    public void shutdown() {
        emitters.clear();
        logger.info("Bot state streaming service shut down");
    }
}
