package io.atlassian.graphql.schemadriven.errors;

public class QueryOperationMissingError extends BaseError {

    public QueryOperationMissingError() {
        super(null, "A schema MUST have a 'query' operation defined");
    }
}
