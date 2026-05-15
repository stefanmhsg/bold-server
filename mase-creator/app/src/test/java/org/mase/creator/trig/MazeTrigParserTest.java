package org.mase.creator.trig;

import org.junit.jupiter.api.Test;
import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.Direction;
import org.mase.creator.model.MazeModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MazeTrigParserTest {

    @Test
    void parsesMultilineCellsToleratesExtrasAndNormalizesConnections() {
        String trig = """
                @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                @prefix maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#> .
                @prefix xhv: <http://www.w3.org/1999/xhtml/vocab#> .

                </maze> {
                    </maze> a maze:CcrsMaze ;
                        xhv:start </cells/1/1> .
                }

                </not-a-cell> {
                    </not-a-cell> a maze:SomethingElse .
                }

                </cells/1/1> {
                    </cells/1/1> rdf:type maze:Cell ;
                        maze:north maze:Wall ;
                        maze:west maze:Wall ;
                        maze:south maze:Wall ;
                        maze:east </cells/1/2> ;
                        maze:green </cells/1/2> .
                }

                </cells/1/2> { </cells/1/2> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east maze:Wall; maze:exit </cells/999> . }
                </cells/999> { </cells/999> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east maze:Wall . }
                """;

        MazeModel model = new MazeTrigParser().parse(trig);
        CellCoordinate first = new CellCoordinate(1, 1);
        CellCoordinate second = new CellCoordinate(1, 2);

        assertTrue(model.hasCell(first));
        assertTrue(model.hasCell(second));
        assertEquals(2, model.cells().size());
        assertEquals(second, model.cell(first).orElseThrow().connection(Direction.EAST).orElseThrow());
        assertEquals(first, model.cell(second).orElseThrow().connection(Direction.WEST).orElseThrow());
        assertEquals(first, model.startCell().orElseThrow());
        assertEquals(second, model.exitSourceCell().orElseThrow());
        assertEquals(List.of(first, second), model.greenRoute());
        assertTrue(model.optimalRoute().isEmpty());
    }

    @Test
    void ignoresNonCoordinateCellGraphsWithoutFailing() {
        String trig = """
                @prefix maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#> .
                </cells/legacy> { </cells/legacy> a maze:Cell ; maze:north maze:Wall . }
                </cells/2/3> { </cells/2/3> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east maze:Wall . }
                """;

        MazeModel model = new MazeTrigParser().parse(trig);

        assertFalse(model.hasCell(new CellCoordinate(1, 1)));
        assertTrue(model.hasCell(new CellCoordinate(2, 3)));
    }

    @Test
    void parsesCorrectPlanCommentsAsOptimalRoute() {
        String trig = """
                @prefix maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#> .
                </cells/1/1> { </cells/1/1> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east maze:Wall . }
                </cells/1/2> { </cells/1/2> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east maze:Wall . }
                </cells/2/2> { </cells/2/2> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east maze:Wall . }
                #NAMED_GRAPHS_END
                #Correct plan
                    # http://127.0.1.1:8080/cells/1/1
                    # http://127.0.1.1:8080/cells/1/2
                    # http://127.0.1.1:8080/cells/2/2
                    # http://127.0.1.1:8080/cells/999
                """;

        MazeModel model = new MazeTrigParser().parse(trig);

        assertEquals(List.of(
                new CellCoordinate(1, 1),
                new CellCoordinate(1, 2),
                new CellCoordinate(2, 2)
        ), model.optimalRoute());
        assertTrue(model.cell(new CellCoordinate(2, 2)).orElseThrow().trailingGraphComment().isBlank());
        assertTrue(model.greenRoute().isEmpty());
    }

    @Test
    void reconstructsGreenRouteFromGreenSuccessors() {
        String trig = """
                @prefix maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#> .
                </cells/1/1> { </cells/1/1> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east maze:Wall; maze:green </cells/1/2> . }
                </cells/1/2> { </cells/1/2> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east maze:Wall; maze:green </cells/2/2> . }
                </cells/2/2> { </cells/2/2> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east maze:Wall . }
                """;

        MazeModel model = new MazeTrigParser().parse(trig);

        assertEquals(List.of(
                new CellCoordinate(1, 1),
                new CellCoordinate(1, 2),
                new CellCoordinate(2, 2)
        ), model.greenRoute());
        assertTrue(model.optimalRoute().isEmpty());
    }

    @Test
    void preservesInlineCustomCellPredicatesAndTrailingComments() {
        String trig = """
                @prefix maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#> .
                </cells/31/42> { </cells/31/42> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east </cells/31/43> . }
                </cells/31/43> { </cells/31/43> a maze:Cell ; maze:north maze:Wall; maze:west </cells/31/42>; maze:south maze:Wall; maze:east maze:Wall; maze:orange </cells/32/43> . } # orange signifiers for re-direct of broken cell.
                """;

        MazeModel model = new MazeTrigParser().parse(trig);
        CellCoordinate custom = new CellCoordinate(31, 43);

        assertEquals(
                List.of("maze:orange </cells/32/43>"),
                model.cell(custom).orElseThrow().customPredicateSegments()
        );
        assertEquals(
                "# orange signifiers for re-direct of broken cell.",
                model.cell(custom).orElseThrow().trailingGraphComment()
        );

        String serialized = new MazeTrigSerializer().serialize(model);

        assertTrue(serialized.contains("maze:orange </cells/32/43> . } # orange signifiers for re-direct of broken cell."));
    }

    @Test
    void preservesMultilineCustomCellGraphPayloadAndIgnoresCommentedRoutePredicates() {
        String trig = """
                @prefix maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#> .
                @prefix dyn: <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#> .
                @prefix hydra: <http://www.w3.org/ns/hydra/core#> .
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                </cells/36/36> { </cells/36/36> a maze:Cell, dyn:Lock ; hydra:operation <http://127.0.1.1:8080/cells/36/36#redAction> ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall . #maze:east </cells/36/37>; maze:green </cells/36/37>
                 <http://127.0.1.1:8080/cells/36/36#redAction> a hydra:Operation ;
                        hydra:method "POST" ;
                        hydra:target <http://127.0.1.1:8080/cells/36/36> ;
                        hydra:expects dyn:RedKeyBodyShape ;
                        hydra:returns dyn:UnlockedLock ;
                        dyn:acceptsKeyType dyn:RedKey ;
                        dyn:hasStatus dyn:open .

                 dyn:RedKeyBodyShape
                        a sh:NodeShape ;
                        sh:targetNode <http://127.0.1.1:8080/cells/36/36> ;
                        sh:property [
                            sh:path dyn:keyValue ;
                            sh:datatype xsd:string ;
                            sh:minCount 1 ;
                        ] .
                }
                """;

        MazeModel model = new MazeTrigParser().parse(trig);
        CellCoordinate lock = new CellCoordinate(36, 36);

        assertEquals(", dyn:Lock", model.cell(lock).orElseThrow().customTypeSuffix());
        assertEquals(
                List.of("hydra:operation <http://127.0.1.1:8080/cells/36/36#redAction>"),
                model.cell(lock).orElseThrow().customPredicateSegments()
        );
        assertTrue(model.greenRoute().isEmpty());

        String serialized = new MazeTrigSerializer().serialize(model);

        assertTrue(serialized.contains("a maze:Cell, dyn:Lock ;"));
        assertTrue(serialized.contains("hydra:operation <http://127.0.1.1:8080/cells/36/36#redAction>"));
        assertTrue(serialized.contains("#maze:east </cells/36/37>; maze:green </cells/36/37>"));
        assertTrue(serialized.contains("dyn:RedKeyBodyShape"));
        assertTrue(serialized.contains("sh:minCount 1"));
        assertFalse(serialized.contains("; maze:green </cells/36/37> ."));
    }

    @Test
    void preservesSeparateCustomStatementsInsideCellGraph() {
        String trig = """
                @prefix maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#> .
                @prefix dyn: <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#> .
                </cells/31/26> {   </cells/31/26> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south  maze:Wall; maze:east maze:Wall .
                     </cells/31/26#key> a dyn:RedKey;
                         dyn:fitsInLock </cells/36/36> ;
                         dyn:keyValue "redkey-1670" .
                 }
                """;

        MazeModel model = new MazeTrigParser().parse(trig);
        String serialized = new MazeTrigSerializer().serialize(model);

        assertTrue(serialized.contains("</cells/31/26#key> a dyn:RedKey;"));
        assertTrue(serialized.contains("dyn:fitsInLock </cells/36/36> ;"));
        assertTrue(serialized.contains("dyn:keyValue \"redkey-1670\" ."));
    }
}
