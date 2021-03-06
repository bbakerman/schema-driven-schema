package io.atlassian.graphql.schemadriven;

import graphql.GraphQLError;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValue;
import graphql.language.FieldDefinition;
import graphql.language.FloatValue;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.IntValue;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectValue;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.language.Value;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.TypeResolver;
import graphql.schema.TypeResolverProxy;
import io.atlassian.fugue.Either;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

public class SchemaGenerator {

    private final SchemaTypeChecker typeChecker = new SchemaTypeChecker();

    public SchemaGenerator() {
    }

    public Either<List<GraphQLError>, GraphQLSchema> makeExecutableSchema(TypeRegistry typeRegistry, RuntimeWiring wiring) {
        List<GraphQLError> errors = typeChecker.checkTypeRegistry(typeRegistry, wiring);
        if (!errors.isEmpty()) {
            return Either.left(errors);
        }
        BuildContext buildCtx = new BuildContext(typeRegistry, wiring);

        return makeExecutableSchemaImpl(buildCtx);
    }

    /**
     * We pass this around so we know what we have defined in a stack like manner plus
     * it gives is helper
     */
    class BuildContext {
        private final TypeRegistry typeRegistry;
        private final RuntimeWiring wiring;
        private final Stack<String> definitionStack = new Stack<>();

        private final Map<String, GraphQLOutputType> outputGTypes = new HashMap<>();
        private final Map<String, GraphQLInputType> inputGTypes = new HashMap<>();

        BuildContext(TypeRegistry typeRegistry, RuntimeWiring wiring) {
            this.typeRegistry = typeRegistry;
            this.wiring = wiring;
        }

        TypeDefinition getTypeDefinition(Type type) {
            return typeRegistry.getType(type).get();
        }

        boolean stackContains(TypeInfo typeInfo) {
            return definitionStack.contains(typeInfo.getName());
        }

        void push(TypeInfo typeInfo) {
            definitionStack.push(typeInfo.getName());
        }

        String pop() {
            return definitionStack.pop();
        }

        GraphQLOutputType hasOutputType(TypeDefinition typeDefinition) {
            return outputGTypes.get(typeDefinition.getName());
        }

        GraphQLInputType hasInputType(TypeDefinition typeDefinition) {
            return inputGTypes.get(typeDefinition.getName());
        }

        void put(GraphQLOutputType outputType) {
            outputGTypes.put(outputType.getName(), outputType);
        }

        void put(GraphQLInputType inputType) {
            inputGTypes.put(inputType.getName(), inputType);
        }

        RuntimeWiring getWiring() {
            return wiring;
        }

        public SchemaDefinition getSchemaDefinition() {
            return typeRegistry.schemaDefinition().get();
        }
    }

