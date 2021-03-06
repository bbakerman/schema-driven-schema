import graphql.schema.*
import io.atlassian.graphql.schemadriven.RuntimeWiring
import io.atlassian.graphql.schemadriven.SchemaCompiler
import io.atlassian.graphql.schemadriven.SchemaGenerator
import spock.lang.Specification

class SchemaGeneratorTest extends Specification {


    GraphQLSchema generateSchema(String schemaSpec, RuntimeWiring wiring) {
        def types = new SchemaCompiler().compile(schemaSpec)
        def typeRegistry = types.right().get()

        def result = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring)
        assert result.isRight()
        result.right().get()
    }

    GraphQLType unwrap1Layer(GraphQLType type) {
        if (type instanceof GraphQLNonNull) {
            type = (type as GraphQLNonNull).wrappedType
        } else if (type instanceof GraphQLList) {
            type = (type as GraphQLList).wrappedType
        }
        type
    }

    GraphQLType unwrap(GraphQLType type) {
        while (true) {
            if (type instanceof GraphQLNonNull) {
                type = (type as GraphQLNonNull).wrappedType
            } else if (type instanceof GraphQLList) {
                type = (type as GraphQLList).wrappedType
            } else {
                break
            }
        }
        type
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
            
            input PostUpVote {
                postId: ID
                votes : Int
            }
            
            # this schema allows the following mutation:
            type Mutation {
              upvotePost (
                upvoteArgs : PostUpVote!
              ): Post
            }
            
            # we need to tell the server which types represent the root query
            # and root mutation types. We call them RootQuery and RootMutation by convention.
            schema {
              query: Query
              mutation: Mutation
            }
        """

        def schema = generateSchema(schemaSpec, new RuntimeWiring())


        expect:


        schema.getQueryType().name == "Query"
        schema.getMutationType().name == "Mutation"

        //        type Query {
        //            posts: [Post]
        //            author(id: Int!): Author
        //        }

        def postField = schema.getQueryType().getFieldDefinition("posts")
        postField.type instanceof GraphQLList
        unwrap(postField.type).name == "Post"


        def authorField = schema.getQueryType().getFieldDefinition("author")
        authorField.type.name == "Author"
        authorField.arguments.get(0).name == "id"
        authorField.arguments.get(0).type instanceof GraphQLNonNull
        unwrap(authorField.arguments.get(0).type).name == "Int"

        //
        // input PostUpVote {
        //        postId: ID
        //        votes : Int
        // }

        // type Mutation {
        //        upvotePost (
        //                upvoteArgs : PostUpVote!
        //        ) : Post
        // }

        def upvotePostField = schema.getMutationType().getFieldDefinition("upvotePost")
        def upvotePostFieldArg = upvotePostField.arguments.get(0)
        upvotePostFieldArg.name == "upvoteArgs"

        upvotePostFieldArg.type instanceof GraphQLNonNull
        unwrap(upvotePostFieldArg.type).name == "PostUpVote"

        (unwrap(upvotePostFieldArg.type) as GraphQLInputObjectType).getField("postId").type.name == "ID"
        (unwrap(upvotePostFieldArg.type) as GraphQLInputObjectType).getField("votes").type.name == "Int"


    }


    def "enum types are handled"() {

        def spec = """     

            enum RGB {
                RED
                GREEN
                BLUE
            }
            
            type Query {
              rgb : RGB
            }
            
            schema {
              query: Query
            }

        """

        def schema = generateSchema(spec, new RuntimeWiring())

        expect:

        def rgbField = schema.getQueryType().getFieldDefinition("rgb")
        rgbField.type instanceof GraphQLEnumType
        (rgbField.type as GraphQLEnumType).values.get(0).getValue() == "RED"
        (rgbField.type as GraphQLEnumType).values.get(1).getValue() == "GREEN"
        (rgbField.type as GraphQLEnumType).values.get(2).getValue() == "BLUE"

    }

    def "interface types are handled"() {

        def spec = """     

            interface Foo {
               is_foo : Boolean
            }
            
            interface Goo {
               is_goo : Boolean
            }
                 
            type Query implements Foo {
                is_foo : Boolean
                is_bar : Boolean
            }     
            
            schema {
              query: Query
            }

        """

        def resolver = new TypeResolver() {
            @Override
            GraphQLObjectType getType(Object object) {
                throw new UnsupportedOperationException("Not implemented")
            }
        }
        def wiring = new RuntimeWiring()
        wiring.forType("Foo").typeResolver(resolver)
        wiring.forType("Goo").typeResolver(resolver)

        def schema = generateSchema(spec, wiring)

        expect:

        schema.queryType.interfaces[0].name == "Foo"
        schema.queryType.fieldDefinitions[0].name == "is_foo"
        schema.queryType.fieldDefinitions[0].type.name == "Boolean"
        schema.queryType.fieldDefinitions[1].name == "is_bar"
        schema.queryType.fieldDefinitions[1].type.name == "Boolean"

    }

}
