package org.maze.infrastructure.web;

import java.net.URI;

/**
 * Maps HTTP request URLs back to the canonical RDF resource IRI base.
 *
 * Browser-facing hosts can differ from the host used when TriG data was loaded.
 * For example, Docker users may reach the server at localhost while the RDF store
 * was initialized with http://127.0.1.1:8080/. Linked Data dereferencing must
 * use the canonical RDF IRI for graph lookup, not the transport URL used by one
 * particular client.
 */
public final class ResourceIriResolver {

    private final URI canonicalBaseUri;

    public ResourceIriResolver(URI canonicalBaseUri) {
        this.canonicalBaseUri = ensureTrailingSlash(canonicalBaseUri);
    }

    public static ResourceIriResolver fromRdfBaseUri(URI rdfBaseUri) {
        return new ResourceIriResolver(rdfBaseUri.resolve("../"));
    }

    public String canonicalize(String requestUri) {
        URI request = URI.create(requestUri);
        String path = request.getPath();

        if (!isMaseResourcePath(path)) {
            return requestUri;
        }

        return canonicalBaseUri.resolve(stripLeadingSlash(path)).toString();
    }

    public URI canonicalBaseUri() {
        return canonicalBaseUri;
    }

    private boolean isMaseResourcePath(String path) {
        return path != null
                && (path.equals("/maze")
                || path.equals("/cells")
                || path.startsWith("/cells/")
                || path.equals("/agents")
                || path.startsWith("/agents/"));
    }

    private static URI ensureTrailingSlash(URI uri) {
        String value = uri.toString();
        if (value.endsWith("/")) {
            return uri;
        }
        return URI.create(value + "/");
    }

    private static String stripLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
