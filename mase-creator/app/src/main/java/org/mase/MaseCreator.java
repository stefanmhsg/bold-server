package org.mase;

import org.mase.creator.ui.MazeCreatorFrame;
import org.apache.jena.query.*;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.fuseki.main.FusekiServer;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class MaseCreator {

    private static final Path INPUT_TRIG =
            dataPath("validation", "input", "MidMaze.trig");

    private static final Path QUERY_DIR =
            dataPath("validation", "query");

    private static final Path VALIDATION_DIR =
            dataPath("validation", "validate");

    private static final Path OUTPUT_TRIG =
            dataPath("validation", "output", "MaseCreator-validation.trig");

    private static final String BASE_IRI = "http://127.0.1.1:8080";

    private static Path dataPath(String mode, String directory) {
        return dataPath(mode, directory, null);
    }

    private static Path dataPath(String mode, String directory, String fileName) {
        for (Path candidate : new Path[] {
                Path.of("data"),
                Path.of("app").resolve("data"),
                Path.of("mase-creator").resolve("app").resolve("data")
        }) {
            if (Files.exists(candidate)) {
                Path path = candidate.resolve(mode).resolve(directory);
                return fileName == null ? path : path.resolve(fileName);
            }
        }

        Path path = Path.of("app").resolve("data").resolve(mode).resolve(directory);
        return fileName == null ? path : path.resolve(fileName);
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && ("--validate".equals(args[0]) || "validate".equals(args[0]))) {
            runValidationServer();
            return;
        }

        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("MASE Creator editor requires a graphical desktop.");
            System.out.println("Run with --validate to use the existing SPARQL validation/server flow.");
            return;
        }

        MazeCreatorFrame.showEditor();
    }

    private static void runValidationServer() throws IOException {

        Dataset dataset = DatasetFactory.createTxnMem();

        System.out.println("Loading base maze from input");
        RDFDataMgr.read(
                dataset,
                INPUT_TRIG.toUri().toString(),
                BASE_IRI,
                Lang.TRIG
        );

        applyUpdateQueries(dataset);
        runValidationQueries(dataset);
        exportDataset(dataset);
        exposeDataset(dataset);
    }

    private static void applyUpdateQueries(Dataset dataset) throws IOException {

        List<Path> updates =
                Files.list(QUERY_DIR)
                        .filter(p -> p.toString().endsWith(".rq"))
                        .sorted(Comparator.comparing(Path::getFileName))
                        .toList();

        try (RDFConnection conn = RDFConnection.connect(dataset)) {
            for (Path update : updates) {
                System.out.println("Applying update " + update.getFileName());
                String sparql = Files.readString(update);

                dataset.executeWrite(() -> {
                    conn.update(sparql);
                });
            }
        }
    }

    private static void runValidationQueries(Dataset dataset) throws IOException {

        if (!Files.exists(VALIDATION_DIR)) {
            System.out.println("No validation directory found, skipping validation");
            return;
        }

        List<Path> validations =
                Files.list(VALIDATION_DIR)
                        .filter(p -> p.toString().endsWith(".rq"))
                        .sorted(Comparator.comparing(Path::getFileName))
                        .toList();

        System.out.println("Running validation queries");

        for (Path validation : validations) {
            String content = Files.readString(validation);
            String[] lines = content.split("\n", 2);
            String comment = lines[0].startsWith("#") ? lines[0].substring(1).trim() : validation.getFileName().toString();

            dataset.executeRead(() -> {
                try (QueryExecution qexec =
                             org.apache.jena.query.QueryExecutionFactory.create(content, dataset)) {
                    org.apache.jena.query.ResultSet results = qexec.execSelect();
                    
                    System.out.println("Validation: " + comment);
                    while (results.hasNext()) {
                        System.out.println("  " + results.next());
                    }
                }
            });
        }
    }

    private static void exportDataset(Dataset dataset) throws IOException {

        Files.createDirectories(OUTPUT_TRIG.getParent());

        System.out.println("Exporting maze to output");

        dataset.executeRead(() -> {
            try (OutputStream out = Files.newOutputStream(OUTPUT_TRIG)) {
                org.apache.jena.riot.RDFWriter.create()
                        .base(BASE_IRI)
                        .format(org.apache.jena.riot.RDFFormat.TRIG_PRETTY)
                        .source(dataset.asDatasetGraph())
                        .output(out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Remove BASE declaration from output. RDFWriter adds it automatically.
        String content = Files.readString(OUTPUT_TRIG);
        content = content.replaceFirst("BASE\\s+<[^>]+>\\s*\n", "");
        Files.writeString(OUTPUT_TRIG, content);
    }

    private static void exposeDataset(Dataset dataset) {

        int port = 8080;
        FusekiServer server =
                FusekiServer.create()
                        .port(port)
                        .add("/", dataset)
                        .build();

        server.start();

        System.out.println("MASE creator running");
        System.out.println("SPARQL query     : " + BASE_IRI + "/query");
        System.out.println("SPARQL update    : " + BASE_IRI + "/update");
        System.out.println("GSP endpoint     : " + BASE_IRI + "/data");
        System.out.println("Dataset          : " + BASE_IRI);
        System.out.println("Example cell GSP : " + BASE_IRI + "/data?graph=" + BASE_IRI + "/cells/4/4");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping MASE creator");
            server.stop();
        }));
    }
}
