package org.maze.application.tx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.api.websocket.events.TransactionEvent;

public class TransactionTraceContext {

    private static final AtomicLong TRANSACTION_IDS = new AtomicLong();

    private final TransactionEvent event;
    private final TransactionTraceMode mode;

    private Set<TripleKey> mergeBefore = Set.of();
    private Set<TripleKey> currentRuleBefore = Set.of();
    private TransactionEvent.RuleChange currentRuleChange;
    private boolean currentRuleActive;

    private TransactionTraceContext(String trigger, TransactionTraceMode mode) {
        this.mode = mode;
        this.event = new TransactionEvent(trigger);
        this.event.traceMode = mode.wireValue();
        this.event.transactionId = TRANSACTION_IDS.incrementAndGet();
    }

    public static TransactionTraceContext forPost(TransactionTraceMode mode, String agent, String graph, String requestBody) {
        if (!mode.emitsEvents()) {
            return null;
        }

        TransactionTraceContext context = new TransactionTraceContext("POST", mode);
        context.event.agent = agent;
        context.event.graph = graph;
        if (mode.capturesTriples()) {
            context.event.requestBody = requestBody;
        }
        return context;
    }

    public static TransactionTraceContext forStartup(TransactionTraceMode mode) {
        return mode.emitsEvents() ? new TransactionTraceContext("STARTUP", mode) : null;
    }

    public static TransactionTraceContext forReset(TransactionTraceMode mode) {
        return mode.emitsEvents() ? new TransactionTraceContext("RESET", mode) : null;
    }

    public void captureMergeBefore(SailRepositoryConnection connection, String graphIri) {
        if (!mode.capturesTriples()) {
            return;
        }
        this.mergeBefore = snapshot(connection, graphIri);
    }

    public void captureMergeAfter(SailRepositoryConnection connection, String graphIri) {
        if (!mode.capturesTriples()) {
            return;
        }
        Set<TripleKey> after = snapshot(connection, graphIri);
        event.mergeAdded = toTriples(diff(after, mergeBefore));
        event.mergeRemoved = toTriples(diff(mergeBefore, after));
    }

    public void beginRule(String ruleName, SailRepositoryConnection connection) {
        currentRuleActive = true;
        if (!mode.capturesTriples()) {
            return;
        }

        currentRuleChange = new TransactionEvent.RuleChange(ruleName);
        if (mode.capturesTriples()) {
            currentRuleBefore = snapshot(connection, null);
        }
    }

    public void markCurrentRuleError(String error) {
        if (currentRuleChange != null) {
            currentRuleChange.error = error;
        }
    }

    public void endRule(SailRepositoryConnection connection) {
        if (!currentRuleActive) {
            return;
        }

        if (!mode.capturesTriples()) {
            // Summary mode intentionally emits only transaction headers and rule count.
            event.ruleCount++;
        } else if (currentRuleChange != null) {
            Set<TripleKey> after = snapshot(connection, null);
            currentRuleChange.added = toTriples(diff(after, currentRuleBefore));
            currentRuleChange.removed = toTriples(diff(currentRuleBefore, after));
            event.rules.add(currentRuleChange);
            event.ruleCount = event.rules.size();
        }

        currentRuleBefore = Set.of();
        currentRuleChange = null;
        currentRuleActive = false;
    }

    public void markCommitted() {
        event.status = "COMMITTED";
        event.finishedAt = System.currentTimeMillis();
    }

    public void markRolledBack(String error) {
        event.status = "ROLLED_BACK";
        event.error = error;
        event.finishedAt = System.currentTimeMillis();
    }

    public void markFailed(String error) {
        event.status = "FAILED";
        event.error = error;
        event.finishedAt = System.currentTimeMillis();
    }

    public TransactionEvent getEvent() {
        return event;
    }

    private static Set<TripleKey> snapshot(SailRepositoryConnection connection, String graphIri) {
        Set<TripleKey> triples = new LinkedHashSet<>();

        ValueFactory valueFactory = connection.getValueFactory();
        IRI graph = graphIri != null ? valueFactory.createIRI(graphIri) : null;

        try (RepositoryResult<Statement> statements = graph == null
                ? connection.getStatements(null, null, null, false)
                : connection.getStatements(null, null, null, false, graph)) {

            while (statements.hasNext()) {
                Statement st = statements.next();
                triples.add(TripleKey.from(st));
            }
        }

        return triples;
    }

    private static Set<TripleKey> diff(Set<TripleKey> left, Set<TripleKey> right) {
        Set<TripleKey> result = new HashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static List<TransactionEvent.RdfTriple> toTriples(Set<TripleKey> keys) {
        List<TransactionEvent.RdfTriple> triples = new ArrayList<>(keys.size());
        for (TripleKey key : keys) {
            triples.add(new TransactionEvent.RdfTriple(key.subject, key.predicate, key.object, key.context));
        }
        return triples;
    }

    private static final class TripleKey {
        private final String subject;
        private final String predicate;
        private final String object;
        private final String context;

        private TripleKey(String subject, String predicate, String object, String context) {
            this.subject = subject;
            this.predicate = predicate;
            this.object = object;
            this.context = context;
        }

        private static TripleKey from(Statement statement) {
            return new TripleKey(
                    statement.getSubject().stringValue(),
                    statement.getPredicate().stringValue(),
                    statement.getObject().toString(),
                    statement.getContext() != null ? statement.getContext().stringValue() : null
            );
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof TripleKey tripleKey)) {
                return false;
            }
            return Objects.equals(subject, tripleKey.subject)
                    && Objects.equals(predicate, tripleKey.predicate)
                    && Objects.equals(this.object, tripleKey.object)
                    && Objects.equals(context, tripleKey.context);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subject, predicate, object, context);
        }
    }
}
