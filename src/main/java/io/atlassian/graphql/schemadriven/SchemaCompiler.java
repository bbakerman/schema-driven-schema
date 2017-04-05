package io.atlassian.graphql.schemadriven;

import graphql.GraphQLError;
import graphql.InvalidSyntaxError;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.SourceLocation;
import graphql.parser.Parser;
import io.atlassian.fugue.Either;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SchemaCompiler {

    public Either<List<GraphQLError>, TypeRegistry> read(URL url) {
        try {
            return read(IOUtils.toString(url.openStream(), Charset.defaultCharset()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Either<List<GraphQLError>, TypeRegistry> read(File file) {
        try {
            return read(new FileReader(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public Either<List<GraphQLError>, TypeRegistry> read(Reader reader) {
        try {
            return read(IOUtils.toString(reader));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Either<List<GraphQLError>, TypeRegistry> read(String schemaInput) {
        try {
            Parser parser = new Parser();
            Document document = parser.parseDocument(schemaInput);

            return buildRegistry(document);
        } catch (ParseCancellationException e) {
            return handleParseException(e);
        }
    }

    private Either<List<GraphQLError>, TypeRegistry> handleParseException(ParseCancellationException e) {
        RecognitionException recognitionException = (RecognitionException) e.getCause();
        SourceLocation sourceLocation = new SourceLocation(recognitionException.getOffendingToken().getLine(), recognitionException.getOffendingToken().getCharPositionInLine());
        InvalidSyntaxError invalidSyntaxError = new InvalidSyntaxError(sourceLocation);
        return Either.left(Collections.singletonList(invalidSyntaxError));
    }

    private Either<List<GraphQLError>, TypeRegistry> buildRegistry(Document document) {
        List<GraphQLError> errors = new ArrayList<>();
        TypeRegistry typeRegistry = new TypeRegistry();
        List<Definition> definitions = document.getDefinitions();
        for (Definition definition : definitions) {
            typeRegistry.add(definition).forEach(errors::add);
        }
        if (errors.size() > 0) {
            return Either.left(errors);
        } else {
            return Either.right(typeRegistry);
        }
    }
}
