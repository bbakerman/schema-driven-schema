package io.atlassian.graphql.schemadriven;

import graphql.Scalars;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class TypeInfo {
    
    public static final List<GraphQLScalarType> STANDARD_SCALARS= new ArrayList<>();
    static {
        STANDARD_SCALARS.add(Scalars.GraphQLInt);
        STANDARD_SCALARS.add(Scalars.GraphQLFloat);
        STANDARD_SCALARS.add(Scalars.GraphQLString);
        STANDARD_SCALARS.add(Scalars.GraphQLBoolean);
        STANDARD_SCALARS.add(Scalars.GraphQLID);

        STANDARD_SCALARS.add(Scalars.GraphQLBigDecimal);
        STANDARD_SCALARS.add(Scalars.GraphQLBigInteger);
        STANDARD_SCALARS.add(Scalars.GraphQLByte);
        STANDARD_SCALARS.add(Scalars.GraphQLChar);
        STANDARD_SCALARS.add(Scalars.GraphQLShort);
        STANDARD_SCALARS.add(Scalars.GraphQLLong);
    }

    public static boolean isStandardScalar(GraphQLScalarType scalarType) {
        return STANDARD_SCALARS.stream().anyMatch(sc -> sc.getName().equals(scalarType.getName()));
    }

    public static TypeInfo typeInfo(Type type) {
        return new TypeInfo(type);
    }

    private final Type rawType;
    private final TypeName typeName;
    private final Stack<Class<?>> decoration = new Stack<>();

    public TypeInfo(Type type) {
        this.rawType = type;
        while (!(type instanceof TypeName)) {
            if (type instanceof NonNullType) {
                decoration.push(NonNullType.class);
                type = ((NonNullType) type).getType();
            }
            if (type instanceof ListType) {
                decoration.push(ListType.class);
                type = ((ListType) type).getType();
            }
        }
        this.typeName = (TypeName) type;
    }

    public Type getRawType() {
        return rawType;
    }

    public TypeName getTypeName() {
        return typeName;
    }

    public String getName() {
        return typeName.getName();
    }

    /**
     * This will decorate a grapql type with the original hirearchy of non null and list ness
     * it originally contained
     * @param objectType this should be a graphql type that was originally built from this raw type
     * @return the decorated type
     */
    public <T extends GraphQLType> T decorate(GraphQLType objectType) {

        GraphQLType out = objectType;
        Stack<Class<?>> wrappingStack = new Stack<>();
        wrappingStack.addAll(this.decoration);
        while (! wrappingStack.isEmpty()) {
            Class<?> clazz = wrappingStack.pop();
            if (clazz.equals(NonNullType.class)) {
                out = new GraphQLNonNull(out);
            }
            if (clazz.equals(ListType.class)) {
                out = new GraphQLList(out);
            }
        }
        // we handle both input and output graphql types
        //noinspection unchecked
        return (T) out;
    }

    @Override
    public String toString() {
        return "TypeInfo{" +
                "rawType=" + rawType +
                ", typeName=" + typeName +
                ", isNonNull=" + decoration +
                '}';
    }
}

