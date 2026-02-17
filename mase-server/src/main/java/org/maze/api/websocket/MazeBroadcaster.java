package org.maze.api.websocket;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServerEndpoint("/ws")
public class MazeBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(MazeBroadcaster.class);
    private static final Set<Session> sessions = new CopyOnWriteArraySet<>();
    private static final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final Deque<String> recentMessages = new ArrayDeque<>();
    private static final int MAX_RECENT_MESSAGES = 50;

    static {
        // Heartbeat every 20 seconds to prevent idle timeouts
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            broadcast("{\"type\":\"PING\"}");
        }, 20, 20, TimeUnit.SECONDS);
    }

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        log.info("WebSocket connected: {}", session.getId());
        replayRecentMessages(session);
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        log.info("WebSocket disconnected: {}", session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        log.error("WebSocket error on session {}", session.getId(), throwable);
        sessions.remove(session);
    }

    public static void broadcast(String message) {
        if (sessions.isEmpty()) {
            rememberMessage(message);
            return;
        }

        rememberMessage(message);
        
        log.info("[BROADCASTER] Broadcasting: {}", message);
        
        for (Session session : sessions) {
            if (session.isOpen()) {
                // Fire and forget - async
                session.getAsyncRemote().sendText(message);
            }
        }
    }

    private static synchronized void rememberMessage(String message) {
        if ("{\"type\":\"PING\"}".equals(message)) {
            return;
        }

        recentMessages.addLast(message);
        while (recentMessages.size() > MAX_RECENT_MESSAGES) {
            recentMessages.removeFirst();
        }
    }

    private static synchronized void replayRecentMessages(Session session) {
        for (String message : recentMessages) {
            if (session.isOpen()) {
                session.getAsyncRemote().sendText(message);
            }
        }
    }
}
