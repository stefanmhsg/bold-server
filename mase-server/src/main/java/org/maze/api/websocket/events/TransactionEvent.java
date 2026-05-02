package org.maze.api.websocket.events;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.rdf4j.model.Statement;

public class TransactionEvent extends MazeEvent {

    public String trigger;
    public String status;
    public String agent;
    public String graph;
    public String requestBody;
    public String error;
    public String traceMode;
    public long transactionId;
    public int ruleCount;
    public long startedAt;
    public long finishedAt;

    public List<RdfTriple> mergeAdded = new ArrayList<>();
    public List<RdfTriple> mergeRemoved = new ArrayList<>();
    public List<RuleChange> rules = new ArrayList<>();

    public TransactionEvent(String trigger) {
        this.type = "TRANSACTION";
        this.trigger = trigger;
        this.startedAt = System.currentTimeMillis();
    }

    @Override
    public void processStatement(Statement st) {
        // Not used for transaction events
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    public static class RdfTriple {
        public String subject;
        public String predicate;
        public String object;
        public String context;

        public RdfTriple() {
        }

        public RdfTriple(String subject, String predicate, String object, String context) {
            this.subject = subject;
            this.predicate = predicate;
            this.object = object;
            this.context = context;
        }
    }

    public static class RuleChange {
        public String ruleName;
        public String error;
        public List<RdfTriple> added = new ArrayList<>();
        public List<RdfTriple> removed = new ArrayList<>();

        public RuleChange() {
        }

        public RuleChange(String ruleName) {
            this.ruleName = ruleName;
        }
    }
}
