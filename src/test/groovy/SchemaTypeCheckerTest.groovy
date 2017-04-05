import graphql.GraphQLError
import io.atlassian.graphql.schemadriven.SchemaCompiler
import io.atlassian.graphql.schemadriven.SchemaTypeChecker
import io.atlassian.graphql.schemadriven.TypeRegistry
import io.atlassian.graphql.schemadriven.errors.SchemaMissingError
import spock.lang.Specification

class SchemaTypeCheckerTest extends Specification {

    TypeRegistry compile(String spec) {
        def types = new SchemaCompiler().read(spec)
        types.right().get()
    }

    List<GraphQLError> check(String spec) {
        def types = compile(spec)

        def result = new SchemaTypeChecker().checkAllTypesPresent(types)
        result
    }

    def "test missing type in object"() {

        def spec = """ 
          type Post {
              id: Int!
              title: String
              votes: Int
              author: Author ### not defined
            }

        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The field type 'Author' is not present when resolving type 'Post'")
    }

    def "test missing type in interface"() {

        def spec = """ 
          interface Post {
              id: Int!
              title: String
              votes: Int
              author: Author  ### not defined
            }

        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The field type 'Author' is not present when resolving type 'Post'")
    }

    def "test missing type in union type"() {

        def spec = """ 

            type Bar {
                id : ID!
            }
            
            union FooBar = Bar | Foo ### not defined
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The union member type 'Foo' is not present when resolving type 'FooBar'")
    }

    def "test missing type in input type"() {

        def spec = """ 

            input ListUsersInput {
                id: ID
                limit: Int
                author : Author ### not defined
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The input value type 'Author' is not present when resolving type 'ListUsersInput'")
    }


    def "test missing type in extension type"() {

        def spec = """ 
            type Query {
                bars: [Bar]!
            }
            type Bar {
                id : ID!
            }
            
            extend type Query {
                foos: [Foo]!    ### not defined
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The field type 'Foo' is not present when resolving type 'Query'")
    }

    def "test missing schema"() {

        def spec = """ 
            type Bar {
                id : ID!
            }
            
            # no schema defined and hence we can proceed
        """

        def result = check(spec)

        expect:

        result.get(0) instanceof SchemaMissingError
    }

    def "test missing schema operation types"() {

        def spec = """ 
            schema {
              query : MissingType
            }
        """

        def result = check(spec)

        expect:

        result.get(0).getMessage().contains("The operation type 'MissingType' is not present when resolving type 'query'")
    }

}