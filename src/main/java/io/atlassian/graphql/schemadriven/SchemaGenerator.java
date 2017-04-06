package io.atlassian.graphql.schemadriven;

import graphql.GraphQLError;
import graphql.Scalars;
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
import graphql.schema.TypeResolverProxy;
import io.atlassian.fugue.Either;
import io.atlassian.graphql.schemadriven.errors.MissingScalarImplementationError;

import java.util.ArrayList;
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
        List<GraphQLError> errors = new ArrayList<>();
        typeRegistry.scalars().keySet().forEach(scalarName -> {
            if (!scalars.containsKey(scalarName)) {
                errors.add(new MissingScalarImplementationError(scalarName));
            }
        });
        return errors;
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

    }

    private Either<List<GraphQLError>, GraphQLSchema> makeExecutableSchemaImpl(TypeRegistry typeRegistry) {
        BuildContext buildCtx = new BuildContext(typeRegistry);

        SchemaDefinition schemaDefinition = typeRegistry.schemaDefinition().get();
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

        // TODO we should realy check that operations are object type definitions during pre-flight
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

        TypeDefinition typeDefinition = getTypeDefinition(buildCtx, rawType);

        GraphQLOutputType outputType = buildCtx.outputGTypes.get(typeDefinition.getName());
        if (outputType != null) {
            return (T) outputType;
        }
        TypeInfo typeInfo = TypeInfo.typeInfo(rawType);

        if (buildCtx.definitionStack.contains(typeInfo.getName())) {
            // we have circled around so put in a type reference and fix it later
            return typeInfo.decorate(new GraphQLTypeReference(typeInfo.getName()));
        }

        buildCtx.definitionStack.push(typeInfo.getName());

        if (typeDefinition instanceof ObjectTypeDefinition) {
            outputType = buildObjectType(buildCtx, (ObjectTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof InterfaceTypeDefinition) {
            outputType = buildInterfaceType(buildCtx, (InterfaceTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof UnionTypeDefinition) {
            outputType = buildUnionType(buildCtx, (UnionTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof EnumTypeDefinition) {
            outputType = buildEnumType((EnumTypeDefinition) typeDefinition);
        } else {
            outputType = buildScalar((ScalarTypeDefinition) typeDefinition);
        }

        buildCtx.outputGTypes.put(outputType.getName(), outputType);
        buildCtx.definitionStack.pop();
        return (T) typeInfo.decorate(outputType);
    }

    private GraphQLInputType buildInputType(BuildContext buildCtx, Type rawType) {

        TypeDefinition typeDefinition = getTypeDefinition(buildCtx, rawType);

        GraphQLInputType inputType = buildCtx.inputGTypes.get(typeDefinition.getName());
        if (inputType != null) {
            return inputType;
        }
        TypeInfo typeInfo = TypeInfo.typeInfo(rawType);

        if (buildCtx.definitionStack.contains(typeInfo.getName())) {
            // we have circled around so put in a type reference and fix it later
            return typeInfo.decorate(new GraphQLTypeReference(typeInfo.getName()));
        }

        buildCtx.definitionStack.push(typeInfo.getName());

        if (typeDefinition instanceof InputObjectTypeDefinition) {
            inputType = buildInputObjectType(buildCtx, (InputObjectTypeDefinition) typeDefinition);
        } else if (typeDefinition instanceof EnumTypeDefinition) {
            inputType = buildEnumType((EnumTypeDefinition) typeDefinition);
        } else {
            inputType = buildScalar((ScalarTypeDefinition) typeDefinition);
        }

        buildCtx.inputGTypes.put(inputType.getName(), inputType);
        buildCtx.definitionStack.pop();
        return typeInfo.decorate(inputType);
    }

    private GraphQLObjectType buildObjectType(BuildContext buildCtx, ObjectTypeDefinition typeDefinition) {
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject();
        builder.name(typeDefinition.getName());
        builder.description("#todo");

        typeDefinition.getFieldDefinitions().forEach(fieldDef ->
                builder.field(buildField(buildCtx, fieldDef)));

        typeDefinition.getImplements().forEach(type -> builder.withInterface(buildOutputType(buildCtx, type)));
        return builder.build();
    }

    private GraphQLInterfaceType buildInterfaceType(BuildContext buildCtx, InterfaceTypeDefinition typeDefinition) {
        GraphQLInterfaceType.Builder builder = GraphQLInterfaceType.newInterface();
        builder.name(typeDefinition.getName());
        builder.description("#todo");

        builder.typeResolver(defaultTypeResolver());

        typeDefinition.getFieldDefinitions().forEach(fieldDef ->
                builder.field(buildField(buildCtx, fieldDef)));
        return buildCtx.add(builder.build());
    }

    private GraphQLUnionType buildUnionType(BuildContext buildCtx, UnionTypeDefinition typeDefinition) {
        GraphQLUnionType.Builder builder = GraphQLUnionType.newUnionType();
        builder.name(typeDefinition.getName());
        builder.description("#todo");
        builder.typeResolver(defaultTypeResolver());

        typeDefinition.getMemberTypes().forEach(mt -> {
            TypeDefinition memberTypeDef = getTypeDefinition(buildCtx, mt);
            GraphQLObjectType objectType = buildObjectType(buildCtx, (ObjectTypeDefinition) memberTypeDef);
            builder.possibleType(objectType);
        });
        return buildCtx.add(builder.build());
    }

    private GraphQLEnumType buildEnumType(EnumTypeDefinition typeDefinition) {
        GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum();
        builder.name(typeDefinition.getName());
        builder.description("#todo");

        typeDefinition.getEnumValueDefinitions().forEach(evd -> builder.value(evd.getName()));
        return builder.build();
    }

    private GraphQLScalarType buildScalar(ScalarTypeDefinition typeDefinition) {
        return scalars.get(typeDefinition.getName());
    }

    private GraphQLFieldDefinition buildField(BuildContext buildCtx, FieldDefinition fieldDef) {
        GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition();
        builder.name(fieldDef.getName());
        builder.description("#todo");

        fieldDef.getInputValueDefinitions().forEach(inputValueDefinition ->
                builder.argument(buildArgument(buildCtx, inputValueDefinition)));

        GraphQLOutputType outputType = buildOutputType(buildCtx, fieldDef.getType());
        builder.type(outputType);

        return builder.build();
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

    private TypeDefinition getTypeDefinition(BuildContext buildCtx, Type type) {
        return buildCtx.typeRegistry.getType(type).get();
    }

    private TypeResolverProxy defaultTypeResolver() {
        // TODO - we need to wire in a type resolver but we have no good way of doing that yet
        return new TypeResolverProxy();
    }
}
