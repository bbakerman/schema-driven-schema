package io.atlassian.graphql.schemadriven;

import graphql.GraphQLError;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeExtensionDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import io.atlassian.graphql.schemadriven.errors.MissingTypeError;
import io.atlassian.graphql.schemadriven.errors.QueryOperationMissingError;
import io.atlassian.graphql.schemadriven.errors.SchemaMissingError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.atlassian.graphql.schemadriven.TypeInfo.typeInfo;

public class SchemaTypeChecker {


    // TODO we should really check that operations are object type definitions during pre-flight



    public List<GraphQLError> checkAllTypesPresent(TypeRegistry typeRegistry) {
        List<GraphQLError> errors = new ArrayList<>();

        // type extensions
        Collection<TypeExtensionDefinition> typeExtensions = typeRegistry.typeExtensions().values();
        typeExtensions.forEach(typeExtension -> {

            List<Type> implementsTypes = typeExtension.getImplements();
            implementsTypes.forEach(checkTypeExists("type extension", typeRegistry, errors, typeExtension));

            checkFieldTypesPresent(typeRegistry, errors, typeExtension, typeExtension.getFieldDefinitions());

        });


        Map<String, TypeDefinition> typesMap = typeRegistry.types();

        // objects
        List<ObjectTypeDefinition> objectTypes = filterTo(typesMap, ObjectTypeDefinition.class);
        objectTypes.forEach(objectType -> {

            List<Type> implementsTypes = objectType.getImplements();
            implementsTypes.forEach(checkTypeExists("object", typeRegistry, errors, objectType));

            checkFieldTypesPresent(typeRegistry, errors, objectType, objectType.getFieldDefinitions());

        });

        // interfaces
        List<InterfaceTypeDefinition> interfaceTypes = filterTo(typesMap, InterfaceTypeDefinition.class);
        interfaceTypes.forEach(interfaceType -> {
            List<FieldDefinition> fields = interfaceType.getFieldDefinitions();

            checkFieldTypesPresent(typeRegistry, errors, interfaceType, fields);

        });

        // union types
        List<UnionTypeDefinition> unionTypes = filterTo(typesMap, UnionTypeDefinition.class);
        unionTypes.forEach(unionType -> {
            List<Type> memberTypes = unionType.getMemberTypes();
            memberTypes.forEach(checkTypeExists("union member", typeRegistry, errors, unionType));

        });


        // input types
        List<InputObjectTypeDefinition> inputTypes = filterTo(typesMap, InputObjectTypeDefinition.class);
        inputTypes.forEach(inputType -> {
            List<InputValueDefinition> inputValueDefinitions = inputType.getInputValueDefinitions();
            List<Type> inputValueTypes = inputValueDefinitions.stream()
                    .map(InputValueDefinition::getType)
                    .collect(Collectors.toList());

            inputValueTypes.forEach(checkTypeExists("input value", typeRegistry, errors, inputType));

        });

        // schema
        if (typeRegistry.schemaDefinition().isEmpty()) {
            errors.add(new SchemaMissingError());
        } else {
            SchemaDefinition schemaDefinition = typeRegistry.schemaDefinition().get();
            List<OperationTypeDefinition> operationTypeDefinitions = schemaDefinition.getOperationTypeDefinitions();

            operationTypeDefinitions
                    .forEach(checkOperationTypesExist(typeRegistry, errors));

            // ensure we have a "query" one
            Optional<OperationTypeDefinition> query = operationTypeDefinitions.stream().filter(op -> "query".equals(op.getName())).findFirst();
            if (! query.isPresent()) {
                errors.add(new QueryOperationMissingError());
            }
        }

        return errors;

    }

    private void checkFieldTypesPresent(TypeRegistry typeRegistry, List<GraphQLError> errors, TypeDefinition typeDefinition, List<FieldDefinition> fields) {
        List<Type> fieldTypes = fields.stream().map(FieldDefinition::getType).collect(Collectors.toList());
        fieldTypes.forEach(checkTypeExists("field", typeRegistry, errors, typeDefinition));

        List<Type> fieldInputValues = fields.stream()
                .map(f -> f.getInputValueDefinitions()
                        .stream()
                        .map(InputValueDefinition::getType)
                        .collect(Collectors.toList()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        fieldInputValues.forEach(checkTypeExists("field input", typeRegistry, errors, typeDefinition));
    }

    private Consumer<Type> checkTypeExists(String typeOfType, TypeRegistry typeRegistry, List<GraphQLError> errors, TypeDefinition typeDefinition) {
        return t -> {
            TypeName unwrapped = typeInfo(t).getTypeName();
            if (!typeRegistry.hasType(unwrapped)) {
                errors.add(new MissingTypeError(typeOfType, typeDefinition, unwrapped));
            }
        };
    }

    private Consumer<OperationTypeDefinition> checkOperationTypesExist(TypeRegistry typeRegistry, List<GraphQLError> errors) {
        return op -> {
            TypeName unwrapped = typeInfo(op.getType()).getTypeName();
            if (!typeRegistry.hasType(unwrapped)) {
                errors.add(new MissingTypeError("operation", op, op.getName(), unwrapped));
            }
        };
    }

    private <T extends TypeDefinition> List<T> filterTo(Map<String, TypeDefinition> types, Class<? extends T> clazz) {
        return types.values().stream()
                .filter(t -> clazz.equals(t.getClass()))
                .map(clazz::cast)
                .collect(Collectors.toList());
    }

}
