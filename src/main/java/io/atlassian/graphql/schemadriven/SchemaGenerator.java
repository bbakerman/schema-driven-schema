package io.atlassian.graphql.schemadriven;

import graphql.GraphQLError;
import graphql.Scalars;
import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import io.atlassian.fugue.Either;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

public class SchemaGenerator {

    private final SchemaTypeChecker typeChecker = new SchemaTypeChecker();
    private final Map<String, GraphQLScalarType> scalars = new LinkedHashMap<>();

    public SchemaGenerator() {
        addScalar(Scalars.GraphQLInt);
        addScalar(Scalars.GraphQLFloat);
        addScalar(Scalars.GraphQLString);
        addScalar(Scalars.GraphQLBoolean);
        addScalar(Scalars.GraphQLID);

        addScalar(Scalars.GraphQLBigDecimal);
        addScalar(Scalars.GraphQLBigInteger);
        addScalar(Scalars.GraphQLByte);
        addScalar(Scalars.GraphQLChar);
        addScalar(Scalars.GraphQLShort);
        addScalar(Scalars.GraphQLLong);

    }

    private void addScalar(GraphQLScalarType scalarType) {
        scalars.put(scalarType.getName(), scalarType);
    }

    public Either<List<GraphQLError>, GraphQLSchema> makeExecutableSchema(TypeRegistry typeRegistry) {
        List<GraphQLError> errors = typeChecker.checkAllTypesPresent(typeRegistry);
        errors.addAll(checkScalarImplementationsArePresent(typeRegistry));
        if (!errors.isEmpty()) {
            return Either.left(errors);
        }
        return makeExecutableSchemaImpl(typeRegistry);
    }

    private Collection<? extends GraphQLError> checkScalarImplementationsArePresent(TypeRegistry typeRegistry) {
        // todo
        return Collections.emptyList();
    }

    class BuildContext {
        TypeRegistry typeRegistry;
        Stack<String> definitionStack = new Stack<>();

        Map<String, GraphQLOutputType> outputGTypes = new HashMap<>();
        Map<String, GraphQLInputType> inputGTypes = new HashMap<>();

        public BuildContext(TypeRegistry typeRegistry) {
            this.typeRegistry = typeRegistry;
        }

        public <T extends GraphQLOutputType> T add(GraphQLOutputType graphQLOutputType) {
            outputGTypes.put(graphQLOutputType.getName(), graphQLOutputType);
            //noinspection unchecked
            return (T) graphQLOutputType;
        }

        public GraphQLScalarType getGraphqlScalar(String scalarName) {
            return scalars.get(scalarName);
        }
    }

    private Either<List<GraphQLError>, GraphQLSchema> makeExecutableSchemaImpl(TypeRegistry typeRegistry) {
        BuildContext buildCtx = new BuildContext(typeRegistry);

        SchemaDefinition schemaDefinition = typeRegistry.schemaDefinition().get();
        List<OperationTypeDefinition> operationTypes = schemaDefinition.getOperationTypeDefinitions();

        // pre-checked via checker
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        OperationTypeDefinition queryOp = operationTypes.stream().filter(op -> "query".equals(op.getName())).findFirst().get();
        Optional<OperationTypeDefinition> mutationOp = operationTypes.stream().filter(op -> "mutation".equals(op.getName())).findFirst();

        GraphQLObjectType query = buildOperation(buildCtx, queryOp);
        GraphQLObjectType mutation = null;

        if (mutationOp.isPresent()) {
            mutation = buildOperation(buildCtx, mutationOp.get());
        }

        GraphQLSchema graphQLSchema = new GraphQLSchema(query, mutation, Collections.emptySet());
        return Either.right(graphQLSchema);
    }

