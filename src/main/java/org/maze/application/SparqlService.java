package org.maze.application;

import java.io.StringWriter;

import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.eclipse.rdf4j.query.resultio.sparqlxml.SPARQLResultsXMLWriter;
import org.eclipse.rdf4j.query.resultio.text.csv.SPARQLResultsCSVWriter;
import org.eclipse.rdf4j.query.resultio.text.tsv.SPARQLResultsTSVWriter;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.maze.domain.model.SparqlResult;
import org.maze.domain.model.SparqlResult.QueryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for executing SPARQL queries against the RDF repository.
 * Supports SELECT, CONSTRUCT, ASK, DESCRIBE, and UPDATE queries with content negotiation.
 * 
 * <p>Query types:
 * <ul>
 *   <li>SELECT - Returns tabular results (JSON, XML, CSV, TSV)</li>
 *   <li>CONSTRUCT - Returns RDF graph (Turtle, JSON-LD, RDF/XML, N-Triples)</li>
 *   <li>ASK - Returns boolean result (JSON, XML)</li>
 *   <li>DESCRIBE - Returns RDF graph (Turtle, JSON-LD, RDF/XML, N-Triples)</li>
 *   <li>UPDATE - Modifies the repository (INSERT, DELETE, etc.)</li>
 * </ul>
 */
public class SparqlService {
    
    private static final Logger log = LoggerFactory.getLogger(SparqlService.class);
    
    private final SailRepository repository;
    
    /**
     * Create a new SPARQL service.
     * 
     * @param repository the RDF repository to query against
     */
    public SparqlService(SailRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Execute a SPARQL query and return results in the requested format.
     * Opens its own connection.
     * 
     * @param queryString the SPARQL query string
     * @param acceptHeader the Accept header for content negotiation (may be null)
     * @return SparqlResult containing the query results or error
     */
    public SparqlResult executeQuery(String queryString, String acceptHeader) {
        return executeQuery(queryString, acceptHeader, null, null);
    }
    
    /**
     * Execute a SPARQL query with optional external connection and rule name.
     * 
     * @param queryString the SPARQL query string
     * @param acceptHeader the Accept header for content negotiation (may be null)
     * @param ruleName optional rule name for logging (may be null)
     * @param externalConn optional external connection for transactional atomicity (may be null)
     * @return SparqlResult containing the query results or error
     */
    public SparqlResult executeQuery(String queryString, String acceptHeader, String ruleName, SailRepositoryConnection externalConn) {
        if (ruleName != null) {
            log.info("Executing SPARQL query for rule: {}", ruleName);
        } else {
            log.info("Executing SPARQL query: {}", queryString.substring(0, Math.min(100, queryString.length())));
        }
        
        boolean ownConnection = (externalConn == null);
        SailRepositoryConnection connection = null;
        
        try {
            connection = ownConnection ? repository.getConnection() : externalConn;
            
            // Determine query type
            QueryType queryType = determineQueryType(queryString);
            
            switch (queryType) {
                case SELECT:
                    return executeSelectQuery(connection, queryString, acceptHeader);
                case CONSTRUCT:
                    return executeConstructQuery(connection, queryString, acceptHeader);
                case ASK:
                    return executeAskQuery(connection, queryString, acceptHeader);
                case DESCRIBE:
                    return executeDescribeQuery(connection, queryString, acceptHeader);
                case UPDATE:
                    return executeUpdateQuery(connection, queryString, ownConnection);
                default:
                    return SparqlResult.failed("Unknown query type", null);
            }
        } catch (Exception e) {
            log.error("Error executing SPARQL query: {}", e.getMessage(), e);
            return SparqlResult.failed(e.getMessage(), null);
        } finally {
            // Only close connection if we opened it
            if (ownConnection && connection != null) {
                connection.close();
            }
        }
    }
    
    /**
     * Determine the type of SPARQL query from the query string.
     */
    private QueryType determineQueryType(String query) {
        String normalized = query.trim().toUpperCase();
        
        // Remove comments and prefixes to find the actual query operation
        String[] lines = normalized.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            
            // Skip comments
            if (trimmed.startsWith("#")) {
                continue;
            }
            
            // Skip PREFIX declarations
            if (trimmed.startsWith("PREFIX") || trimmed.startsWith("BASE")) {
                continue;
            }
            
            // Check for query type
            if (trimmed.startsWith("SELECT")) {
                return QueryType.SELECT;
            } else if (trimmed.startsWith("CONSTRUCT")) {
                return QueryType.CONSTRUCT;
            } else if (trimmed.startsWith("ASK")) {
                return QueryType.ASK;
            } else if (trimmed.startsWith("DESCRIBE")) {
                return QueryType.DESCRIBE;
            } else if (trimmed.startsWith("INSERT") || trimmed.startsWith("DELETE") || 
                       trimmed.startsWith("LOAD") || trimmed.startsWith("CLEAR") ||
                       trimmed.startsWith("DROP") || trimmed.startsWith("CREATE") ||
                       trimmed.startsWith("ADD") || trimmed.startsWith("MOVE") ||
                       trimmed.startsWith("COPY")) {
                return QueryType.UPDATE;
            }
        }
        
        return QueryType.SELECT; // default
    }
    
