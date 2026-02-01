package org.mase;

import org.apache.jena.query.*;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.fuseki.main.FusekiServer;

import java.nio.file.Path;

public class MaseCreator {

    private static final Path INPUT_TRIG =
            Path.of("app/data/input/maze.trig");

    private static final Path QUERY_DIR =
            Path.of("app/data/query");

    private static final Path VALIDATION_DIR =
            Path.of("app/data/validate");

    private static final Path OUTPUT_TRIG =
            Path.of("app/data/output/maze.generated.trig");

    public static void main(String[] args) throws IOException {

        Dataset dataset = DatasetFactory.createTxnMem();

        System.out.println("Loading base maze from input");
        RDFDataMgr.read(
                dataset,
                INPUT_TRIG.toUri().toString(),
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
                        .filter(p -> p.toString().endsWith(".ru"))
                        .sorted(Comparator.comparing(Path::getFileName))
                        .toList();

        try (RDFConnection conn = RDFConnection.connect(dataset)) {
            for (Path update : updates) {
                System.out.println("Applying update " + update.getFileName());
                String sparql = Files.readString(update);

                Txn.executeWrite(dataset, () -> {
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

        boolean valid = true;

        for (Path validation : validations) {
            String sparql = Files.readString(validation);

            boolean result = Txn.calculateRead(dataset, () -> {
                try (QueryExecution qexec =
                             org.apache.jena.query.QueryExecutionFactory.create(sparql, dataset)) {
                    return qexec.execAsk();
                }
            });

            System.out.println(
                    "Validation " + validation.getFileName() + " -> " + result
            );

            if (!result) {
                valid = false;
            }
        }

        if (!valid) {
            System.err.println("Maze validation failed");
        } else {
            System.out.println("Maze validation passed");
        }
    }

    private static void exportDataset(Dataset dataset) throws IOException {

        Files.createDirectories(OUTPUT_TRIG.getParent());

        System.out.println("Exporting maze to output");

        Txn.executeRead(dataset, () -> {
            try (OutputStream out = Files.newOutputStream(OUTPUT_TRIG)) {
                RDFDataMgr.write(out, dataset, Lang.TRIG);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void exposeDataset(Dataset dataset) {

        FusekiServer server =
                FusekiServer.create()
                        .add("/maze", dataset)
                        .build();

        server.start();


        System.out.println("MASE creator running");
        System.out.println("SPARQL endpoint  : http://localhost:3030/mase/sparql");
        System.out.println("GSP endpoint     : http://localhost:3030/mase/data");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping MASE creator");
            server.stop();
        }));
    }
}
