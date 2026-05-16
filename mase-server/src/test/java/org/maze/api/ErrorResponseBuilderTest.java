package org.maze.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.Test;
import org.maze.domain.vocab.MazeVocab;

class ErrorResponseBuilderTest {

    private static final String TARGET_GRAPH = "http://127.0.1.1:8080/cells/0/1";
    private static final String ERROR_MESSAGE =
            "Access denied. Cell http://127.0.1.1:8080/cells/0/1 is not accessible from http://127.0.1.1:8080/cells/0/0 (no connection in graph)";

    @Test
    void turtleErrorResponsePreservesMessageAsRdfLiteral() throws Exception {
        Response response = ErrorResponseBuilder.build(
                List.of(MediaType.valueOf("text/turtle")),
                403,
                TARGET_GRAPH,
                ERROR_MESSAGE);

        assertEquals(403, response.getStatus());
        assertEquals(MediaType.valueOf("text/turtle"), response.getMediaType());

        Model model = Rio.parse(new StringReader((String) response.getEntity()), TARGET_GRAPH, RDFFormat.TURTLE);
        ValueFactory vf = SimpleValueFactory.getInstance();
        IRI target = vf.createIRI(TARGET_GRAPH);
        IRI messagePredicate = vf.createIRI(MazeVocab.ERROR_MESSAGE);
        IRI statusPredicate = vf.createIRI(MazeVocab.ERROR_STATUS_CODE);

        assertTrue(model.contains(target, messagePredicate, vf.createLiteral(ERROR_MESSAGE)));
        Literal statusLiteral = vf.createLiteral(403);
        assertTrue(model.contains(target, statusPredicate, statusLiteral));
    }

    @Test
    void jsonLdErrorResponsePreservesMessageAsRdfLiteral() throws Exception {
        Response response = ErrorResponseBuilder.build(
                List.of(MediaType.valueOf("application/ld+json")),
                404,
                TARGET_GRAPH,
                ERROR_MESSAGE);

        assertEquals(404, response.getStatus());
        assertEquals(MediaType.valueOf("application/ld+json"), response.getMediaType());

        Model model = Rio.parse(new StringReader((String) response.getEntity()), TARGET_GRAPH, RDFFormat.JSONLD);
        ValueFactory vf = SimpleValueFactory.getInstance();

        assertTrue(model.contains(
                vf.createIRI(TARGET_GRAPH),
                vf.createIRI(MazeVocab.ERROR_MESSAGE),
                vf.createLiteral(ERROR_MESSAGE)));
    }

    @Test
    void plainTextFallbackKeepsLegacyBodyExactly() {
        Response response = ErrorResponseBuilder.build(
                List.of(MediaType.WILDCARD_TYPE),
                400,
                TARGET_GRAPH,
                ERROR_MESSAGE);

        assertEquals(400, response.getStatus());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getMediaType());
        assertEquals(ERROR_MESSAGE, response.getEntity());
    }

    @Test
    void customFallbackKeepsLegacyBodyExactly() {
        String fallbackJson = "{\"error\":\"Query is required\"}";

        Response response = ErrorResponseBuilder.build(
                List.of(MediaType.valueOf("application/json")),
                400,
                "http://127.0.1.1:8080/sparql",
                "Query is required",
                fallbackJson,
                MediaType.APPLICATION_JSON_TYPE);

        assertEquals(400, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals(fallbackJson, response.getEntity());
    }
}
