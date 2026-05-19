package org.mase.creator.ui;

import org.mase.creator.model.CellCoordinate;
import org.mase.creator.model.Direction;
import org.mase.creator.model.GridBounds;
import org.mase.creator.model.MazeCell;
import org.mase.creator.model.MazeModel;
import org.mase.creator.model.PathStroke;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.Optional;

public final class MazeEditorPanel extends JPanel {

    private static final Color EMPTY_FILL = Color.WHITE;
    private static final Color EMPTY_BORDER = new Color(218, 224, 218);
    private static final Color CELL_FILL = new Color(181, 221, 174);
    private static final Color OPTIMAL_ROUTE_FILL = new Color(255, 195, 255, 175);
    private static final Color GREEN_ARROW_FILL = new Color(21, 138, 72);
    private static final Color GREEN_ARROW_OUTLINE = new Color(5, 83, 45);
    private static final Color GREEN_ARROW_HALO = new Color(237, 255, 242, 230);
    private static final Color CUSTOM_CONTENT_MARKER = new Color(224, 144, 38);
    private static final Color WALL_COLOR = Color.BLACK;
    private static final Color MARKER_COLOR = new Color(22, 76, 55);
    private static final int DEFAULT_CELL_SIZE = 28;
    private static final int WALL_MARGIN = 8;

    private final GridHitTester hitTester = new GridHitTester();
    private MazeModel model;
    private EditMode editMode = EditMode.DRAW;
    private EditorTool tool = EditorTool.CELL;
    private PathStroke activeStroke;
    private PathStroke activeOptimalRouteStroke;
    private PathStroke activeGreenRouteStroke;
    private int cellSize = DEFAULT_CELL_SIZE;

    public MazeEditorPanel(MazeModel model) {
        this.model = model;
        setBackground(Color.WHITE);
        setOpaque(true);

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                handleMousePressed(event);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                handleMouseDragged(event.getPoint());
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                activeStroke = null;
                activeOptimalRouteStroke = null;
                activeGreenRouteStroke = null;
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    public void setModel(MazeModel model) {
        this.model = model;
        activeStroke = null;
        activeOptimalRouteStroke = null;
        activeGreenRouteStroke = null;
        revalidate();
        repaint();
    }

    public void setEditMode(EditMode editMode) {
        this.editMode = editMode;
        activeStroke = null;
        activeOptimalRouteStroke = null;
        activeGreenRouteStroke = null;
    }

    public EditMode editMode() {
        return editMode;
    }

    public void setTool(EditorTool tool) {
        this.tool = tool;
        activeStroke = null;
        activeOptimalRouteStroke = null;
        activeGreenRouteStroke = null;
    }

    public EditorTool tool() {
        return tool;
    }

    @Override
    public Dimension getPreferredSize() {
        GridBounds bounds = model.bounds();
        return new Dimension(bounds.yCount() * cellSize + 1, bounds.xCount() * cellSize + 1);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintEmptyGrid(g);
            paintCells(g);
        } finally {
            g.dispose();
        }
    }

    private void paintEmptyGrid(Graphics2D g) {
        GridBounds bounds = model.bounds();
        g.setColor(EMPTY_FILL);
        g.fillRect(0, 0, bounds.yCount() * cellSize, bounds.xCount() * cellSize);
        g.setColor(EMPTY_BORDER);
        g.setStroke(new BasicStroke(1f));

        for (int x = 0; x <= bounds.xCount(); x++) {
            int py = x * cellSize;
            g.drawLine(0, py, bounds.yCount() * cellSize, py);
        }
        for (int y = 0; y <= bounds.yCount(); y++) {
            int px = y * cellSize;
            g.drawLine(px, 0, px, bounds.xCount() * cellSize);
        }
    }

