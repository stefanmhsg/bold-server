package org.maze.api.websocket;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

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

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        log.info("WebSocket connected: {}", session.getId());
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
            return;
        }
        
        log.debug("[BROADCASTER] Broadcasting: {}", message);
        
        for (Session session : sessions) {
            if (session.isOpen()) {
                // Fire and forget - async
                session.getAsyncRemote().sendText(message);
            }
        }
    }
}
