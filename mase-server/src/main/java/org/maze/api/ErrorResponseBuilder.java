package org.maze.api;

import java.io.StringWriter;
import java.util.List;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.maze.domain.vocab.MazeVocab;

/**
 * Builds HTTP error responses while preserving the existing error message text.
 */
public final class ErrorResponseBuilder {

    private static final MediaType TEXT_TURTLE = MediaType.valueOf("text/turtle");
    private static final MediaType JSON_LD = MediaType.valueOf("application/ld+json");
    private static final MediaType RDF_XML = MediaType.valueOf("application/rdf+xml");
    private static final MediaType N_TRIPLES = MediaType.valueOf("application/n-triples");

    private static final List<MediaType> RDF_MEDIA_TYPES = List.of(
            TEXT_TURTLE,
            JSON_LD,
            RDF_XML,
            N_TRIPLES);

    private ErrorResponseBuilder() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    public static Response build(List<MediaType> acceptableTypes,
                                 int statusCode,
                                 String targetResourceUri,
                                 String message) {
        return build(acceptableTypes, statusCode, targetResourceUri, message, message, MediaType.TEXT_PLAIN_TYPE);
    }

    public static Response build(List<MediaType> acceptableTypes,
                                 int statusCode,
                                 String targetResourceUri,
                                 String message,
                                 String fallbackEntity,
                                 MediaType fallbackMediaType) {
        MediaType rdfMediaType = selectRdfMediaType(acceptableTypes);
        if (rdfMediaType == null) {
            return Response.status(statusCode)
                    .type(fallbackMediaType)
                    .entity(fallbackEntity)
                    .build();
        }

        RDFFormat format = rdfFormatForMediaType(rdfMediaType);
        return Response.status(statusCode)
                .type(rdfMediaType)
                .entity(serializeRdfError(targetResourceUri, statusCode, message, format))
                .build();
    }

    static MediaType selectRdfMediaType(List<MediaType> acceptableTypes) {
        if (acceptableTypes == null || acceptableTypes.isEmpty()) {
            return null;
        }

        for (MediaType acceptableType : acceptableTypes) {
            if (acceptableType.isWildcardType() || acceptableType.isWildcardSubtype()) {
                continue;
            }
            if (isQualityZero(acceptableType)) {
                continue;
            }

            for (MediaType rdfMediaType : RDF_MEDIA_TYPES) {
                if (acceptableType.isCompatible(rdfMediaType)) {
                    return rdfMediaType;
                }
            }
        }

        return null;
    }

    static String serializeRdfError(String targetResourceUri, int statusCode, String message, RDFFormat format) {
        ValueFactory vf = SimpleValueFactory.getInstance();
        IRI targetResource = vf.createIRI(targetResourceUri);
        IRI errorMessage = vf.createIRI(MazeVocab.ERROR_MESSAGE);
        IRI errorStatusCode = vf.createIRI(MazeVocab.ERROR_STATUS_CODE);
        IRI httpStatusCodeValue = vf.createIRI(MazeVocab.HTTP_STATUS_CODE_VALUE);

        Model model = new LinkedHashModel();
        model.setNamespace("mase", MazeVocab.MASE_NS);
        model.setNamespace("http", MazeVocab.HTTP_NS);
        model.setNamespace("rdf", RDF.NAMESPACE);
        model.setNamespace("xsd", XSD.NAMESPACE);
        model.add(targetResource, errorMessage, vf.createLiteral(message));
        model.add(targetResource, errorStatusCode, vf.createLiteral(statusCode));
        model.add(targetResource, httpStatusCodeValue, vf.createLiteral(Integer.toString(statusCode)));

        StringWriter writer = new StringWriter();
        Rio.write(model, writer, format);
        return writer.toString();
    }

    private static boolean isQualityZero(MediaType mediaType) {
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

    private static RDFFormat rdfFormatForMediaType(MediaType mediaType) {
        String mimeType = mediaType.getType() + "/" + mediaType.getSubtype();
        return Rio.getWriterFormatForMIMEType(mimeType)
                .orElse(RDFFormat.TURTLE);
    }
}
