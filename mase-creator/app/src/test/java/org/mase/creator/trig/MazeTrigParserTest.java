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
}
