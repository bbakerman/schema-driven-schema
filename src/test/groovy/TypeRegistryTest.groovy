import graphql.language.SchemaDefinition
import io.atlassian.graphql.schemadriven.SchemaCompiler
import io.atlassian.graphql.schemadriven.TypeRegistry
import io.atlassian.graphql.schemadriven.errors.SchemaRedefinitionError
import spock.lang.Specification

class TypeRegistryTest extends Specification {

    TypeRegistry compile(String spec) {
        def types = new SchemaCompiler().read(spec)
        types.right().get()
    }

    def "test default scalars are locked in"() {

        def registry = new TypeRegistry()

        def scalars = registry.scalars()

        expect:

        scalars.containsKey("Int")
        scalars.containsKey("Float")
        scalars.containsKey("String")
        scalars.containsKey("Boolean")
        scalars.containsKey("ID")

        // graphql-java library extensions
        scalars.containsKey("Long")
        scalars.containsKey("BigInteger")
        scalars.containsKey("BigDecimal")
        scalars.containsKey("Short")
        scalars.containsKey("Char")

    }

    def "adding 2 schemas is not allowed"() {
        def registry = new TypeRegistry()
        def result1 = registry.add(new SchemaDefinition())
        def result2 = registry.add(new SchemaDefinition())

        expect:
        result1.isEmpty()
        result2.get() instanceof SchemaRedefinitionError
    }


    def "test merge of schema types"() {

        def spec1 = """ 
            schema {
                query: Query
                mutation: Mutation
            }
        """

        def spec2 = """ 
            schema {
                query: Query2
                mutation: Mutation2
            }
        """

        def result1 = compile(spec1)
        def result2 = compile(spec2)

        def errors = result1.merge(result2)

        expect:

        errors.isDefined()
        errors.get().get(0) instanceof SchemaRedefinitionError
    }

    def "test merge of object types"() {

        def spec1 = """ 
          type Post {
              id: Int!
              title: String
              votes: Int
            }

        """

        def spec2 = """ 
          type Post {
              id: Int!
            }

        """

        def result1 = compile(spec1)
        def result2 = compile(spec2)

        def errors = result1.merge(result2)

        expect:

        errors.isDefined()
        errors.get().get(0).getMessage().contains("tried to redefine existing 'Post'")
    }


    def "test merge of scalar types"() {

        def spec1 = """ 
          type Post {
              id: Int!
              title: String
              votes: Int
            }
            
            scalar Url

        """

        def spec2 = """ 
         
         scalar UrlX
         
         scalar Url
         

        """

        def result1 = compile(spec1)
        def result2 = compile(spec2)

        def errors = result1.merge(result2)

        expect:

        errors.isDefined()
        errors.get().get(0).getMessage().contains("tried to redefine existing 'Url'")
    }

}
