package org.mase.creator.ui;

import org.mase.creator.autosave.AutoSaveService;
import org.mase.creator.model.MazeModel;
import org.mase.creator.scenario.ScenarioPackageExportResult;
import org.mase.creator.scenario.ScenarioPackageExporter;
import org.mase.creator.scenario.ScenarioPackageNames;
import org.mase.creator.trig.MazeTrigParser;
import org.mase.creator.trig.MazeTrigSerializer;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class MazeCreatorFrame extends JFrame {

    private static final Path AUTO_SAVE_PATH = dataPath("editor", "autosave", "MaseCreator-autosave.trig");
    private static final Path OUTPUT_DIRECTORY = dataPath("editor", "output", ScenarioPackageExporter.DEFAULT_PACKAGE_NAME).getParent();

    private final MazeTrigParser parser = new MazeTrigParser();
    private final MazeTrigSerializer serializer = new MazeTrigSerializer();
    private final ScenarioPackageExporter packageExporter = new ScenarioPackageExporter(serializer);
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
        xSpinner.addChangeListener(event -> resizeModelFromSpinners());
        ySpinner.addChangeListener(event -> resizeModelFromSpinners());

        return MazeToolbarFactory.create(
                new MazeToolbarFactory.Actions(
                        this::newBlankModel,
                        this::openTrigFile,
                        this::restoreAutoSave,
                        this::saveScenarioPackage,
                        this::eraseAll,
                        this::clearOptimalRoute,
                        this::clearGreenRoute
                ),
                editorPanel,
                xSpinner,
                ySpinner
        );
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

    private void saveScenarioPackage() {
        try {
            Files.createDirectories(OUTPUT_DIRECTORY);
            Optional<PackageSelection> selectedPackage = selectPackageExport();
            if (selectedPackage.isEmpty()) {
                statusLabel.setText("Create Package canceled");
                return;
            }

            PackageSelection selection = selectedPackage.get();
            ScenarioPackageExportResult result = packageExporter.export(
                    model,
                    OUTPUT_DIRECTORY,
                    selection.packageName(),
                    selection.replaceExisting()
            );
            Path absolutePath = result.packageRoot();
            statusLabel.setText("Created package " + absolutePath);
            JOptionPane.showMessageDialog(
                    this,
                    "Scenario package generated:" + System.lineSeparator() + absolutePath,
                    "MASE Creator",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (IOException e) {
            showError("Could not create scenario package", e);
        }
    }

    private Optional<PackageSelection> selectPackageExport() {
        Path defaultPackage = OUTPUT_DIRECTORY.resolve(ScenarioPackageExporter.DEFAULT_PACKAGE_NAME);
        if (!Files.exists(defaultPackage)) {
            return Optional.of(new PackageSelection(ScenarioPackageExporter.DEFAULT_PACKAGE_NAME, false));
        }

        String message = "A scenario package already exists:" + System.lineSeparator()
                + defaultPackage.toAbsolutePath().normalize() + System.lineSeparator()
                + System.lineSeparator()
                + "Overwrite it or create a differently named package in the same output directory?";
        int choice = JOptionPane.showOptionDialog(
                this,
                message,
                "Create Package",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[] {"Overwrite", "Adjust Name", "Cancel"},
                "Adjust Name"
        );

        if (choice == JOptionPane.YES_OPTION) {
            return Optional.of(new PackageSelection(ScenarioPackageExporter.DEFAULT_PACKAGE_NAME, true));
        }
        if (choice == JOptionPane.NO_OPTION) {
            return promptForPackageName();
        }
        return Optional.empty();
    }

    private Optional<PackageSelection> promptForPackageName() {
        String suggestedName = ScenarioPackageNames.nextAvailablePackageName(
                OUTPUT_DIRECTORY,
                ScenarioPackageExporter.DEFAULT_PACKAGE_NAME);

        while (true) {
            Object input = JOptionPane.showInputDialog(
                    this,
                    "Package folder name in " + OUTPUT_DIRECTORY.toAbsolutePath().normalize() + ":",
                    "Create Package",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    null,
                    suggestedName
            );
            if (input == null) {
                return Optional.empty();
            }

            String packageName;
            try {
                packageName = ScenarioPackageNames.normalizePackageDirectoryName(input.toString());
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "MASE Creator", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            Path selected = OUTPUT_DIRECTORY.resolve(packageName);
            if (!Files.exists(selected)) {
                return Optional.of(new PackageSelection(packageName, false));
            }
            if (confirmOverwrite(selected)) {
                return Optional.of(new PackageSelection(packageName, true));
            }
            suggestedName = ScenarioPackageNames.nextAvailablePackageName(OUTPUT_DIRECTORY, packageName);
        }
    }

    private boolean confirmOverwrite(Path path) {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "A scenario package already exists:" + System.lineSeparator()
                        + path.toAbsolutePath().normalize() + System.lineSeparator()
                        + System.lineSeparator()
                        + "Overwrite it?",
                "Create Package",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return choice == JOptionPane.YES_OPTION;
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

    private record PackageSelection(String packageName, boolean replaceExisting) {
    }
}