    /**
     * Execute a SELECT query and return results in the requested format.
     */
    private SparqlResult executeSelectQuery(SailRepositoryConnection connection, 
                                           String queryString, String acceptHeader) {
        try {
            TupleQuery query = connection.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
            StringWriter writer = new StringWriter();
            
            // Determine result format from Accept header
            String contentType;
            if (acceptHeader != null && acceptHeader.contains("application/sparql-results+xml")) {
                contentType = "application/sparql-results+xml";
                query.evaluate(new SPARQLResultsXMLWriter(writer));
            } else if (acceptHeader != null && acceptHeader.contains("text/csv")) {
                contentType = "text/csv";
                query.evaluate(new SPARQLResultsCSVWriter(writer));
            } else if (acceptHeader != null && acceptHeader.contains("text/tab-separated-values")) {
                contentType = "text/tab-separated-values";
                query.evaluate(new SPARQLResultsTSVWriter(writer));
            } else {
                // Default to JSON
                contentType = "application/sparql-results+json";
                query.evaluate(new SPARQLResultsJSONWriter(writer));
            }
            
            log.info("SELECT query executed successfully, returning {}", contentType);
            return SparqlResult.success(writer.toString(), contentType, QueryType.SELECT);
            
        } catch (Exception e) {
            log.error("Error executing SELECT query: {}", e.getMessage(), e);
            return SparqlResult.failed(e.getMessage(), QueryType.SELECT);
        }
    }
    
    /**
     * Execute a CONSTRUCT query and return RDF graph in the requested format.
     */
    private SparqlResult executeConstructQuery(SailRepositoryConnection connection,
                                              String queryString, String acceptHeader) {
        try {
            GraphQuery query = connection.prepareGraphQuery(QueryLanguage.SPARQL, queryString);
            StringWriter writer = new StringWriter();
            
            // Determine RDF format from Accept header
            RDFFormat format;
            String contentType;
            
            if (acceptHeader != null && acceptHeader.contains("application/rdf+xml")) {
                format = RDFFormat.RDFXML;
                contentType = "application/rdf+xml";
            } else if (acceptHeader != null && acceptHeader.contains("application/n-triples")) {
                format = RDFFormat.NTRIPLES;
                contentType = "application/n-triples";
            } else if (acceptHeader != null && acceptHeader.contains("text/turtle")) {
                format = RDFFormat.TURTLE;
                contentType = "text/turtle";
            } else if (acceptHeader != null && acceptHeader.contains("application/ld+json")) {
                format = RDFFormat.JSONLD;
                contentType = "application/ld+json";
            } else {
                // Default to JSON-LD
                format = RDFFormat.JSONLD;
                contentType = "application/ld+json";
            }
            
            RDFWriter rdfWriter = Rio.createWriter(format, writer);
            query.evaluate(rdfWriter);
            
            log.info("CONSTRUCT query executed successfully, returning {}", contentType);
            return SparqlResult.success(writer.toString(), contentType, QueryType.CONSTRUCT);
            
        } catch (Exception e) {
            log.error("Error executing CONSTRUCT query: {}", e.getMessage(), e);
            return SparqlResult.failed(e.getMessage(), QueryType.CONSTRUCT);
        }
    }
    
