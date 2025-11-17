package org.maze.api.ld;

import java.io.IOException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple CORS headers for browser clients.
 */
@Provider
@PreMatching
public class CorsFilter implements ContainerResponseFilter {

    private static final Logger log = LoggerFactory.getLogger(CorsFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {

        // Add only if not already set by another component
        if (responseContext.getHeaders().getFirst("Access-Control-Allow-Origin") == null) {
            responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
            responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD");
            responseContext.getHeaders().add("Access-Control-Allow-Headers", "Accept, Content-Type");
        }
    }
}
