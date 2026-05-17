package org.maze.api.vocab;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.maze.domain.vocab.MazeVocab;

/**
 * Serves the checked-in MASE vocabulary document with RDF content negotiation.
 */
@Path("/vocab")
public class VocabularyResource {

    private static final java.nio.file.Path VOCABULARY_PATH = java.nio.file.Path.of("docs", "maze.ttl");

    private static final MediaType TEXT_TURTLE = MediaType.valueOf("text/turtle");
    private static final MediaType JSON_LD = MediaType.valueOf("application/ld+json");
    private static final MediaType RDF_XML = MediaType.valueOf("application/rdf+xml");
    private static final MediaType N_TRIPLES = MediaType.valueOf("application/n-triples");

    private static final List<MediaType> RDF_MEDIA_TYPES = List.of(
            TEXT_TURTLE,
            JSON_LD,
            RDF_XML,
            N_TRIPLES);

    @GET
    @Produces({ "text/turtle", "application/ld+json", "application/rdf+xml", "application/n-triples" })
    public Response getVocabulary(@Context HttpHeaders headers) {
        if (!Files.isRegularFile(VOCABULARY_PATH)) {
            return Response.serverError()
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .entity("Vocabulary file not found: " + VOCABULARY_PATH)
                    .build();
        }

        MediaType mediaType = selectRdfMediaType(headers.getAcceptableMediaTypes());
        RDFFormat format = rdfFormatForMediaType(mediaType);
        StreamingOutput output = streamVocabulary(format);

        return Response.ok(output, mediaType)
                .header("Vary", "Accept")
                .build();
    }

    private StreamingOutput streamVocabulary(RDFFormat format) {
        return outputStream -> {
            try (InputStream inputStream = Files.newInputStream(VOCABULARY_PATH)) {
                Model model = Rio.parse(inputStream, MazeVocab.MAZE_NS, RDFFormat.TURTLE);
                Rio.write(model, outputStream, format);
            } catch (IOException e) {
                throw new WebApplicationException("Failed to serve vocabulary", e);
            }
        };
    }

    private MediaType selectRdfMediaType(List<MediaType> acceptableTypes) {
        if (acceptableTypes == null || acceptableTypes.isEmpty()) {
            return TEXT_TURTLE;
        }

        for (MediaType acceptableType : acceptableTypes) {
            if (isQualityZero(acceptableType)) {
                continue;
            }

            if (acceptableType.isWildcardType() || acceptableType.isWildcardSubtype()) {
                return TEXT_TURTLE;
            }

            for (MediaType rdfMediaType : RDF_MEDIA_TYPES) {
                if (acceptableType.isCompatible(rdfMediaType)) {
                    return rdfMediaType;
                }
            }
        }

        return TEXT_TURTLE;
    }

    private boolean isQualityZero(MediaType mediaType) {
        String quality = mediaType.getParameters().get("q");
        if (quality == null) {
            return false;
        }

        try {
            return Double.parseDouble(quality) <= 0.0d;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private RDFFormat rdfFormatForMediaType(MediaType mediaType) {
        String mimeType = mediaType.getType() + "/" + mediaType.getSubtype();
        return Rio.getWriterFormatForMIMEType(mimeType)
                .orElse(RDFFormat.TURTLE);
    }
}