    /**
     * Execute an ASK query and return boolean result.
     */
    private SparqlResult executeAskQuery(SailRepositoryConnection connection,
                                        String queryString, String acceptHeader) {
        try {
            BooleanQuery query = connection.prepareBooleanQuery(QueryLanguage.SPARQL, queryString);
            boolean result = query.evaluate();
            
            // Return result in SPARQL JSON Results format
            String contentType = "application/sparql-results+json";
            String jsonResult = String.format("{\"head\":{},\"boolean\":%s}", result);
            
            log.info("ASK query executed successfully, result: {}", result);
            return SparqlResult.success(jsonResult, contentType, QueryType.ASK);
            
        } catch (Exception e) {
            log.error("Error executing ASK query: {}", e.getMessage(), e);
            return SparqlResult.failed(e.getMessage(), QueryType.ASK);
        }
    }
    
    /**
     * Execute a DESCRIBE query and return RDF graph in the requested format.
     */
    private SparqlResult executeDescribeQuery(SailRepositoryConnection connection,
                                             String queryString, String acceptHeader) {
        try {
            GraphQuery query = connection.prepareGraphQuery(QueryLanguage.SPARQL, queryString);
            StringWriter writer = new StringWriter();
            
            // Determine RDF format from Accept header (same as CONSTRUCT)
            RDFFormat format;
            String contentType;
            
            if (acceptHeader != null && acceptHeader.contains("application/rdf+xml")) {
                format = RDFFormat.RDFXML;
                contentType = "application/rdf+xml";
            } else if (acceptHeader != null && acceptHeader.contains("application/n-triples")) {
                format = RDFFormat.NTRIPLES;
                contentType = "application/n-triples";
            } else if (acceptHeader != null && acceptHeader.contains("text/turtle")) {
                format = RDFFormat.TURTLE;
                contentType = "text/turtle";
            } else if (acceptHeader != null && acceptHeader.contains("application/ld+json")) {
                format = RDFFormat.JSONLD;
                contentType = "application/ld+json";
            } else {
                // Default to JSON-LD
                format = RDFFormat.JSONLD;
                contentType = "application/ld+json";
            }
            
            RDFWriter rdfWriter = Rio.createWriter(format, writer);
            query.evaluate(rdfWriter);
            
            log.info("DESCRIBE query executed successfully, returning {}", contentType);
            return SparqlResult.success(writer.toString(), contentType, QueryType.DESCRIBE);
            
        } catch (Exception e) {
            log.error("Error executing DESCRIBE query: {}", e.getMessage(), e);
            return SparqlResult.failed(e.getMessage(), QueryType.DESCRIBE);
        }
    }
    
    /**
     * Execute an UPDATE query (INSERT, DELETE, etc.) with optional transaction management.
     * 
     * @param connection the repository connection
     * @param queryString the SPARQL UPDATE query
     * @param ownTransaction true if this method should manage begin/commit/rollback, false if external transaction
     * @return SparqlResult containing success or error
     */
    private SparqlResult executeUpdateQuery(SailRepositoryConnection connection, String queryString, boolean ownTransaction) {
        try {
            if (ownTransaction) {
                connection.begin();
            }
            
            Update update = connection.prepareUpdate(QueryLanguage.SPARQL, queryString);
            update.execute();
            
            if (ownTransaction) {
                connection.commit();
            }
            
            log.info("UPDATE query executed successfully");
            
            // Return success message in JSON format
            String contentType = "application/json";
            String jsonResult = "{\"message\":\"Update executed successfully\"}";
            
            return SparqlResult.success(jsonResult, contentType, QueryType.UPDATE);
            
        } catch (Exception e) {
            if (ownTransaction) {
                connection.rollback();
            }
            log.error("Error executing UPDATE query: {}", e.getMessage(), e);
            return SparqlResult.failed(e.getMessage(), QueryType.UPDATE);
        }
    }
}
