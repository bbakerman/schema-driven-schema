import graphql.GraphQLError
import io.atlassian.fugue.Either
import io.atlassian.graphql.schemadriven.SchemaCompiler
import io.atlassian.graphql.schemadriven.SchemaGenerator
import io.atlassian.graphql.schemadriven.TypeRegistry
import spock.lang.Specification

class SchemaGeneratorTest extends Specification {

    Either<List<GraphQLError>, TypeRegistry> compile(String types) {
        new SchemaCompiler().read(types)
    }

    def "test simple schema generate"() {

        def schemaSpec = """
            type Author {
              id: Int! # the ! means that every author object _must_ have an id
              firstName: String
              lastName: String
              posts: [Post] # the list of Posts by this author
            }
            
            type Post {
              id: Int!
              title: String
              votes: Int
              author: Author
            }
            
            # the schema allows the following query:
            type Query {
              posts: [Post]
              author(id: Int!): Author # author query must receive an id as argument
            }
            
            # this schema allows the following mutation:
            type Mutation {
              upvotePost (
                postId: Int!
              ): Post
            }
            
            # we need to tell the server which types represent the root query
            # and root mutation types. We call them RootQuery and RootMutation by convention.
            schema {
              query: Query
              mutation: Mutation
            }
        """

        def types = compile(schemaSpec)
        def typeRegistry = types.right().get()

        def result = new SchemaGenerator().makeExecutableSchema(typeRegistry)


        expect:

        result != null

    }


}
