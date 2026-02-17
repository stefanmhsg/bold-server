package org.maze.application.tx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.maze.api.websocket.events.TransactionEvent;

public class TransactionTraceContext {

    private final TransactionEvent event;

    private Set<TripleKey> mergeBefore = Set.of();
    private Set<TripleKey> currentRuleBefore = Set.of();
    private TransactionEvent.RuleChange currentRuleChange;

    private TransactionTraceContext(String trigger) {
        this.event = new TransactionEvent(trigger);
    }

    public static TransactionTraceContext forPost(String agent, String graph, String requestBody) {
        TransactionTraceContext context = new TransactionTraceContext("POST");
        context.event.agent = agent;
        context.event.graph = graph;
        context.event.requestBody = requestBody;
        return context;
    }

    public static TransactionTraceContext forStartup() {
        return new TransactionTraceContext("STARTUP");
    }

    public void captureMergeBefore(SailRepositoryConnection connection, String graphIri) {
        this.mergeBefore = snapshot(connection, graphIri);
    }

    public void captureMergeAfter(SailRepositoryConnection connection, String graphIri) {
        Set<TripleKey> after = snapshot(connection, graphIri);
        event.mergeAdded = toTriples(diff(after, mergeBefore));
        event.mergeRemoved = toTriples(diff(mergeBefore, after));
    }

    public void beginRule(String ruleName, SailRepositoryConnection connection) {
        currentRuleChange = new TransactionEvent.RuleChange(ruleName);
        currentRuleBefore = snapshot(connection, null);
    }

    public void markCurrentRuleError(String error) {
        if (currentRuleChange != null) {
            currentRuleChange.error = error;
        }
    }

    public void endRule(SailRepositoryConnection connection) {
        if (currentRuleChange == null) {
            return;
        }

        Set<TripleKey> after = snapshot(connection, null);
        currentRuleChange.added = toTriples(diff(after, currentRuleBefore));
        currentRuleChange.removed = toTriples(diff(currentRuleBefore, after));
        event.rules.add(currentRuleChange);

        currentRuleBefore = Set.of();
        currentRuleChange = null;
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
