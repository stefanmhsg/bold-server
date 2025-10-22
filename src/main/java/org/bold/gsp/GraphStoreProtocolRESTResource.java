package org.bold.gsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.bold.Configurator;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Exposing the named graphs in the RDF Dataset as REST resources, in the style
 * of the SPARQL Graph Store Protocol.
 * 
 * TODO: Implement POST, LDP-style
 * 
 * @author Tobias Käfer
 *
 */

// Catching all, which can only be done using a regex. Must assign a name ("id") to use a regex. 
// Use IsPutOrNamedGraphInDatasetFilter to create 404 responses before we get here.
@Path("/{id: .*}")
public class GraphStoreProtocolRESTResource {
	private static final Logger log = LoggerFactory.getLogger(GraphStoreProtocolRESTResource.class);

	@Context
	ServletContext _ctx;

	// Instead of multiple methods with individual @Produces annotations, we could
	// also use just one method with multiple @Produces annotations. However, the
	// eventually chosen media type is not retrievable programmatically, so we write
	// several methods and thus access the choice.

@GET
@Path("/.debug-contexts")
@Produces("text/plain")
public String debugListContexts() {
    SailRepository repo = (SailRepository) _ctx.getAttribute(Configurator.SAIL_REPOSITORY_SERVLET_ATTRIBUTE);
    try (SailRepositoryConnection connection = repo.getConnection()) {
        StringBuilder sb = new StringBuilder();

        var contexts = connection.getContextIDs();   // type: RepositoryResult<Resource>
        try {
            while (contexts.hasNext()) {
                Resource c = contexts.next();
                long count = connection.size(c);
                sb.append(c.stringValue()).append("  (").append(count).append(" triples)\n");
            }
        } finally {
            contexts.close();
        }

        return sb.toString();
    }
}

	

	@GET
	@Produces("text/turtle")
	public StreamingOutput getTurtle(@Context UriInfo uriinfo) {
		log.info("GET request for graph (text/turtle): {}", uriinfo.getAbsolutePath());
		return getRDF(uriinfo, RDFFormat.TURTLE);
	}

	@GET
	@Produces("application/rdf+xml")
	public StreamingOutput getRDFXML(@Context UriInfo uriinfo) {
		log.info("GET request for graph (application/rdf+xml): {}", uriinfo.getAbsolutePath());
		return getRDF(uriinfo, RDFFormat.RDFXML);
	}

	@GET
	@Produces("application/n-triples")
	public StreamingOutput getNtriples(@Context UriInfo uriinfo) {
		log.info("GET request for graph (application/n-triples): {}", uriinfo.getAbsolutePath());
		return getRDF(uriinfo, RDFFormat.NTRIPLES);
	}

	private StreamingOutput getRDF(UriInfo uriinfo, RDFFormat finalOutputFormat) {

		log.info("STM: Inside getRDF at GraphStoreProtocolRESTResource");
		SailRepository repo = (SailRepository) _ctx.getAttribute(Configurator.SAIL_REPOSITORY_SERVLET_ATTRIBUTE);
		SailRepositoryConnection connection = repo.getConnection();

		ValueFactory vf = connection.getValueFactory();

		IRI graphName = vf.createIRI(uriinfo.getAbsolutePath().toString());
		log.info("Resolved graph IRI: {}", graphName);

		// checking if graph exists in triple store
		if (!connection.hasStatement(null, null, null, false, graphName)) {
			log.warn("Graph not found in RDF dataset: {}", graphName);
			connection.close();
			throw new NotFoundException("STM: Graph not found in RDF dataset: " + graphName);
		}

		log.info("Graph found, exporting: {} with format {}", graphName, finalOutputFormat.getName());

		// RIO wants to write to a stream, thus we have to wrap RIO's writer
		StreamingOutput output = new StreamingOutput() {

			@Override
			public void write(OutputStream os) throws IOException, WebApplicationException {
				RDFWriter writer = Rio.createWriter(finalOutputFormat, os);
				connection.export(writer, graphName);
				connection.close();
			}
		};

		return output;
	}

	// Instead of writing multiple methods with different @Consumes annotations, we
	// could also just write one method with multiple @Consumes annotations. But then, we
	// would need to ask Rio to parse the Content-type header a second time in order
	// to create the instances of RDFFormat that we need when adding the triples via
	// the {@link SailRepositoryConnection}.

