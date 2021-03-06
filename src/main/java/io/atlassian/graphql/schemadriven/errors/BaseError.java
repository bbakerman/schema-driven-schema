package io.atlassian.graphql.schemadriven.errors;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.Node;
import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.List;

class BaseError extends RuntimeException implements GraphQLError {
    protected static final SourceLocation NO_WHERE = new SourceLocation(-1, -1);

    private Node node;

    public BaseError(Node node, String msg) {
        super(msg);
        this.node = node;
    }

    public static String lineCol(Node node) {
        SourceLocation sourceLocation = node.getSourceLocation() == null ? NO_WHERE : node.getSourceLocation();
        return String.format("[@%d:%d]", sourceLocation.getLine(), sourceLocation.getColumn());
    }

    @Override
    public List<SourceLocation> getLocations() {
        return node == null ? Collections.singletonList(NO_WHERE) : Collections.singletonList(node.getSourceLocation());
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }

    @Override
    public String toString() {
        return "BaseError{" +
                "msg='" + getMessage() + '\'' +
                ", node=" + node +
                '}';
    }
}
