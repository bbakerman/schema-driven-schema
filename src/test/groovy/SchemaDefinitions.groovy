/**
 * TODO: Document this file here
 */
class SchemaDefinitions {

    static ALL_DEFINED_TYPES = """

            interface Foo {
               is_foo : Boolean
            }
            
            interface Goo {
               is_goo : Boolean
            }
                 
            type Bar implements Foo {
                is_foo : Boolean
                is_bar : Boolean
            }     

            type Baz implements Foo, Goo {
                is_foo : Boolean
                is_goo : Boolean
                is_baz : Boolean
            }     
            
            enum USER_STATE {
                NOT_FOUND
                ACTIVE
                INACTIVE
                SUSPENDED
            }
            
            scalar Url
            
            type User {
                name : String
                homepage : Url
                state : USER_STATE
            }
            

            type Author {
              id: Int! # the ! means that every author object _must_ have an id
              user: User
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
            schema @java(package:"com.company.package", directive2:123) {
              query: Query
              mutation: Mutation
            }
                    
          """

}
