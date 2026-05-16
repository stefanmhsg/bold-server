package org.mase.creator.trig;

import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.Direction;
import org.mase.creator.model.MazeCell;
import org.mase.creator.model.MazeModel;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MazeTrigSerializer {

    private static final String BASE_IRI = "http://127.0.1.1:8080";

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
        appendPreservedDocumentBlocks(trig, model);
        appendMazeGraph(trig, model);
        trig.append(System.lineSeparator());
        trig.append("#NAMED_GRAPHS_START").append(System.lineSeparator());
        Map<CellCoordinate, CellCoordinate> greenSuccessors = greenSuccessors(model);

        model.cells().stream()
                .sorted(Comparator.comparing(MazeCell::coordinate))
                .forEach(cell -> trig.append(renderCell(model, cell, greenSuccessors)).append(System.lineSeparator()));

        if (model.exitSourceCell().isPresent()) {
            trig.append("</cells/999> { </cells/999> a maze:Cell ; maze:north maze:Wall; maze:west maze:Wall; maze:south maze:Wall; maze:east maze:Wall . }")
                    .append(System.lineSeparator());
        }

        trig.append("#NAMED_GRAPHS_END").append(System.lineSeparator());
        appendCorrectPlan(trig, model);
        return trig.toString();
    }

    private void appendMazeGraph(StringBuilder trig, MazeModel model) {
        String startIri = model.startCell()
                .map(CellCoordinate::iri)
                .orElseGet(() -> model.rawStartIri()
                        .orElseGet(() -> model.cells().stream()
                                .map(MazeCell::coordinate)
                                .min(CellCoordinate::compareTo)
                                .map(CellCoordinate::iri)
                                .orElse("</cells/1/1>")));

        trig.append("</maze> {").append(System.lineSeparator());
        trig.append("    </maze> a ldp:BasicContainer , maze:CcrsMaze ;").append(System.lineSeparator());
        trig.append("        maze:transitions maze:north, maze:south, maze:east, maze:west , maze:exit ;").append(System.lineSeparator());
        trig.append("        ldp:contains </map> ;").append(System.lineSeparator());
        trig.append("        xhv:start ").append(startIri).append(" .").append(System.lineSeparator());
        trig.append("}").append(System.lineSeparator());
    }

    private void appendPreservedDocumentBlocks(StringBuilder trig, MazeModel model) {
        for (String block : model.preservedDocumentBlocks()) {
            if (!block.isBlank()) {
                trig.append(block.stripTrailing()).append(System.lineSeparator()).append(System.lineSeparator());
            }
        }
    }

    private String renderCell(
            MazeModel model,
            MazeCell cell,
            Map<CellCoordinate, CellCoordinate> greenSuccessors
    ) {
        String iri = cell.coordinate().iri();
        String firstStatement = renderCellStatement(model, cell, greenSuccessors);
        if (!cell.customGraphTail().isBlank()) {
            StringBuilder block = new StringBuilder();
            block.append(iri).append(" { ").append(firstStatement);
            if (!startsWithWhitespace(cell.customGraphTail())) {
                block.append(" ");
            }
            block.append(cell.customGraphTail()).append(System.lineSeparator()).append("}");
            appendTrailingGraphComment(block, cell);
            return block.toString();
        }

        StringBuilder line = new StringBuilder();
        line.append(iri).append(" { ").append(firstStatement).append(" }");
        appendTrailingGraphComment(line, cell);
        return line.toString();
    }

    private String renderCellStatement(
            MazeModel model,
            MazeCell cell,
            Map<CellCoordinate, CellCoordinate> greenSuccessors
    ) {
        String iri = cell.coordinate().iri();
        StringBuilder statement = new StringBuilder();
        statement.append(iri).append(" a maze:Cell");
        appendCustomTypeSuffix(statement, cell.customTypeSuffix());
        statement.append(" ; ");

        for (int i = 0; i < Direction.SERIALIZATION_ORDER.size(); i++) {
            Direction direction = Direction.SERIALIZATION_ORDER.get(i);
            statement.append(direction.predicate())
                    .append(" ")
                    .append(renderTarget(model, cell, direction));
            if (i < Direction.SERIALIZATION_ORDER.size() - 1) {
                statement.append("; ");
            }
        }

        if (model.exitSourceCell().filter(cell.coordinate()::equals).isPresent()) {
            statement.append("; maze:exit </cells/999>");
        }

        CellCoordinate greenSuccessor = greenSuccessors.get(cell.coordinate());
        if (greenSuccessor != null) {
            statement.append("; maze:green ").append(greenSuccessor.iri());
        }

        for (String segment : cell.customPredicateSegments()) {
            statement.append("; ").append(segment);
        }

        statement.append(" .");
        return statement.toString();
    }

    private void appendCustomTypeSuffix(StringBuilder statement, String customTypeSuffix) {
        if (customTypeSuffix.isBlank()) {
            return;
        }
        if (customTypeSuffix.startsWith(",") || customTypeSuffix.startsWith(";")) {
            statement.append(customTypeSuffix);
        } else {
            statement.append(" ").append(customTypeSuffix);
        }
    }

    private boolean startsWithWhitespace(String value) {
        return !value.isEmpty() && Character.isWhitespace(value.charAt(0));
    }

    private void appendTrailingGraphComment(StringBuilder output, MazeCell cell) {
        if (!cell.trailingGraphComment().isBlank()) {
            output.append(" ").append(cell.trailingGraphComment());
        }
    }

    private String renderTarget(MazeModel model, MazeCell cell, Direction direction) {
        return cell.connection(direction)
                .filter(model::hasCell)
                .filter(target -> cell.coordinate().isAdjacent(target))
                .map(CellCoordinate::iri)
                .orElseGet(() -> cell.preservedDirectionTarget(direction).orElse("maze:Wall"));
    }

    private Map<CellCoordinate, CellCoordinate> greenSuccessors(MazeModel model) {
        return model.greenRoutes().stream()
                .flatMap(route -> java.util.stream.IntStream.range(0, Math.max(0, route.size() - 1))
                        .mapToObj(index -> Map.entry(route.get(index), route.get(index + 1))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, ignored) -> first));
    }

    private void appendCorrectPlan(StringBuilder trig, MazeModel model) {
        List<String> preservedLines = model.preservedCorrectPlanLines();
        if (!preservedLines.isEmpty()) {
            for (String line : preservedLines) {
                trig.append(line).append(System.lineSeparator());
            }
            return;
        }

        List<CellCoordinate> route = model.optimalRoute();
        if (route.isEmpty()) {
            return;
        }

        trig.append("#Correct plan").append(System.lineSeparator());
        for (CellCoordinate coordinate : route) {
            trig.append("    # ")
                    .append(BASE_IRI)
                    .append(coordinate.path())
                    .append(System.lineSeparator());
        }

        if (model.exitSourceCell().filter(route.get(route.size() - 1)::equals).isPresent()) {
            trig.append("    # ")
                    .append(BASE_IRI)
                    .append("/cells/999")
                    .append(System.lineSeparator());
        }
    }
}
