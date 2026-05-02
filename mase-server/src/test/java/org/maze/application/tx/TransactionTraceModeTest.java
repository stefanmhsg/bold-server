package org.maze.application.tx;

import org.junit.jupiter.api.Test;
import org.maze.api.websocket.events.TransactionEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TransactionTraceModeTest {

    @Test
    void summaryTraceEmitsHeadersWithoutRequestBodyOrTripleSnapshots() {
        TransactionTraceContext trace = TransactionTraceContext.forPost(
                TransactionTraceMode.SUMMARY,
                "bob",
                "http://127.0.1.1:8080/cells/0/0",
                "<request> <body> <omitted> .");

        trace.beginRule("move.rq", null);
        trace.endRule(null);

        TransactionEvent event = trace.getEvent();
        System.out.printf(
                "[TEST] summary trace -> mode=%s, trigger=%s, agent=%s, graph=%s, requestBody=%s, ruleCount=%d%n",
                event.traceMode,
                event.trigger,
                event.agent,
                event.graph,
                event.requestBody,
                event.ruleCount);

        assertEquals("summary", event.traceMode);
        assertEquals("POST", event.trigger);
        assertEquals("bob", event.agent);
        assertEquals("http://127.0.1.1:8080/cells/0/0", event.graph);
        assertNull(event.requestBody);
        assertEquals(1, event.ruleCount);
        assertEquals(0, event.rules.size());
        assertEquals(0, event.mergeAdded.size());
        assertEquals(0, event.mergeRemoved.size());
    }

    @Test
    void fullTraceKeepsRequestBodyForDebugInspection() {
        TransactionTraceContext trace = TransactionTraceContext.forPost(
                TransactionTraceMode.FULL,
                "bob",
                "http://127.0.1.1:8080/cells/0/0",
                "<request> <body> <included> .");

        TransactionEvent event = trace.getEvent();
        System.out.printf(
                "[TEST] full trace -> mode=%s, requestBody=%s%n",
                event.traceMode,
                event.requestBody);

        assertEquals("full", event.traceMode);
        assertEquals("<request> <body> <included> .", event.requestBody);
    }

    @Test
    void offTraceDoesNotCreateTransactionEvent() {
        TransactionTraceContext trace = TransactionTraceContext.forPost(
                TransactionTraceMode.OFF,
                "bob",
                "http://127.0.1.1:8080/cells/0/0",
                "<request> <body> <omitted> .");

        System.out.println("[TEST] off trace -> no TRANSACTION event");

        assertNull(trace);
    }

    @Test
    void propertyValuesMapToTraceModes() {
        assertSame(TransactionTraceMode.SUMMARY, TransactionTraceMode.fromProperty(null));
        assertSame(TransactionTraceMode.SUMMARY, TransactionTraceMode.fromProperty("summary"));
        assertSame(TransactionTraceMode.FULL, TransactionTraceMode.fromProperty("true"));
        assertSame(TransactionTraceMode.FULL, TransactionTraceMode.fromProperty("full"));
        assertSame(TransactionTraceMode.OFF, TransactionTraceMode.fromProperty("false"));
        assertSame(TransactionTraceMode.OFF, TransactionTraceMode.fromProperty("off"));
    }
}
