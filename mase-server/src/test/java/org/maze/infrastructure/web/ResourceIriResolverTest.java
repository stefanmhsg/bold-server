package org.maze.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

class ResourceIriResolverTest {

    private static final ResourceIriResolver RESOLVER = ResourceIriResolver.fromRdfBaseUri(
            URI.create("http://127.0.1.1:8080/gsp/"));

    @Test
    void canonicalizesCellRequestFromBrowserFacingHost() {
        assertEquals(
                "http://127.0.1.1:8080/cells/31/25",
                RESOLVER.canonicalize("http://localhost:8080/cells/31/25"));
    }

    @Test
    void canonicalizesAgentRequestFromDockerServiceHost() {
        assertEquals(
                "http://127.0.1.1:8080/agents/bob",
                RESOLVER.canonicalize("http://mase-server:8080/agents/bob"));
    }

    @Test
    void leavesNonMazeEndpointUntouched() {
        assertEquals(
                "http://localhost:8080/admin/maze",
                RESOLVER.canonicalize("http://localhost:8080/admin/maze"));
    }

    @Test
    void derivesCanonicalBaseFromRdfBase() {
        assertEquals(
                URI.create("http://127.0.1.1:8080/"),
                RESOLVER.canonicalBaseUri());
    }
}
