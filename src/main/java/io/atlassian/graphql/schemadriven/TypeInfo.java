package io.atlassian.graphql.schemadriven;

import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;

public class TypeInfo {

    public static TypeInfo typeInfo(Type type) {
        return new TypeInfo(type);
    }

    public TypeInfo(Type type) {
        this.rawType = type;
        while (!(type instanceof TypeName)) {
            if (type instanceof NonNullType) {
                isNonNull = true;
                type = ((NonNullType) type).getType();
            }
            if (type instanceof ListType) {
                isList = true;
                type = ((ListType) type).getType();
            }
        }
        this.typeName = (TypeName) type;
    }

    private final Type rawType;
    private final TypeName typeName;
    boolean isNonNull;
    boolean isList;

    public Type getRawType() {
        return rawType;
    }

    public TypeName getTypeName() {
        return typeName;
    }

    public String getName() {
        return typeName.getName();
    }

    public boolean isNonNull() {
        return isNonNull;
    }

    public boolean isList() {
        return isList;
    }

    @Override
    public String toString() {
        return "TypeInfo{" +
                "rawType=" + rawType +
                ", typeName=" + typeName +
                ", isNonNull=" + isNonNull +
                ", isList=" + isList +
                '}';
    }

    public GraphQLOutputType decorate(GraphQLOutputType objectType) {
        // TODO
        // this is not quite right yet
        //
        // we can have [ type ! ] !
        // we can have [ type ! ]
        // we can have [ type ] !

        // fix this

        GraphQLOutputType out = objectType;
        if (isNonNull) {
            out = new GraphQLNonNull(out);
        }
        if (isList) {
            out = new GraphQLList(out);
        }
        return out;
    }
}
