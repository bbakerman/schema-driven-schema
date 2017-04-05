import graphql.GraphQLError
import graphql.language.ObjectTypeDefinition
import graphql.language.ScalarTypeDefinition
import io.atlassian.fugue.Either
import io.atlassian.graphql.schemadriven.SchemaCompiler
import io.atlassian.graphql.schemadriven.TypeRegistry
import spock.lang.Specification

/**
 * We don't want to retest the base GraphQL parser since it has its own testing
 * but we do want to test our aspects of it
 */
class SchemaCompilerTest extends Specification {

    Either<List<GraphQLError>, TypeRegistry> read(String types) {
        new SchemaCompiler().read(types)
    }

    def "test full schema read"() {

        def types = read(SchemaDefinitions.ALL_DEFINED_TYPES)
        def typeRegistry = types.right().get()
        def parsedTypes = typeRegistry.types()
        def scalarTypes = typeRegistry.scalars()
        def schemaDef = typeRegistry.schemaDefinitions()

        expect:

        parsedTypes.size() == 10
        parsedTypes.get("Query") instanceof ObjectTypeDefinition

        scalarTypes.size() == 11 // includes standard scalars
        scalarTypes.get("Url") instanceof ScalarTypeDefinition


        schemaDef.size() == 1
    }

    def "test bad schema"() {
        def spec = """   
            
            scala Url   # spillin misteak

            interface Foo {
               is_foo : Boolean
            }
            
                    
          """

        def result = read(spec)

        expect:

        result.isLeft()
        result.left().get().size() == 1
    }

}
