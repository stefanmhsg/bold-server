package org.maze.examples;

import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.http.A2ACardResolver;
import io.a2a.client.http.A2AHttpClient;
import io.a2a.client.http.JdkA2AHttpClient;
import io.a2a.client.transport.rest.RestTransport;
import io.a2a.client.transport.rest.RestTransportConfig;
import io.a2a.client.transport.spi.interceptors.ClientCallContext;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Artifact;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TextPart;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class A2AKeySeekerAgent {

    private static final String AGENT_NAME = "a2a-key-seeker-min";
    private static final String DEFAULT_KEYHOLDER_BASE_URL = "http://127.0.0.1:8095";
    private static final Pattern DYN_KEYVALUE_PATTERN = Pattern.compile("dyn:keyValue\\s+\"([^\"]+)\"");

    public static void main(String[] args) {
        new A2AKeySeekerAgent().run();
    }

    public void run() {
        String baseUrl = resolveKeyHolderBaseUrl();
        logA2A("Starting minimal A2A flow");
        logA2A("Resolving AgentCard from " + baseUrl + "/.well-known/agent-card.json");

        A2AHttpClient sdkHttpClient = new JdkA2AHttpClient();
        AgentCard card;
        try {
            card = new A2ACardResolver(sdkHttpClient, baseUrl).getAgentCard();
        } catch (Exception ex) {
            logA2A("AgentCard discovery failed: " + ex.getMessage());
            return;
        }

        if (card == null) {
            logA2A("AgentCard discovery returned null");
            return;
        }

        logA2A("AgentCard resolved");
        logA2A("Using SDK URL: " + card.url());

        RestTransportConfig transportConfig = new RestTransportConfig(sdkHttpClient);
        ClientCallContext callContext = new ClientCallContext(Map.of(), Map.of());
        AtomicReference<String> keyRef = new AtomicReference<>();

        BiConsumer<ClientEvent, AgentCard> consumer = (event, agentCard) -> {
            String eventType = event == null ? "null" : event.getClass().getSimpleName();
            logA2A("Received SDK event: " + eventType);
            extractKeyValueFromEvent(event).ifPresent(value -> {
                keyRef.set(value);
                logA2A("Extracted key value: " + value);
            });
        };

        Message request = new Message(
                Message.Role.USER,
                List.of(new TextPart("provide_red_key")),
                "msg-" + UUID.randomUUID(),
                null,
                null,
                List.of(),
                Map.of(),
                List.of()
        );

        Client sdkClient = null;
        try {
            sdkClient = Client.builder(card)
                    .withTransport(RestTransport.class, transportConfig)
                    .build();

            logA2A("Sending message: provide_red_key");
            sdkClient.sendMessage(request, List.of(consumer), throwable -> {
                String message = throwable == null ? "unknown" : throwable.getMessage();
                logA2A("SDK stream error: " + message);
            }, callContext);
            logA2A("A2A sendMessage completed");
        } catch (Exception ex) {
            logA2A("A2A SDK request failed: " + ex.getMessage());
            return;
        } finally {
            if (sdkClient != null) {
                sdkClient.close();
            }
        }

        if (keyRef.get() == null) {
            logA2A("No key value found in response");
        } else {
            logA2A("SUCCESS: key value received via SDK: " + keyRef.get());
        }
    }

    private String resolveKeyHolderBaseUrl() {
        String env = System.getenv("KEYHOLDER_BASE_URL");
        if (env == null || env.isBlank()) {
            return DEFAULT_KEYHOLDER_BASE_URL;
        }
        return env.trim();
    }

    private Optional<String> extractKeyValueFromEvent(ClientEvent event) {
        if (event instanceof MessageEvent messageEvent) {
            Optional<String> fromMessage = extractKeyValueFromMessage(messageEvent.getMessage());
            if (fromMessage.isPresent()) {
                return fromMessage;
            }
        }

        if (event instanceof TaskEvent taskEvent) {
            Optional<String> fromTask = extractKeyValueFromTask(taskEvent.getTask());
            if (fromTask.isPresent()) {
                return fromTask;
            }
        }

        return Optional.empty();
    }

    private Optional<String> extractKeyValueFromTask(Task task) {
        if (task == null) {
            return Optional.empty();
        }

        if (task.getHistory() != null) {
            for (Message historyMessage : task.getHistory()) {
                Optional<String> parsed = extractKeyValueFromMessage(historyMessage);
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
        }

        if (task.getArtifacts() != null) {
            for (Artifact artifact : task.getArtifacts()) {
                for (Part<?> part : artifact.parts()) {
                    if (part instanceof TextPart textPart) {
                        Optional<String> parsed = extractKeyValue(textPart.getText());
                        if (parsed.isPresent()) {
                            return parsed;
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private Optional<String> extractKeyValueFromMessage(Message message) {
        if (message == null || message.getParts() == null) {
            return Optional.empty();
        }

        for (Part<?> part : message.getParts()) {
            if (part instanceof TextPart textPart) {
                Optional<String> parsed = extractKeyValue(textPart.getText());
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
        }

        return Optional.empty();
    }

    private Optional<String> extractKeyValue(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = DYN_KEYVALUE_PATTERN.matcher(body);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }

        if (body.contains("redkey")) {
            return Optional.of("redkey");
        }

        return Optional.empty();
    }

    private void logA2A(String details) {
        System.out.println("[" + AGENT_NAME + " A2A] " + details);
    }
}
