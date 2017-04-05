package io.atlassian.graphql.schemadriven.errors;

public class SchemaMissingError extends BaseError {

    public SchemaMissingError() {
        super(null, "There is no ttop level schema object defined");
    }
}
