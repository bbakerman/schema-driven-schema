# schema-driven-schema

This library allows for "schema first" development.

It will allow you to compile a set of schema files into a executable `GraphqlSchema`.
 
 
So given a graphql schema input file with

```graphql

type Author {
  id: Int! 
  firstName: String
  lastName: String
  posts: [Post]
}

type Post {
  id: Int!
  title: String
  votes: Int
  author: Author
}

type Query {
  posts: [Post]
  author(id: Int!): Author
}

type Mutation {
  upvotePost (
    postId: Int!
  ): Post
}

schema {
  query: Query
  mutation: Mutation
}

```

You could compile and generate an executable schema via

```java
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

```

The library is NOT a code generation library.  It will not create "Rails" style code backing the schema.

The application logic backing this schema is still up to you.  However it will allows you to use
common specifications to generate the schema.

