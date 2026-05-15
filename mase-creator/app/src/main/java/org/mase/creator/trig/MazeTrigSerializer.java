package org.mase.creator.trig;

import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.Direction;
import org.mase.creator.model.MazeCell;
import org.mase.creator.model.MazeModel;

import java.util.Comparator;
import java.util.List;

public final class MazeTrigSerializer {

    private static final List<String> PREFIXES = List.of(
            "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .",
            "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .",
            "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .",
            "@prefix ldp: <http://www.w3.org/ns/ldp#> .",
            "@prefix maze: <https://kaefer3000.github.io/2021-02-dagstuhl/vocab#> .",
            "@prefix xhv: <http://www.w3.org/1999/xhtml/vocab#> .",
            "@prefix http: <http://www.w3.org/2011/http#> .",
            "@prefix httpm: <http://www.w3.org/2011/http-methods#> .",
            "@prefix prov: <http://www.w3.org/ns/prov#> .",
            "@prefix dyn: <https://paul.ti.rw.fau.de/~am52etar/dynmaze/dynmaze#> .",
            "@prefix ui: <https://example.org/ui#> .",
            "@prefix stig: <https://example.org/stigmark#> .",
            "@prefix ccrs: <https://example.org/ccrs#> .",
            "@prefix hydra: <http://www.w3.org/ns/hydra/core#> .",
            "@prefix sh: <http://www.w3.org/ns/shacl#> ."
    );

    public String serialize(MazeModel model) {
        StringBuilder trig = new StringBuilder();
        PREFIXES.forEach(prefix -> trig.append(prefix).append(System.lineSeparator()));
        trig.append(System.lineSeparator());
        appendMazeGraph(trig, model);
        trig.append(System.lineSeparator());
        trig.append("#NAMED_GRAPHS_START").append(System.lineSeparator());

        model.cells().stream()
                .sorted(Comparator.comparing(MazeCell::coordinate))
                .forEach(cell -> trig.append(renderCell(model, cell)).append(System.lineSeparator()));

        if (model.exitSourceCell().isPresent()) {
            trig.append("</cells/999> { </cells/999> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east maze:Wall . }")
                    .append(System.lineSeparator());
        }

        trig.append("#NAMED_GRAPHS_END").append(System.lineSeparator());
        return trig.toString();
    }

    private void appendMazeGraph(StringBuilder trig, MazeModel model) {
        String startIri = model.startCell()
                .or(() -> model.cells().stream()
                        .map(MazeCell::coordinate)
                        .min(CellCoordinate::compareTo))
                .map(CellCoordinate::iri)
                .orElse("</cells/1/1>");

        trig.append("</maze> {").append(System.lineSeparator());
        trig.append("    </maze> a ldp:BasicContainer , maze:CcrsMaze ;").append(System.lineSeparator());
        trig.append("        maze:transitions maze:north, maze:south, maze:east, maze:west , maze:exit ;").append(System.lineSeparator());
        trig.append("        ldp:contains </map> ;").append(System.lineSeparator());
        trig.append("        xhv:start ").append(startIri).append(" .").append(System.lineSeparator());
        trig.append("}").append(System.lineSeparator());
    }

    private String renderCell(MazeModel model, MazeCell cell) {
        String iri = cell.coordinate().iri();
        StringBuilder line = new StringBuilder();
        line.append(iri).append(" { ").append(iri).append(" a maze:Cell ; ");

        for (int i = 0; i < Direction.SERIALIZATION_ORDER.size(); i++) {
            Direction direction = Direction.SERIALIZATION_ORDER.get(i);
            line.append(direction.predicate())
                    .append(" ")
                    .append(renderTarget(model, cell, direction));
            if (i < Direction.SERIALIZATION_ORDER.size() - 1) {
                line.append("; ");
            }
        }

        if (model.exitSourceCell().filter(cell.coordinate()::equals).isPresent()) {
            line.append("; maze:exit </cells/999>");
        }

        line.append(" . }");
        return line.toString();
    }

    private String renderTarget(MazeModel model, MazeCell cell, Direction direction) {
        return cell.connection(direction)
                .filter(model::hasCell)
                .filter(target -> cell.coordinate().isAdjacent(target))
                .map(CellCoordinate::iri)
                .orElse("maze:Wall");
    }
}