    private Either<List<GraphQLError>, GraphQLSchema> makeExecutableSchemaImpl(BuildContext buildCtx) {

        SchemaDefinition schemaDefinition = buildCtx.getSchemaDefinition();
        List<OperationTypeDefinition> operationTypes = schemaDefinition.getOperationTypeDefinitions();

        // pre-flight checked via checker
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

    private GraphQLObjectType buildOperation(BuildContext buildCtx, OperationTypeDefinition operation) {
        Type type = operation.getType();

        return buildOutputType(buildCtx, type);
    }


    /**
     * This is the main recursive spot that builds out the various forms of Output types
     *
     * @param buildCtx the context we need to work out what we are doing
     * @param rawType  the type to be built
     *
     * @return an output type
     */
    @SuppressWarnings("unchecked")
    private <T extends GraphQLOutputType> T buildOutputType(BuildContext buildCtx, Type rawType) {

        TypeDefinition typeDefinition = buildCtx.getTypeDefinition(rawType);

        GraphQLOutputType outputType = buildCtx.hasOutputType(typeDefinition);
        if (outputType != null) {
            return (T) outputType;
        }
        TypeInfo typeInfo = TypeInfo.typeInfo(rawType);

        if (buildCtx.stackContains(typeInfo)) {
            // we have circled around so put in a type reference and fix it up later
            // otherwise we will go into an infinite loop
            return typeInfo.decorate(new GraphQLTypeReference(typeInfo.getName()));
        }

        buildCtx.push(typeInfo);

        if (typeDefinition instanceof ObjectTypeDefinition) {
            outputType = buildObjectType(buildCtx, (ObjectTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof InterfaceTypeDefinition) {
            outputType = buildInterfaceType(buildCtx, (InterfaceTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof UnionTypeDefinition) {
            outputType = buildUnionType(buildCtx, (UnionTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof EnumTypeDefinition) {
            outputType = buildEnumType((EnumTypeDefinition) typeDefinition);
        } else {
            outputType = buildScalar(buildCtx, (ScalarTypeDefinition) typeDefinition);
        }

        buildCtx.put(outputType);
        buildCtx.pop();
        return (T) typeInfo.decorate(outputType);
    }

    private GraphQLInputType buildInputType(BuildContext buildCtx, Type rawType) {

        TypeDefinition typeDefinition = buildCtx.getTypeDefinition(rawType);

        GraphQLInputType inputType = buildCtx.hasInputType(typeDefinition);
        if (inputType != null) {
            return inputType;
        }
        TypeInfo typeInfo = TypeInfo.typeInfo(rawType);

        if (buildCtx.stackContains(typeInfo)) {
            // we have circled around so put in a type reference and fix it later
            return typeInfo.decorate(new GraphQLTypeReference(typeInfo.getName()));
        }

        buildCtx.push(typeInfo);

        if (typeDefinition instanceof InputObjectTypeDefinition) {
            inputType = buildInputObjectType(buildCtx, (InputObjectTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof EnumTypeDefinition) {
            inputType = buildEnumType((EnumTypeDefinition) typeDefinition);
        } else {
            inputType = buildScalar(buildCtx, (ScalarTypeDefinition) typeDefinition);
        }

        buildCtx.put(inputType);
        buildCtx.pop();
        return typeInfo.decorate(inputType);
    }

    private GraphQLObjectType buildObjectType(BuildContext buildCtx, ObjectTypeDefinition typeDefinition) {
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject();
        builder.name(typeDefinition.getName());
        builder.description("#todo");

        typeDefinition.getFieldDefinitions().forEach(fieldDef ->
                builder.field(buildField(buildCtx, typeDefinition, fieldDef)));

        typeDefinition.getImplements().forEach(type -> builder.withInterface(buildOutputType(buildCtx, type)));
        return builder.build();
    }

    private GraphQLInterfaceType buildInterfaceType(BuildContext buildCtx, InterfaceTypeDefinition typeDefinition) {
        GraphQLInterfaceType.Builder builder = GraphQLInterfaceType.newInterface();
        builder.name(typeDefinition.getName());
        builder.description("#todo");

        builder.typeResolver(getTypeResolver(buildCtx, typeDefinition.getName()));

        typeDefinition.getFieldDefinitions().forEach(fieldDef ->
                builder.field(buildField(buildCtx, typeDefinition, fieldDef)));
        return builder.build();
    }

    private GraphQLUnionType buildUnionType(BuildContext buildCtx, UnionTypeDefinition typeDefinition) {
        GraphQLUnionType.Builder builder = GraphQLUnionType.newUnionType();
        builder.name(typeDefinition.getName());
        builder.description("#todo");
        builder.typeResolver(getTypeResolver(buildCtx, typeDefinition.getName()));

        typeDefinition.getMemberTypes().forEach(mt -> {
            TypeDefinition memberTypeDef = buildCtx.getTypeDefinition(mt);
            GraphQLObjectType objectType = buildObjectType(buildCtx, (ObjectTypeDefinition) memberTypeDef);
            builder.possibleType(objectType);
        });
        return builder.build();
    }

    private GraphQLEnumType buildEnumType(EnumTypeDefinition typeDefinition) {
        GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum();
        builder.name(typeDefinition.getName());
        builder.description("#todo");

        typeDefinition.getEnumValueDefinitions().forEach(evd -> builder.value(evd.getName()));
        return builder.build();
    }

    private GraphQLScalarType buildScalar(BuildContext buildCtx, ScalarTypeDefinition typeDefinition) {
        return buildCtx.getWiring().getScalars().get(typeDefinition.getName());
    }

    private GraphQLFieldDefinition buildField(BuildContext buildCtx, TypeDefinition parentType, FieldDefinition fieldDef) {
        GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition();
        builder.name(fieldDef.getName());
        builder.description("#todo");

        builder.dataFetcher(buildDataFetcher(buildCtx, parentType, fieldDef));

        fieldDef.getInputValueDefinitions().forEach(inputValueDefinition ->
                builder.argument(buildArgument(buildCtx, inputValueDefinition)));

        GraphQLOutputType outputType = buildOutputType(buildCtx, fieldDef.getType());
        builder.type(outputType);

        return builder.build();
    }

    private DataFetcher buildDataFetcher(BuildContext buildCtx, TypeDefinition parentType, FieldDefinition fieldDef) {
        RuntimeWiring wiring = buildCtx.getWiring();
        String fieldName = fieldDef.getName();
        DataFetcher dataFetcher = wiring.getDataFetcherForType(parentType.getName()).get(fieldName);
        if (dataFetcher == null) {
            //
            // in the future we could support FieldDateFetcher but we would need a way to indicate that in the schema spec
            // perhaps by a directive
            dataFetcher = new PropertyDataFetcher(fieldName);
        }
        return dataFetcher;
    }

    private GraphQLInputObjectType buildInputObjectType(BuildContext buildCtx, InputObjectTypeDefinition typeDefinition) {
        GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject();
        builder.name(typeDefinition.getName());
        builder.description("#todo");

        typeDefinition.getInputValueDefinitions().forEach(fieldDef ->
                builder.field(buildInputField(buildCtx, fieldDef)));
        return builder.build();
    }


    private GraphQLInputObjectField buildInputField(BuildContext buildCtx, InputValueDefinition fieldDef) {
        GraphQLInputObjectField.Builder fieldBuilder = GraphQLInputObjectField.newInputObjectField();
        fieldBuilder.name(fieldDef.getName());
        fieldBuilder.description("#todo");

        fieldBuilder.type(buildInputType(buildCtx, fieldDef.getType()));
        fieldBuilder.defaultValue(buildValue(fieldDef.getDefaultValue()));

        return fieldBuilder.build();
    }

    private GraphQLArgument buildArgument(BuildContext buildCtx, InputValueDefinition valueDefinition) {
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument();
        builder.name(valueDefinition.getName());
        builder.description("#todo");

        builder.type(buildInputType(buildCtx, valueDefinition.getType()));
        builder.defaultValue(buildValue(valueDefinition.getDefaultValue()));

        return builder.build();
    }

    private Object buildValue(Value value) {
        Object result = null;
        if (value instanceof IntValue) {
            result = ((IntValue) value).getValue();
        } else if (value instanceof FloatValue) {
            result = ((FloatValue) value).getValue();
        } else if (value instanceof StringValue) {
            result = ((StringValue) value).getValue();
        } else if (value instanceof EnumValue) {
            result = ((EnumValue) value).getName();
        } else if (value instanceof BooleanValue) {
            result = ((BooleanValue) value).isValue();
        } else if (value instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) value;
            result = arrayValue.getValues().stream().map(this::buildValue).toArray();
        } else if (value instanceof ObjectValue) {
            result = buildObjectValue((ObjectValue) value);
        }
        return result;

    }

    private Object buildObjectValue(ObjectValue defaultValue) {
        HashMap<String, Object> map = new LinkedHashMap<>();
        defaultValue.getObjectFields().forEach(of -> map.put(of.getName(), buildValue(of.getValue())));
        return map;
    }


    private TypeResolver getTypeResolver(BuildContext buildCtx, String name) {
        TypeResolver typeResolver = buildCtx.getWiring().getTypeResolvers().get(name);
        if (typeResolver == null) {
            // this really should be checked earlier via a pre-flight check
            typeResolver = new TypeResolverProxy();
        }
        return typeResolver;
    }
}
