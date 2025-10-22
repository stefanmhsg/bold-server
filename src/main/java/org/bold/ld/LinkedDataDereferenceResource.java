package org.bold.ld;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.bold.Configurator;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dereference any request URI as an RDF named graph if present in the store.
 * Mounted at "/*" in Jetty, while /gsp/* still maps to the GSP resource.
 */
@Path("/{id: .*}")
public class LinkedDataDereferenceResource {

    private static final Logger log = LoggerFactory.getLogger(LinkedDataDereferenceResource.class);

    @Context
    ServletContext _ctx;

    @GET
    @Produces("text/turtle")
    public StreamingOutput getTurtle(@Context UriInfo uriinfo,
                                     @HeaderParam("Accept") String accept) {
        log.info("LD GET request for graph (text/turtle): {}", uriinfo.getAbsolutePath());
        return streamGraph(uriinfo, RDFFormat.TURTLE);
    }

    @GET
    @Produces("application/ld+json")
    public StreamingOutput getJsonLd(@Context UriInfo uriinfo,
                                     @HeaderParam("Accept") String accept) {
        log.info("LD GET request for graph (application/ld+json): {}", uriinfo.getAbsolutePath());
        return streamGraph(uriinfo, RDFFormat.JSONLD);
    }

    @GET
    @Produces("application/rdf+xml")
    public StreamingOutput getRdfXml(@Context UriInfo uriinfo,
                                     @HeaderParam("Accept") String accept) {
        log.info("LD GET request for graph (application/rdf+xml): {}", uriinfo.getAbsolutePath());
        return streamGraph(uriinfo, RDFFormat.RDFXML);
    }

    @GET
    @Produces("application/n-triples")
    public StreamingOutput getNTriples(@Context UriInfo uriinfo,
                                       @HeaderParam("Accept") String accept) {
        log.info("LD GET request for graph (application/n-triples): {}", uriinfo.getAbsolutePath());
        return streamGraph(uriinfo, RDFFormat.NTRIPLES);
    }

    private StreamingOutput streamGraph(UriInfo uriinfo, RDFFormat outputFormat) {
        SailRepository repo = (SailRepository) _ctx.getAttribute(Configurator.SAIL_REPOSITORY_SERVLET_ATTRIBUTE);
        SailRepositoryConnection connection = repo.getConnection();

        try {
            ValueFactory vf = connection.getValueFactory();
            IRI graphName = vf.createIRI(uriinfo.getAbsolutePath().toString());

            log.info("LD resolved graph IRI: {}", graphName);

            boolean exists = connection.hasStatement(null, null, null, false, graphName);
            if (!exists) {
                connection.close();
                log.info("LD graph not found, returning 404 for {}", graphName);
                throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity("Graph not found in RDF dataset: " + graphName)
                            .build());
            }

            StreamingOutput output = new StreamingOutput() {
                @Override
                public void write(OutputStream os) throws IOException, WebApplicationException {
                    try {
                        RDFWriter writer = Rio.createWriter(outputFormat, os);
                        connection.export(writer, graphName);
                        log.info("LD exported graph {} as {}", graphName, outputFormat.getDefaultMIMEType());
                    } finally {
                        connection.close();
                    }
                }
            };
            return output;

        } catch (RuntimeException e) {
            try { connection.close(); } catch (Exception ignore) {}
            log.error("LD error while dereferencing {}", uriinfo.getAbsolutePath(), e);
            throw e;
        }
    }
}
