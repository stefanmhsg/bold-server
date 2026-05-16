package org.mase.creator.trig;

import org.junit.jupiter.api.Test;
import org.mase.creator.model.MazeModel;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MazeTrigFixtureRoundTripTest {

    private static final Pattern GRAPH_PATTERN = Pattern.compile(
            "(?m)^[ \\t]*(<[^>]+>)\\s*\\{(.*?)\\}([ \\t]*#[^\\r\\n]*)?",
            Pattern.DOTALL
    );
    private static final Pattern GREEN_PATTERN = Pattern.compile("maze:green\\s+(<[^>]+>)");

    @Test
    void ccrsMazeV1RoundTripRetainsScenarioComponents() throws IOException, URISyntaxException {
        String source = Files.readString(Path.of(
                getClass().getResource("/fixtures/CcrsMazeV1.trig").toURI()
        ));

        MazeModel model = new MazeTrigParser().parse(source);
        String output = new MazeTrigSerializer().serialize(model);

        assertEquals(activeGreenSuccessors(source), activeGreenSuccessors(output));
        assertContainsIgnoringWhitespace(output, "</counter> { </counter> a maze:Counter; rdf:value 0 . }");
        assertContainsIgnoringWhitespace(output, "</colors> { maze:green a maze:Color . }");
        assertContainsIgnoringWhitespace(output, "</cells/0> { </cells/0> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south </cells/0/2>; maze:east maze:Wall; maze:green </cells/0/2> . }");
        assertContainsIgnoringWhitespace(output, "</cells/0/2> { </cells/0/2> a maze:Cell ; maze:north </cells/0>");
        assertContainsIgnoringWhitespace(output, "xhv:start </cells/0> .");

        assertContainsIgnoringWhitespace(output, "hydra:operation <http://127.0.1.1:8080/cells/12/5#greenAction>");
        assertContainsIgnoringWhitespace(output, "</cells/31/26#key> a dyn:RedKey; dyn:fitsInLock </cells/36/36> ; dyn:keyValue \"redkey-1670\" .");
        assertContainsIgnoringWhitespace(output, "maze:orange </cells/32/43> . } # orange signifiers for re-direct of broken cell.");

        assertContainsIgnoringWhitespace(output, "#define the root of our scenario, where the maze exists");
        assertContainsIgnoringWhitespace(output, "# </cells/28/13> { </cells/28/13> a maze:Cell, dyn:Lock ; hydra:operation <http://127.0.1.1:8080/cells/28/13#blueAction>");
        assertContainsIgnoringWhitespace(output, "# Representing a broken cell -> returns 404:");
        assertContainsIgnoringWhitespace(output, "#; maze:green </cells/28/14>");
        assertContainsIgnoringWhitespace(output, "#Correct plan # http://127.0.1.1:8080/cells/0");
    }

    private Set<String> activeGreenSuccessors(String trig) {
        Set<String> successors = new LinkedHashSet<>();
        Matcher graphMatcher = GRAPH_PATTERN.matcher(trig);
        while (graphMatcher.find()) {
            String subject = graphMatcher.group(1);
            Matcher greenMatcher = GREEN_PATTERN.matcher(stripComments(graphMatcher.group(2)));
            if (greenMatcher.find()) {
                successors.add(subject + " -> " + greenMatcher.group(1));
            }
        }
        return successors;
    }

    private void assertContainsIgnoringWhitespace(String actual, String expectedSnippet) {
        assertTrue(
                normalizeWhitespace(actual).contains(normalizeWhitespace(expectedSnippet)),
                () -> "Expected output to contain, ignoring whitespace: " + expectedSnippet
        );
    }

    private String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String stripComments(String text) {
        StringBuilder stripped = new StringBuilder(text.length());
        boolean inIri = false;
        boolean inComment = false;
        char quote = 0;
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (inComment) {
                if (current == '\n' || current == '\r') {
                    inComment = false;
                    stripped.append(current);
                } else {
                    stripped.append(' ');
                }
                continue;
            }
            if (quote != 0) {
                stripped.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (inIri) {
                stripped.append(current);
                if (current == '>') {
                    inIri = false;
                }
                continue;
            }

            if (current == '#') {
                inComment = true;
                stripped.append(' ');
            } else {
                stripped.append(current);
                if (current == '<') {
                    inIri = true;
                } else if (current == '"' || current == '\'') {
                    quote = current;
                }
            }
        }
        return stripped.toString();
    }
}