    private GraphQLOutputType buildOutputType(BuildContext buildCtx, Type rawType, TypeDefinition typeDefinition) {
        GraphQLOutputType graphQLOutputType = buildCtx.outputGTypes.get(typeDefinition.getName());
        if (graphQLOutputType != null) {
            return graphQLOutputType;
        }
        TypeInfo typeInfo = TypeInfo.typeInfo(rawType);

        if (buildCtx.definitionStack.contains(typeInfo.getName())) {
            // we have circled around so put in a type reference and fix it later
            return typeInfo.decorate(new GraphQLTypeReference(typeInfo.getName()));
        }

        buildCtx.definitionStack.push(typeInfo.getName());

        GraphQLOutputType decorated = null;
        if (typeDefinition instanceof ObjectTypeDefinition) {
            decorated = typeInfo.decorate(buildObjectType(buildCtx, typeInfo, (ObjectTypeDefinition) typeDefinition));
        }
        if (typeDefinition instanceof InterfaceTypeDefinition) {
            decorated = typeInfo.decorate(buildInterfaceType(buildCtx, typeInfo, (InterfaceTypeDefinition) typeDefinition));
        }
        if (typeDefinition instanceof UnionTypeDefinition) {
            decorated = typeInfo.decorate(buildUnionType(buildCtx, typeInfo, (UnionTypeDefinition) typeDefinition));
        }
        if (typeDefinition instanceof ScalarTypeDefinition) {
            decorated = typeInfo.decorate(buildScalar(buildCtx, typeInfo, (ScalarTypeDefinition) typeDefinition));
        }
        buildCtx.definitionStack.pop();
        return decorated;
    }

    private GraphQLScalarType buildScalar(BuildContext buildCtx, TypeInfo typeInfo, ScalarTypeDefinition typeDefinition) {
        return buildCtx.getGraphqlScalar(typeDefinition.getName());
    }

    private GraphQLObjectType buildObjectType(BuildContext buildCtx, TypeInfo typeInfo, ObjectTypeDefinition typeDefinition) {
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject();
        builder.name(typeDefinition.getName());
        builder.description("#todo");

        typeDefinition.getFieldDefinitions().forEach(fieldDef -> builder.field(buildField(buildCtx, fieldDef)));
        return builder.build();
    }


    private GraphQLInterfaceType buildInterfaceType(BuildContext buildCtx, TypeInfo typeInfo, InterfaceTypeDefinition typeDefinition) {
        GraphQLInterfaceType.Builder builder = GraphQLInterfaceType.newInterface();
        builder.name(typeDefinition.getName());
        builder.description("#todo");

        typeDefinition.getFieldDefinitions().forEach(fieldDef -> builder.field(buildField(buildCtx, fieldDef)));
        return buildCtx.add(builder.build());
    }

    private GraphQLUnionType buildUnionType(BuildContext buildCtx, TypeInfo typeInfo, UnionTypeDefinition typeDefinition) {
        GraphQLUnionType.Builder builder = GraphQLUnionType.newUnionType();
        builder.name(typeDefinition.getName());
        builder.description("#todo");
        typeDefinition.getMemberTypes().forEach(mt -> {
            TypeDefinition memberTypeDef = buildCtx.typeRegistry.getType(mt).get();
            GraphQLObjectType objectType = buildObjectType(buildCtx, typeInfo, (ObjectTypeDefinition) memberTypeDef);
            builder.possibleType(objectType);
        });
        return buildCtx.add(builder.build());
    }

    private GraphQLFieldDefinition buildField(BuildContext buildCtx, FieldDefinition fieldDef) {
        GraphQLFieldDefinition.Builder fieldBuilder = GraphQLFieldDefinition.newFieldDefinition();
        fieldBuilder.name(fieldDef.getName());
        fieldBuilder.description("#todo");

        buildArguments(buildCtx, fieldBuilder, fieldDef.getInputValueDefinitions());

        TypeDefinition typeDef = buildCtx.typeRegistry.getType(fieldDef.getType()).get();

        fieldBuilder.type(buildOutputType(buildCtx, fieldDef.getType(), typeDef));

        return fieldBuilder.build();
    }

    private void buildArguments(BuildContext buildCtx, GraphQLFieldDefinition.Builder fieldBuilder, List<InputValueDefinition> valueDefinitionList) {
        // TODO
    }

    private GraphQLObjectType buildOperation(BuildContext buildCtx, OperationTypeDefinition operation) {
        Type type = operation.getType();
        TypeDefinition operationType = buildCtx.typeRegistry.getType(type).get();

        GraphQLOutputType objectType = buildOutputType(buildCtx, type, operationType);
        // we should check that operations are object type definitions during pre-flight
        return (GraphQLObjectType) objectType;
    }


}
