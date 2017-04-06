package io.atlassian.graphql.schemadriven;

import graphql.Assert;
import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLScalarType;
import graphql.schema.TypeResolver;

import java.util.LinkedHashMap;
import java.util.Map;

public class RuntimeWiring {

    private final Map<String, Map<String, DataFetcher>> dataFetchers = new LinkedHashMap<>();
    private final Map<String, GraphQLScalarType> scalars = new LinkedHashMap<>();
    private final Map<String, TypeResolver> typeResolvers = new LinkedHashMap<>();

    public RuntimeWiring() {
        scalar(Scalars.GraphQLInt);
        scalar(Scalars.GraphQLFloat);
        scalar(Scalars.GraphQLString);
        scalar(Scalars.GraphQLBoolean);
        scalar(Scalars.GraphQLID);

        scalar(Scalars.GraphQLBigDecimal);
        scalar(Scalars.GraphQLBigInteger);
        scalar(Scalars.GraphQLByte);
        scalar(Scalars.GraphQLChar);
        scalar(Scalars.GraphQLShort);
        scalar(Scalars.GraphQLLong);

    }

    /**
     * This allows you to add in new custom Scalar implementations beyond the standard set.
     *
     * @param scalarType the new scalar implementation
     */
    public void scalar(GraphQLScalarType scalarType) {
        scalars.put(scalarType.getName(), scalarType);
    }

    public Map<String, GraphQLScalarType> getScalars() {
        return new LinkedHashMap<>(scalars);
    }

    Map<String, Map<String, DataFetcher>> getDataFetchers() {
        return dataFetchers;
    }

    Map<String, DataFetcher> getDataFetcherForType(String typeName) {
        return dataFetchers.computeIfAbsent(typeName, k -> new LinkedHashMap<>());
    }

    Map<String, TypeResolver> getTypeResolvers() {
        return typeResolvers;
    }

    public TypeWiring forType(String typeName) {
        return new TypeWiring(typeName);
    }

    public class TypeWiring {
        private final String typeName;

        private TypeWiring(String typeName) {
            this.typeName = typeName;
        }

        /**
         * Adds a data fetcher for thr current type to the specified field
         *
         * @param fieldName   the field that data fetcher should apply to
         * @param dataFetcher the new data Fetcher
         *
         * @return the current type wiring
         */
        public TypeWiring dataFetcher(String fieldName, DataFetcher dataFetcher) {
            Assert.assertNotNull(dataFetcher, "you must provide a data fetcher");
            Assert.assertNotNull(fieldName, "you must tel us what field");
            Map<String, DataFetcher> map = dataFetchers.computeIfAbsent(typeName, k -> new LinkedHashMap<>());
            map.put(fieldName, dataFetcher);
            return this;
        }

        /**
         * Adds a {@link TypeResolver} to the current type.  This MUST be specified for Interface
         * and Union types.
         *
         * @param typeResolver the type resolver in play
         *
         * @return the current type wiring
         */
        public TypeWiring typeResolver(TypeResolver typeResolver) {
            Assert.assertNotNull(typeResolver, "you must provide a type resolver");
            typeResolvers.put(typeName, typeResolver);
            return this;
        }

        public TypeWiring forType(String typeName) {
            return new TypeWiring(typeName);
        }
    }


}
