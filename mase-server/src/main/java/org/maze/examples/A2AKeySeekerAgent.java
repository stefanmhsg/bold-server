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
import io.a2a.spec.AgentSkill;
import io.a2a.spec.Artifact;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TextPart;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class A2AKeySeekerAgent {

    private static final String AGENT_NAME = "a2a-key-seeker-min";
    private static final String DEFAULT_KEYHOLDER_BASE_URL = "http://127.0.0.1:8095";

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
            ex.printStackTrace(System.out);
            return;
        }

        if (card == null) {
            logA2A("AgentCard discovery returned null");
            return;
        }

        logA2A("AgentCard resolved");
        printAgentCard(card);

        String requestedSkill = resolveSkillFromCard(card);
        if (requestedSkill == null || requestedSkill.isBlank()) {
            logA2A("No usable skill found in AgentCard");
            return;
        }

        logA2A("Discovered skill from AgentCard: " + requestedSkill);

        RestTransportConfig transportConfig = new RestTransportConfig(sdkHttpClient);
        ClientCallContext callContext = new ClientCallContext(Map.of(), Map.of());

        BiConsumer<ClientEvent, AgentCard> consumer = (event, agentCard) -> {
            logA2A("Received SDK event");
            printEvent(event);
        };

        Message request = new Message(
                Message.Role.USER,
                List.of(new TextPart(requestedSkill)),
                "msg-" + UUID.randomUUID(),
                null,
                null,
                List.of(),
                Map.of(),
                List.of()
        );

        printOutgoingMessage(request);

        Client sdkClient = null;
        try {
            sdkClient = Client.builder(card)
                    .withTransport(RestTransport.class, transportConfig)
                    .build();

            logA2A("Sending message via SDK");
            sdkClient.sendMessage(request, List.of(consumer), throwable -> {
                String message = throwable == null ? "unknown" : throwable.getMessage();
                logA2A("SDK stream error: " + message);
                if (throwable != null) {
                    throwable.printStackTrace(System.out);
                }
            }, callContext);
            logA2A("A2A sendMessage completed");
        } catch (Exception ex) {
            logA2A("A2A SDK request failed: " + ex.getMessage());
            ex.printStackTrace(System.out);
        } finally {
            if (sdkClient != null) {
                sdkClient.close();
            }
        }
    }

    private String resolveKeyHolderBaseUrl() {
        String env = System.getenv("KEYHOLDER_BASE_URL");
        if (env == null || env.isBlank()) {
            return DEFAULT_KEYHOLDER_BASE_URL;
        }
        return env.trim();
    }

    private String resolveSkillFromCard(AgentCard card) {
        if (card == null || card.skills() == null || card.skills().isEmpty()) {
            return null;
        }

        if (card.skills().size() == 1) {
            AgentSkill onlySkill = card.skills().get(0);
            return onlySkill == null ? null : onlySkill.id();
        }

        for (AgentSkill skill : card.skills()) {
            if (skill == null || skill.id() == null) {
                continue;
            }
            if ("provide_red_key".equalsIgnoreCase(skill.id())) {
                return skill.id();
            }
        }

        for (AgentSkill skill : card.skills()) {
            if (skill != null && skill.id() != null && !skill.id().isBlank()) {
                return skill.id();
            }
        }

        return null;
    }

    private void printAgentCard(AgentCard card) {
        System.out.println();
        System.out.println("========== AGENT CARD ==========");
        System.out.println(card);

        if (card.skills() != null && !card.skills().isEmpty()) {
            System.out.println("skills:");
            for (AgentSkill skill : card.skills()) {
                System.out.println("  " + skill);
            }
        }

        if (card.additionalInterfaces() != null && !card.additionalInterfaces().isEmpty()) {
            System.out.println("additionalInterfaces:");
            for (Object item : card.additionalInterfaces()) {
                System.out.println("  " + item);
            }
        }

        System.out.println("================================");
        System.out.println();
    }

    private void printOutgoingMessage(Message message) {
        System.out.println();
        System.out.println("======= OUTGOING MESSAGE =======");
        System.out.println(message);

        if (message != null && message.getParts() != null) {
            System.out.println("parts:");
            for (Part<?> part : message.getParts()) {
                System.out.println("  " + part);
                if (part instanceof TextPart textPart) {
                    System.out.println("    text: " + textPart.getText());
                }
            }
        }

        System.out.println("================================");
        System.out.println();
    }

    private void printEvent(ClientEvent event) {
        System.out.println();
        System.out.println("========= SDK EVENT =========");
        System.out.println("event class: " + (event == null ? "null" : event.getClass().getName()));
        System.out.println("event toString:");
        System.out.println(event);

        if (event instanceof MessageEvent messageEvent) {
            System.out.println();
            System.out.println("----- MESSAGE EVENT -----");
            printMessage(messageEvent.getMessage());
        }

        if (event instanceof TaskEvent taskEvent) {
            System.out.println();
            System.out.println("------ TASK EVENT ------");
            printTask(taskEvent.getTask());
        }

        System.out.println("============================");
        System.out.println();
    }

    private void printTask(Task task) {
        if (task == null) {
            System.out.println("task: null");
            return;
        }

        System.out.println("task toString:");
        System.out.println(task);

        System.out.println("task.id: " + task.getId());
        System.out.println("task.contextId: " + task.getContextId());
        System.out.println("task.status: " + task.getStatus());
        System.out.println("task.metadata: " + task.getMetadata());

        if (task.getHistory() != null && !task.getHistory().isEmpty()) {
            System.out.println("task.history:");
            for (int i = 0; i < task.getHistory().size(); i++) {
                System.out.println("  history[" + i + "]:");
                printMessage(task.getHistory().get(i));
            }
        }

        if (task.getArtifacts() != null && !task.getArtifacts().isEmpty()) {
            System.out.println("task.artifacts:");
            for (int i = 0; i < task.getArtifacts().size(); i++) {
                System.out.println("  artifact[" + i + "]:");
                printArtifact(task.getArtifacts().get(i));
            }
        }
    }

    private void printMessage(Message message) {
        if (message == null) {
            System.out.println("message: null");
            return;
        }

        System.out.println("message toString:");
        System.out.println(message);

        System.out.println("message.id: " + message.getMessageId());
        System.out.println("message.role: " + message.getRole());
        System.out.println("message.contextId: " + message.getContextId());
        System.out.println("message.taskId: " + message.getTaskId());
        System.out.println("message.metadata: " + message.getMetadata());

        if (message.getParts() != null && !message.getParts().isEmpty()) {
            System.out.println("message.parts:");
            for (int i = 0; i < message.getParts().size(); i++) {
                Part<?> part = message.getParts().get(i);
                System.out.println("  part[" + i + "]: " + part);
                if (part instanceof TextPart textPart) {
                    System.out.println("    text: " + textPart.getText());
                }
            }
        }
    }

    private void printArtifact(Artifact artifact) {
        if (artifact == null) {
            System.out.println("artifact: null");
            return;
        }

        System.out.println("artifact toString:");
        System.out.println(artifact);

        System.out.println("artifact.id: " + artifact.artifactId());
        System.out.println("artifact.name: " + artifact.name());
        System.out.println("artifact.metadata: " + artifact.metadata());

        if (artifact.parts() != null && !artifact.parts().isEmpty()) {
            System.out.println("artifact.parts:");
            for (int i = 0; i < artifact.parts().size(); i++) {
                Part<?> part = artifact.parts().get(i);
                System.out.println("  part[" + i + "]: " + part);
                if (part instanceof TextPart textPart) {
                    System.out.println("    text: " + textPart.getText());
                }
            }
        }
    }

    private void logA2A(String details) {
        System.out.println("[" + AGENT_NAME + " A2A] " + details);
    }
}