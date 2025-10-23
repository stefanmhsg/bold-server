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
import javax.ws.rs.POST;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;

import org.bold.Configurator;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.Model;
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

    // -------------------------------------------
    // Additional methods (e.g., POST, OPTIONS)
    // -------------------------------------------
    
    @OPTIONS
    public Response handleOptions(@Context UriInfo uriinfo) {
        return Response.noContent()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type")
                .build();
    }

    @POST
    @Consumes({ "text/turtle", "application/n-triples", "application/ld+json", "application/rdf+xml" })
    public Response postGraph(@Context UriInfo uriinfo, String body) {
        String graphIRI = uriinfo.getAbsolutePath().toString();
        log.info("LD POST merge into graph: {}", graphIRI);

        SailRepository repo = (SailRepository) _ctx.getAttribute(Configurator.SAIL_REPOSITORY_SERVLET_ATTRIBUTE);
        try (SailRepositoryConnection connection = repo.getConnection()) {

            ValueFactory vf = connection.getValueFactory();
            IRI graphName = vf.createIRI(graphIRI);

            // check existence (same semantics as GET)
            boolean exists = connection.hasStatement(null, null, null, false, graphName);
            if (!exists) {
                log.info("LD POST received but graph does not exist: {}", graphIRI);
                return Response.status(Response.Status.NOT_FOUND).entity("Graph not found: " + graphIRI).build();
            }

            // Parse body into this graph context
            java.util.Optional<RDFFormat> fmtOpt = Rio.getParserFormatForMIMEType(detectContentType(body));
            RDFFormat fmt = fmtOpt.orElse(RDFFormat.TURTLE); // fallback

            Model model = Rio.parse(new java.io.ByteArrayInputStream(body.getBytes()),
                      graphIRI,      // base URI → resolves relative URIs against the graph itself
                      fmt,
                      graphName);    // <-- merge into this same named graph

            connection.begin();
            connection.add(model);
            connection.commit();

            log.info("LD POST merged triples: \n {} \n into: {}", body, graphIRI);
            // verify body
            try {
                org.eclipse.rdf4j.query.GraphQuery query = connection.prepareGraphQuery(
                    "CONSTRUCT { ?s ?p ?o } WHERE { GRAPH <" + graphIRI + "> { ?s ?p ?o } }");

                java.io.StringWriter sw = new java.io.StringWriter();
                RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE, sw);
                query.evaluate(writer);
                log.info("\n Graph content: \n{}", sw.toString());
            } catch (Exception e) {
                log.error("LD POST error verifying graph {}", graphIRI, e);
            }


            return Response.noContent()
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
                    .build();

        } catch (IOException | RDFParseException | UnsupportedRDFormatException e) {
            log.error("LD POST failed parsing body for {}", graphIRI, e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Bad RDF payload").build();
        } catch (Exception e) {
            log.error("LD POST error while writing graph {}", graphIRI, e);
            return Response.serverError().build();
        }
    }

    // Helper to guess MIME if no Content-Type was set by LDFu (which it often doesn't)
    private String detectContentType(String body) {
        // ultra lightweight heuristic: N3/Turtle starts with @prefix or <> or <http...
        String t = body.trim().toLowerCase();
        if (t.startsWith("@prefix") || t.startsWith("<")) return "text/turtle";
        return "text/turtle"; // fallback ok for ldfu
    }

}
