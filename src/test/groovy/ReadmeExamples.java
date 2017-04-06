import graphql.GraphQLError;
import graphql.schema.GraphQLSchema;
import io.atlassian.fugue.Either;
import io.atlassian.graphql.schemadriven.SchemaCompiler;
import io.atlassian.graphql.schemadriven.SchemaGenerator;
import io.atlassian.graphql.schemadriven.TypeRegistry;

import java.io.File;
import java.util.List;

/**
 * TODO: Document this class / interface here
 *
 * @since v0.x
 */
@SuppressWarnings("unused")
public class ReadmeExamples {

    public static final String SCHEMA_SPECIFICATION = "" +
            "type Author {\n" +
            "  id: Int!\n" +
            "  firstName: String\n" +
            "  lastName: String\n" +
            "  posts: [Post] \n" +
            "}\n" +
            "\n" +
            "type Post {\n" +
            "  id: Int!\n" +
            "  title: String\n" +
            "  votes: Int\n" +
            "  author: Author\n" +
            "}\n" +
            "\n" +
            "type Query {\n" +
            "  posts: [Post]\n" +
            "  author(id: Int!): Author \n" +
            "}\n" +
            "\n" +
            "\n" +
            "type Mutation {\n" +
            "  upvotePost (\n" +
            "    postId: Int!\n" +
            "  ): Post\n" +
            "}\n" +
            "\n" +
            "schema {\n" +
            "  query: Query\n" +
            "  mutation: Mutation\n" +
            "}";

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
        Either<List<GraphQLError>, GraphQLSchema> generationResult = schemaGenerator.makeExecutableSchema(typeRegistry);

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
