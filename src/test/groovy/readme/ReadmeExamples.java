package readme;

import graphql.GraphQLError;
import graphql.schema.GraphQLSchema;
import io.atlassian.fugue.Either;
import io.atlassian.graphql.schemadriven.RuntimeWiring;
import io.atlassian.graphql.schemadriven.SchemaCompiler;
import io.atlassian.graphql.schemadriven.SchemaGenerator;
import io.atlassian.graphql.schemadriven.TypeRegistry;

import java.io.File;
import java.util.List;

/**
 * This is where we put README.md examples so they compile
 */
@SuppressWarnings("unused")
public class ReadmeExamples {

    @SuppressWarnings("ConstantConditions")
    void basicExample() {

        SchemaCompiler schemaCompiler = new SchemaCompiler();
        SchemaGenerator schemaGenerator = new SchemaGenerator();

        File schemaFile = loadSchema();

        Either<List<GraphQLError>, TypeRegistry> compileResult = schemaCompiler.compile(schemaFile);

        //
        // if there are errors they are in the left projection
        if (compileResult.isLeft()) {
            List<GraphQLError> errors = compileResult.left().get();
            // report these errors some ...
        }

        // but assuming it works
        TypeRegistry typeRegistry = compileResult.right().get();
        RuntimeWiring wiring = new RuntimeWiring();
        Either<List<GraphQLError>, GraphQLSchema> generationResult = schemaGenerator.makeExecutableSchema(typeRegistry, wiring);

        //
        // again errors are reported via the left projection
        if (generationResult.isLeft()) {
            List<GraphQLError> errors = generationResult.left().get();
            // report these errors some ...
        }

        // but assuming generation works you should now have a functional graphql schema
        GraphQLSchema graphQLSchema = generationResult.right().get();

    }

    private File loadSchema() {
        return null;
    }
}
