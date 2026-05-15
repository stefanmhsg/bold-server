package org.mase.creator.trig;

import org.junit.jupiter.api.Test;
import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.Direction;
import org.mase.creator.model.MazeModel;

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
}
