package org.maze.api.vocab;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.Test;
import org.maze.domain.vocab.MazeVocab;

class VocabularyDocumentTest {

    @Test
    void mazeVocabularyParsesAndContainsCurrentPublicTerms() throws Exception {
        Model model;
        try (InputStream inputStream = Files.newInputStream(Path.of("docs", "maze.ttl"))) {
            model = Rio.parse(inputStream, MazeVocab.MAZE_NS, RDFFormat.TURTLE);
        }

        ValueFactory vf = SimpleValueFactory.getInstance();
        assertClass(model, vf.createIRI(MazeVocab.MAZE_NS + "Cell"));
        assertClass(model, vf.createIRI(MazeVocab.DYNMAZE_NS + "Lock"));
        assertClass(model, vf.createIRI(MazeVocab.MASE_NS + "ScenarioPackage"));
        assertProperty(model, vf.createIRI(MazeVocab.UI_HAS_UI_ELEMENT));
        assertProperty(model, vf.createIRI(MazeVocab.ERROR_MESSAGE));
        assertProperty(model, vf.createIRI("https://example.org/a2a#agentCard"));
    }

    private void assertClass(Model model, IRI iri) {
        assertTrue(model.contains(iri, RDF.TYPE, RDFS.CLASS), "Missing class: " + iri);
    }

    private void assertProperty(Model model, IRI iri) {
        assertTrue(model.contains(iri, RDF.TYPE, RDF.PROPERTY), "Missing property: " + iri);
    }
}