    private void paintCells(Graphics2D g) {
        for (MazeCell cell : model.cells()) {
            RectanglePixels rect = rectangleFor(cell.coordinate());
            g.setColor(CELL_FILL);
            g.fillRect(rect.x(), rect.y(), cellSize, cellSize);
        }

        g.setColor(OPTIMAL_ROUTE_FILL);
        for (CellCoordinate coordinate : model.optimalRoute()) {
            RectanglePixels rect = rectangleFor(coordinate);
            g.fillRect(rect.x() + 4, rect.y() + 4, cellSize - 8, cellSize - 8);
        }

        paintGreenRouteArrows(g);

        g.setColor(CUSTOM_CONTENT_MARKER);
        for (MazeCell cell : model.cells()) {
            if (cell.hasCustomContent()) {
                RectanglePixels rect = rectangleFor(cell.coordinate());
                int size = Math.max(5, cellSize / 5);
                g.fillRect(rect.x() + cellSize - size - 4, rect.y() + 4, size, size);
            }
        }

        g.setStroke(new BasicStroke(3f));
        g.setColor(WALL_COLOR);
        for (MazeCell cell : model.cells()) {
            RectanglePixels rect = rectangleFor(cell.coordinate());
            paintWalls(g, cell, rect);
        }

        paintMarkers(g);
    }

    private void paintGreenRouteArrows(Graphics2D g) {
        for (Map.Entry<CellCoordinate, CellCoordinate> successor : model.greenSuccessors().entrySet()) {
            CellCoordinate source = successor.getKey();
            if (!model.hasCell(source)) {
                continue;
            }
            directionToward(source, successor.getValue())
                    .ifPresent(direction -> drawGreenArrow(g, rectangleFor(source), direction));
        }
    }

    private Optional<Direction> directionToward(CellCoordinate source, CellCoordinate target) {
        Optional<Direction> adjacentDirection = source.directionTo(target);
        if (adjacentDirection.isPresent()) {
            return adjacentDirection;
        }

        int deltaX = target.x() - source.x();
        int deltaY = target.y() - source.y();
        if (deltaX == 0 && deltaY == 0) {
            return Optional.empty();
        }
        if (Math.abs(deltaX) >= Math.abs(deltaY)) {
            return Optional.of(deltaX < 0 ? Direction.NORTH : Direction.SOUTH);
        }
        return Optional.of(deltaY < 0 ? Direction.WEST : Direction.EAST);
    }

