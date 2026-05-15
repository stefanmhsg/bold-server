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
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Optional;

public final class MazeEditorPanel extends JPanel {

    private static final Color EMPTY_FILL = Color.WHITE;
    private static final Color EMPTY_BORDER = new Color(218, 224, 218);
    private static final Color CELL_FILL = new Color(181, 221, 174);
    private static final Color OPTIMAL_ROUTE_FILL = new Color(255, 195, 255, 175);
    private static final Color GREEN_ROUTE_FILL = new Color(38, 151, 76, 180);
    private static final Color WALL_COLOR = Color.BLACK;
    private static final Color MARKER_COLOR = new Color(22, 76, 55);
    private static final int DEFAULT_CELL_SIZE = 28;
    private static final int WALL_MARGIN = 8;

    private final GridHitTester hitTester = new GridHitTester();
    private MazeModel model;
    private EditorTool tool = EditorTool.DRAW_PATH;
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
                handleMousePressed(event.getPoint());
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

        g.setColor(GREEN_ROUTE_FILL);
        for (CellCoordinate coordinate : model.greenRoute()) {
            RectanglePixels rect = rectangleFor(coordinate);
            int inset = Math.max(7, cellSize / 4);
            g.fillRect(rect.x() + inset, rect.y() + inset, cellSize - inset * 2, cellSize - inset * 2);
        }

        g.setStroke(new BasicStroke(3f));
        g.setColor(WALL_COLOR);
        for (MazeCell cell : model.cells()) {
            RectanglePixels rect = rectangleFor(cell.coordinate());
            paintWalls(g, cell, rect);
        }

        paintMarkers(g);
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

    private void handleMousePressed(Point point) {
        switch (tool) {
            case DRAW_PATH -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .ifPresent(coordinate -> activeStroke = model.beginPath(coordinate));
            case DRAW_OPTIMAL_ROUTE -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .filter(model::hasCell)
                    .flatMap(model::beginOptimalRoute)
                    .ifPresent(stroke -> activeOptimalRouteStroke = stroke);
            case DRAW_GREEN_ROUTE -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .filter(model::hasCell)
                    .flatMap(model::beginGreenRoute)
                    .ifPresent(stroke -> activeGreenRouteStroke = stroke);
            case DRAW_WALL -> hitTester.boundaryAt(point, model.bounds(), cellSize, WALL_MARGIN)
                    .ifPresent(hit -> model.drawWall(hit.coordinate(), hit.direction()));
            case DELETE_CELL -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .ifPresent(model::deleteCell);
            case PLACE_START -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .ifPresent(model::placeStart);
            case PLACE_EXIT -> hitTester.cellAt(point, model.bounds(), cellSize)
                    .ifPresent(model::placeExit);
        }
        repaint();
    }

    private void handleMouseDragged(Point point) {
        Optional<CellCoordinate> coordinate = hitTester.cellAt(point, model.bounds(), cellSize);
        if (coordinate.isEmpty()) {
            return;
        }

        if (tool == EditorTool.DRAW_PATH && activeStroke != null) {
            model.continuePath(activeStroke, coordinate.get());
            repaint();
        } else if (tool == EditorTool.DRAW_OPTIMAL_ROUTE && activeOptimalRouteStroke != null) {
            if (model.hasCell(coordinate.get())) {
                model.continueOptimalRoute(activeOptimalRouteStroke, coordinate.get());
                repaint();
            }
        } else if (tool == EditorTool.DRAW_GREEN_ROUTE && activeGreenRouteStroke != null) {
            if (model.hasCell(coordinate.get())) {
                model.continueGreenRoute(activeGreenRouteStroke, coordinate.get());
                repaint();
            }
        } else if (tool == EditorTool.DELETE_CELL) {
            model.deleteCell(coordinate.get());
            repaint();
        }
    }

    private record RectanglePixels(int x, int y) {
    }
}
