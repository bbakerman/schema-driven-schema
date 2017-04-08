package io.atlassian.graphql.schemadriven.errors;

import graphql.language.TypeDefinition;

import static java.lang.String.format;

public class MissingTypeResolverError extends BaseError {

    public MissingTypeResolverError(TypeDefinition typeDefinition) {
        super(typeDefinition, format("There is no type resolver defined for interface / union '%s' type", typeDefinition.getName()));
    }

}