    private void drawGreenArrow(Graphics2D g, RectanglePixels rect, Direction direction) {
        Path2D arrow = greenArrowShape(rect, direction);
        g.setStroke(new BasicStroke(Math.max(2f, cellSize / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(GREEN_ARROW_HALO);
        g.draw(arrow);
        g.setColor(GREEN_ARROW_FILL);
        g.fill(arrow);
        g.setStroke(new BasicStroke(Math.max(1.4f, cellSize / 20f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(GREEN_ARROW_OUTLINE);
        g.draw(arrow);
    }

    private Path2D greenArrowShape(RectanglePixels rect, Direction direction) {
        double left = rect.x() + Math.max(4.0, cellSize * 0.14);
        double right = rect.x() + cellSize - Math.max(4.0, cellSize * 0.14);
        double top = rect.y() + Math.max(4.0, cellSize * 0.14);
        double bottom = rect.y() + cellSize - Math.max(4.0, cellSize * 0.14);
        double centerX = rect.x() + cellSize / 2.0;
        double centerY = rect.y() + cellSize / 2.0;
        double halfTailWidth = Math.max(4.0, cellSize * 0.17);
        double headBaseX = rect.x() + cellSize * 0.58;
        double headBaseY = rect.y() + cellSize * 0.58;

        Path2D arrow = new Path2D.Double();
        switch (direction) {
            case EAST -> {
                arrow.moveTo(left, centerY - halfTailWidth);
                arrow.lineTo(headBaseX, centerY - halfTailWidth);
                arrow.lineTo(headBaseX, top);
                arrow.lineTo(right, centerY);
                arrow.lineTo(headBaseX, bottom);
                arrow.lineTo(headBaseX, centerY + halfTailWidth);
                arrow.lineTo(left, centerY + halfTailWidth);
            }
            case WEST -> {
                double mirroredHeadBaseX = rect.x() + cellSize - (headBaseX - rect.x());
                arrow.moveTo(right, centerY - halfTailWidth);
                arrow.lineTo(mirroredHeadBaseX, centerY - halfTailWidth);
                arrow.lineTo(mirroredHeadBaseX, top);
                arrow.lineTo(left, centerY);
                arrow.lineTo(mirroredHeadBaseX, bottom);
                arrow.lineTo(mirroredHeadBaseX, centerY + halfTailWidth);
                arrow.lineTo(right, centerY + halfTailWidth);
            }
            case SOUTH -> {
                arrow.moveTo(centerX - halfTailWidth, top);
                arrow.lineTo(centerX - halfTailWidth, headBaseY);
                arrow.lineTo(left, headBaseY);
                arrow.lineTo(centerX, bottom);
                arrow.lineTo(right, headBaseY);
                arrow.lineTo(centerX + halfTailWidth, headBaseY);
                arrow.lineTo(centerX + halfTailWidth, top);
            }
            case NORTH -> {
                double mirroredHeadBaseY = rect.y() + cellSize - (headBaseY - rect.y());
                arrow.moveTo(centerX - halfTailWidth, bottom);
                arrow.lineTo(centerX - halfTailWidth, mirroredHeadBaseY);
                arrow.lineTo(left, mirroredHeadBaseY);
                arrow.lineTo(centerX, top);
                arrow.lineTo(right, mirroredHeadBaseY);
                arrow.lineTo(centerX + halfTailWidth, mirroredHeadBaseY);
                arrow.lineTo(centerX + halfTailWidth, bottom);
            }
        }
        arrow.closePath();
        return arrow;
    }

    private void paintWalls(Graphics2D g, MazeCell cell, RectanglePixels rect) {
        for (Direction direction : Direction.SERIALIZATION_ORDER) {
            if (cell.connection(direction).filter(model::hasCell).isPresent()) {
                continue;
            }

            switch (direction) {
                case NORTH -> g.drawLine(rect.x(), rect.y(), rect.x() + cellSize, rect.y());
                case WEST -> g.drawLine(rect.x(), rect.y(), rect.x(), rect.y() + cellSize);
                case SOUTH -> g.drawLine(rect.x(), rect.y() + cellSize, rect.x() + cellSize, rect.y() + cellSize);
                case EAST -> g.drawLine(rect.x() + cellSize, rect.y(), rect.x() + cellSize, rect.y() + cellSize);
            }
        }
    }

    private void paintMarkers(Graphics2D g) {
        g.setColor(MARKER_COLOR);
        g.setFont(getFont().deriveFont(Font.BOLD, 15f));
        model.startCell().ifPresent(coordinate -> drawCenteredText(g, "S", rectangleFor(coordinate)));
        model.exitSourceCell().ifPresent(coordinate -> drawCenteredText(g, "E", rectangleFor(coordinate)));
    }

    private void drawCenteredText(Graphics2D g, String text, RectanglePixels rect) {
        FontMetrics metrics = g.getFontMetrics();
        int x = rect.x() + (cellSize - metrics.stringWidth(text)) / 2;
        int y = rect.y() + ((cellSize - metrics.getHeight()) / 2) + metrics.getAscent();
        g.drawString(text, x, y);
    }

    private RectanglePixels rectangleFor(CellCoordinate coordinate) {
        GridBounds bounds = model.bounds();
        int yIndex = coordinate.y() - bounds.minY();
        int xIndex = coordinate.x() - bounds.minX();
        return new RectanglePixels(yIndex * cellSize, xIndex * cellSize);
    }

    private void handleMousePressed(MouseEvent event) {
        Point point = event.getPoint();
        if (editMode == EditMode.DRAW) {
            handleDrawPressed(point);
        } else {
            handleDeletePressed(point);
        }
        repaint();
    }

    private void handleDrawPressed(Point point) {
        switch (tool) {
            case CELL -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .ifPresent(coordinate -> activeStroke = model.beginPath(coordinate));
            case OPTIMAL_ROUTE -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .filter(model::hasCell)
                    .flatMap(model::beginOptimalRoute)
                    .ifPresent(stroke -> activeOptimalRouteStroke = stroke);
            case GREEN_ROUTE -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .filter(model::hasCell)
                    .flatMap(model::beginGreenRoute)
                    .ifPresent(stroke -> activeGreenRouteStroke = stroke);
            case WALL -> hitTester.boundaryAt(point, model.bounds(), cellSize, WALL_MARGIN)
                    .ifPresent(hit -> model.drawWall(hit.coordinate(), hit.direction()));
            case START -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .ifPresent(model::placeStart);
            case EXIT -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .ifPresent(model::placeExit);
        }
    }

    private void handleDeletePressed(Point point) {
        switch (tool) {
            case CELL -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .ifPresent(model::deleteCell);
            case WALL -> hitTester.boundaryAt(point, model.bounds(), cellSize, WALL_MARGIN)
                    .ifPresent(this::deleteWall);
            case START -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .ifPresent(model::clearStartAt);
            case EXIT -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .ifPresent(model::clearExitAt);
            case OPTIMAL_ROUTE -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .ifPresent(model::removeOptimalAt);
            case GREEN_ROUTE -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .ifPresent(model::removeGreenAt);
        }
    }

    private void handleMouseDragged(Point point) {
        Optional<CellCoordinate> coordinate = hitTester.cellAt(point, model.bounds(), cellSize);
        if (coordinate.isEmpty()) {
            return;
        }

        if (editMode == EditMode.DRAW) {
            if (tool == EditorTool.CELL && activeStroke != null) {
                model.continuePath(activeStroke, coordinate.get());
                repaint();
            } else if (tool == EditorTool.WALL) {
                hitTester.boundaryAt(point, model.bounds(), cellSize, WALL_MARGIN)
                        .ifPresent(hit -> model.drawWall(hit.coordinate(), hit.direction()));
                repaint();
            } else if (tool == EditorTool.OPTIMAL_ROUTE && activeOptimalRouteStroke != null) {
                if (model.hasCell(coordinate.get())) {
                    model.continueOptimalRoute(activeOptimalRouteStroke, coordinate.get());
                    repaint();
                }
            } else if (tool == EditorTool.GREEN_ROUTE && activeGreenRouteStroke != null) {
                if (model.hasCell(coordinate.get())) {
                    model.continueGreenRoute(activeGreenRouteStroke, coordinate.get());
                    repaint();
                }
            }
        } else {
            if (tool == EditorTool.CELL) {
                model.deleteCell(coordinate.get());
                repaint();
            } else if (tool == EditorTool.WALL) {
                hitTester.boundaryAt(point, model.bounds(), cellSize, WALL_MARGIN)
                        .ifPresent(this::deleteWall);
                repaint();
            } else if (tool == EditorTool.START) {
                model.clearStartAt(coordinate.get());
                repaint();
            } else if (tool == EditorTool.EXIT) {
                model.clearExitAt(coordinate.get());
                repaint();
            } else if (tool == EditorTool.OPTIMAL_ROUTE) {
                model.removeOptimalAt(coordinate.get());
                repaint();
            } else if (tool == EditorTool.GREEN_ROUTE) {
                model.removeGreenAt(coordinate.get());
                repaint();
            }
        }
    }

    private void deleteWall(BoundaryHit hit) {
        CellCoordinate neighbor = hit.direction().move(hit.coordinate());
        model.connectIfAdjacent(hit.coordinate(), neighbor);
    }

    private record RectanglePixels(int x, int y) {
    }
}
