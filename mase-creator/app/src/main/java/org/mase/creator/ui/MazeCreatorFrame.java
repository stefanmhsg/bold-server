package org.mase.creator.ui;

import org.mase.creator.autosave.AutoSaveService;
import org.mase.creator.model.MazeModel;
import org.mase.creator.trig.MazeTrigParser;
import org.mase.creator.trig.MazeTrigSerializer;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;

public final class MazeCreatorFrame extends JFrame {

    private static final Path AUTO_SAVE_PATH = dataPath("editor", "autosave", "MaseCreator-autosave.trig");
    private static final Path OUTPUT_PATH = dataPath("editor", "output", "MaseCreator.trig");

    private final MazeTrigParser parser = new MazeTrigParser();
    private final MazeTrigSerializer serializer = new MazeTrigSerializer();
    private final AutoSaveService autoSaveService = new AutoSaveService(AUTO_SAVE_PATH, parser, serializer);
    private final MazeEditorPanel editorPanel;
    private final JLabel statusLabel = new JLabel("Ready");
    private final JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 999, 1));
    private final JSpinner ySpinner = new JSpinner(new SpinnerNumberModel(24, 1, 999, 1));
    private MazeModel model;
    private Runnable modelListener;
    private boolean syncingSpinners;

    public MazeCreatorFrame() {
        super("MASE Creator");
        model = MazeModel.blank(24, 24);
        editorPanel = new MazeEditorPanel(model);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(createToolbar(), BorderLayout.NORTH);
        add(new JScrollPane(editorPanel), BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        attachModel(model);
        setSize(1100, 820);
        setLocationByPlatform(true);
    }

    public static void showEditor() {
        SwingUtilities.invokeLater(() -> new MazeCreatorFrame().setVisible(true));
    }

    private static Path dataPath(String mode, String directory, String fileName) {
        for (Path candidate : new Path[] {
                Path.of("data"),
                Path.of("app").resolve("data"),
                Path.of("mase-creator").resolve("app").resolve("data")
        }) {
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.resolve(mode).resolve(directory).resolve(fileName);
            }
        }
        return Path.of("app").resolve("data").resolve(mode).resolve(directory).resolve(fileName);
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton newButton = new JButton("New");
        newButton.addActionListener(event -> newBlankModel());
        toolbar.add(newButton);

        JButton eraseAllButton = new JButton("Erase All");
        eraseAllButton.addActionListener(event -> eraseAll());
        toolbar.add(eraseAllButton);

        JButton clearOptimalRouteButton = new JButton("Clear Optimal");
        clearOptimalRouteButton.addActionListener(event -> clearOptimalRoute());
        toolbar.add(clearOptimalRouteButton);

        JButton clearGreenRouteButton = new JButton("Clear maze:green");
        clearGreenRouteButton.addActionListener(event -> clearGreenRoute());
        toolbar.add(clearGreenRouteButton);

        JButton restoreButton = new JButton("Restore Auto-Save");
        restoreButton.addActionListener(event -> restoreAutoSave());
        toolbar.add(restoreButton);

        JButton openButton = new JButton("Open");
        openButton.addActionListener(event -> openTrigFile());
        toolbar.add(openButton);

        JButton createButton = new JButton("Create Maze");
        createButton.addActionListener(event -> saveTrigFile());
        toolbar.add(createButton);

        toolbar.addSeparator();
        ButtonGroup tools = new ButtonGroup();
        for (EditorTool tool : EditorTool.values()) {
            JToggleButton button = new JToggleButton(tool.label());
            button.addActionListener(event -> editorPanel.setTool(tool));
            tools.add(button);
            toolbar.add(button);
            if (tool == EditorTool.DRAW_PATH) {
                button.setSelected(true);
            }
        }

        toolbar.addSeparator();
        toolbar.add(new JLabel("X"));
        toolbar.add(xSpinner);
        toolbar.add(new JLabel("Y"));
        toolbar.add(ySpinner);

        xSpinner.addChangeListener(event -> resizeModelFromSpinners());
        ySpinner.addChangeListener(event -> resizeModelFromSpinners());

        return toolbar;
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(statusLabel);
        return panel;
    }

    private void attachModel(MazeModel nextModel) {
        if (modelListener != null) {
            model.removeChangeListener(modelListener);
        }

        model = nextModel;
        modelListener = () -> {
            editorPanel.revalidate();
            editorPanel.repaint();
            autoSave();
        };
        model.addChangeListener(modelListener);
        editorPanel.setModel(model);
        syncSpinnersToModel();
    }

    private void newBlankModel() {
        int xCount = (Integer) xSpinner.getValue();
        int yCount = (Integer) ySpinner.getValue();
        attachModel(MazeModel.blank(xCount, yCount));
        autoSave();
        statusLabel.setText("New maze");
    }

    private void eraseAll() {
        attachModel(MazeModel.blank(model.xCount(), model.yCount()));
        autoSave();
        statusLabel.setText("Canvas erased");
    }

    private void clearOptimalRoute() {
        model.clearOptimalRoute();
        statusLabel.setText("Optimal route cleared");
    }

    private void clearGreenRoute() {
        model.clearGreenRoute();
        statusLabel.setText("maze:green route cleared");
    }

    private void restoreAutoSave() {
        try {
            MazeModel restored = autoSaveService.load().orElse(null);
            if (restored == null) {
                statusLabel.setText("No auto-save found");
                return;
            }
            attachModel(restored);
            statusLabel.setText("Restored auto-save");
        } catch (IOException e) {
            showError("Could not restore auto-save", e);
        }
    }

    private void openTrigFile() {
        JFileChooser chooser = trigChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            MazeModel loaded = parser.parse(chooser.getSelectedFile().toPath());
            attachModel(loaded);
            autoSave();
            statusLabel.setText("Loaded " + chooser.getSelectedFile().getName());
        } catch (IOException e) {
            showError("Could not load TriG file", e);
        }
    }

    private void saveTrigFile() {
        try {
            java.nio.file.Files.createDirectories(OUTPUT_PATH.getParent());
            java.nio.file.Files.writeString(OUTPUT_PATH, serializer.serialize(model));
            statusLabel.setText("Created " + OUTPUT_PATH);
        } catch (IOException e) {
            showError("Could not create maze file", e);
        }
    }

    private JFileChooser trigChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(Path.of("").toAbsolutePath().toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("TriG files", "trig"));
        return chooser;
    }

    private void resizeModelFromSpinners() {
        if (model == null || syncingSpinners) {
            return;
        }
        int xCount = (Integer) xSpinner.getValue();
        int yCount = (Integer) ySpinner.getValue();
        model.setBoundsByCellCounts(xCount, yCount);
    }

    private void syncSpinnersToModel() {
        syncingSpinners = true;
        try {
            xSpinner.setValue(model.xCount());
            ySpinner.setValue(model.yCount());
        } finally {
            syncingSpinners = false;
        }
    }

    private void autoSave() {
        try {
            autoSaveService.save(model);
            statusLabel.setText("Auto-saved to " + autoSaveService.autoSavePath());
        } catch (IOException e) {
            statusLabel.setText("Auto-save failed: " + e.getMessage());
        }
    }

    private void showError(String message, IOException e) {
        JOptionPane.showMessageDialog(this, message + System.lineSeparator() + e.getMessage(),
                "MASE Creator", JOptionPane.ERROR_MESSAGE);
    }
}