	@PUT
	@Consumes("text/turtle")
	public Response putTurtle(@Context UriInfo uriinfo, @Context HttpServletRequest req, InputStream is) {
		log.info("PUT request for graph (text/turtle): {}", uriinfo.getAbsolutePath());
		return putRDF(uriinfo, req, is, RDFFormat.TURTLE);
	}

	@PUT
	@Consumes("application/n-triples")
	public Response putNtriples(@Context UriInfo uriinfo, @Context HttpServletRequest req, InputStream is) {
		log.info("PUT request for graph (application/n-triples): {}", uriinfo.getAbsolutePath());
		return putRDF(uriinfo, req, is, RDFFormat.NTRIPLES);
	}

	@PUT
	@Consumes("application/rdf+xml")
	public Response putRDFXML(@Context UriInfo uriinfo, @Context HttpServletRequest req, InputStream is) {
		log.info("PUT request for graph (application/rdf+xml): {}", uriinfo.getAbsolutePath());
		return putRDF(uriinfo, req, is, RDFFormat.RDFXML);
	}

	private Response putRDF(UriInfo uriinfo, HttpServletRequest req, InputStream is, RDFFormat parsedMimeType) {

		// Connecting to the repository.
		SailRepository repo = (SailRepository) _ctx.getAttribute(Configurator.SAIL_REPOSITORY_SERVLET_ATTRIBUTE);
		SailRepositoryConnection connection = repo.getConnection();

		// Determining target URI from request.
		String requestTargetUriString = uriinfo.getAbsolutePath().toString();

		// Converting target URI to Rio's classes.
		ValueFactory vf = connection.getValueFactory();
		IRI requestTargetUriIRI = vf.createIRI(requestTargetUriString);

		log.info("PUT request for graph: {} with mime {}", requestTargetUriString, parsedMimeType.getName());

		// Begin transaction
		// Begin transaction
		connection.begin();
		
		boolean resourceExistedBeforeRequest = false;
		// checking if graph exists in triple store
		if (connection.hasStatement(null, null, null, false, requestTargetUriIRI)) {
			resourceExistedBeforeRequest = true;
		}

		// PUT semantics: Content supplied replaces what's at the target URI. Thus removing what's there...
        connection.clear(requestTargetUriIRI);
        try {
        	// ...and then adding the new stuff.
			connection.add(is, requestTargetUriString, parsedMimeType, requestTargetUriIRI);
			connection.commit();
			log.info("Successfully updated graph: {}", requestTargetUriString);
		} catch (RDFParseException e) {
			connection.rollback();
			log.error("Failed to parse RDF data for graph: {}", requestTargetUriString, e);
			throw new BadRequestException(e);
		} catch (RepositoryException | IOException e) {
			connection.rollback();
			log.error("Failed to update graph: {}", requestTargetUriString, e);
			throw new InternalServerErrorException(e);
		} finally {
			connection.close();
		}

        // Creating response with appropriate response code.
        if (resourceExistedBeforeRequest)
        	return Response.noContent().build();
        else
        	return Response.created(URI.create(requestTargetUriString)).build();
	}	

	@DELETE
	public Response deleteResource(@Context UriInfo uriinfo) {

		SailRepository repo = (SailRepository) _ctx.getAttribute(Configurator.SAIL_REPOSITORY_SERVLET_ATTRIBUTE);
		SailRepositoryConnection connection = repo.getConnection();

		// Determining target URI from request.
		String requestTargetUriString = uriinfo.getAbsolutePath().toString();

		// Converting target URI to Rio's classes.
		ValueFactory vf = connection.getValueFactory();
		IRI requestTargetUriIRI = vf.createIRI(requestTargetUriString);

		log.info("DELETE request for graph: {}", requestTargetUriString);
		// checking if graph exists in triple store
		if (!connection.hasStatement(null, null, null, false, requestTargetUriIRI)) {
			connection.close();
			log.warn("Graph not found in RDF dataset: {}", requestTargetUriString);
			throw new NotFoundException();
		}

		// Deleting the named graph
		connection.clear(requestTargetUriIRI);
		log.info("Deleted graph: {}", requestTargetUriString);

		// Cleanup
		connection.close();

		return Response.noContent().build();
	}
}
