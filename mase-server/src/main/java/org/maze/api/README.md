# MASE API

This package contains the HTTP API resources for linked-data graph dereferencing, SPARQL access, admin snapshots, and WebSocket events.

## Error Responses

[LinkedDataDereferenceResource.java](ld/LinkedDataDereferenceResource.java) handles linked-data `GET` and `POST` responses, and [SparqlResource.java](sparql/SparqlResource.java) handles `/sparql` responses. Both keep the existing HTTP status codes and error message strings.

If the request `Accept` header explicitly asks for an RDF serialization (`text/turtle`, `application/ld+json`, `application/rdf+xml`, or `application/n-triples`), error responses are serialized as RDF by [ErrorResponseBuilder.java](ErrorResponseBuilder.java). If no RDF media type is requested, linked-data responses fall back to the legacy `text/plain` body and `/sparql` responses fall back to the legacy `application/json` body.

The RDF error body attaches the unchanged message to the targeted resource or endpoint. The vocabulary terms are defined in [MazeVocab.java](../domain/vocab/MazeVocab.java).

```turtle
@prefix mase: <https://example.org/mase#> .
@prefix http: <http://www.w3.org/2011/http#> .

<http://127.0.1.1:8080/cells/0/1>
  mase:errorMessage "Access denied. Cell http://127.0.1.1:8080/cells/0/1 is not accessible from http://127.0.1.1:8080/cells/0/0 (no connection in graph)" ;
  mase:errorStatusCode 403 ;
  http:statusCodeValue "403" .
```

Agents that want RDF errors on `POST` should send an `Accept` header as well as `Content-Type`, for example `Accept: text/turtle, text/plain;q=0.1`.
