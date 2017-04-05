package io.atlassian.graphql.schemadriven.errors;

import graphql.language.SchemaDefinition;

import static java.lang.String.format;

public class SchemaRedefinitionError extends BaseError {

    public SchemaRedefinitionError(SchemaDefinition oldEntry, SchemaDefinition newEntry) {
        super(oldEntry, format("There is already a schema defined %s.  The offending new new ones is here %s",
                lineCol(oldEntry), lineCol(newEntry)));
    }
}
