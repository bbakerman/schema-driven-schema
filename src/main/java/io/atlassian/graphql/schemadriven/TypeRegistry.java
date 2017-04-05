package io.atlassian.graphql.schemadriven;

import graphql.GraphQLError;
import graphql.language.Definition;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeExtensionDefinition;
import graphql.language.TypeName;
import io.atlassian.fugue.Option;
import io.atlassian.graphql.schemadriven.errors.SchemaRedefinitionError;
import io.atlassian.graphql.schemadriven.errors.TypeRedefinitionError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.atlassian.fugue.Option.none;
import static io.atlassian.fugue.Option.option;
import static io.atlassian.fugue.Option.some;

public class TypeRegistry {

    private final Map<String, ScalarTypeDefinition> standardScalarTypes = new LinkedHashMap<>();
    private final Map<String, ScalarTypeDefinition> scalarTypes = new LinkedHashMap<>();
    private final Map<String, TypeExtensionDefinition> typeExtensions = new LinkedHashMap<>();
    private final Map<String, TypeDefinition> types = new LinkedHashMap<>();
    private SchemaDefinition schema;

    public TypeRegistry() {
        seedScalars();
    }

    private void seedScalars() {
        // graphql standard scalars
        addStandardScalar("Int");
        addStandardScalar("Float");
        addStandardScalar("String");
        addStandardScalar("Boolean");
        addStandardScalar("ID");

        // graphql-java library extensions
        addStandardScalar("Long");
        addStandardScalar("BigInteger");
        addStandardScalar("BigDecimal");
        addStandardScalar("Short");
        addStandardScalar("Char");

    }

    private void addStandardScalar(String scalarName) {
        standardScalarTypes.put(scalarName, new ScalarTypeDefinition(scalarName));
    }

    public Option<GraphQLError> add(Definition definition) {
        if (definition instanceof TypeExtensionDefinition) {
            TypeExtensionDefinition newEntry = (TypeExtensionDefinition) definition;
            return define(typeExtensions, typeExtensions, newEntry);
        } else if (definition instanceof ScalarTypeDefinition) {
            ScalarTypeDefinition newEntry = (ScalarTypeDefinition) definition;
            return define(scalarTypes, scalarTypes, newEntry);
        } else if (definition instanceof TypeDefinition) {
            TypeDefinition newEntry = (TypeDefinition) definition;
            return define(types, types, newEntry);
        } else if (definition instanceof SchemaDefinition) {
            SchemaDefinition newSchema = (SchemaDefinition) definition;
            if (schema != null) {
                return some(new SchemaRedefinitionError(this.schema, newSchema));
            } else {
                schema = newSchema;
            }
        }
        return none();
    }

    private <T extends TypeDefinition> Option<GraphQLError> define(Map<String, T> source, Map<String, T> target, T newEntry) {
        String name = newEntry.getName();

        T olderEntry = source.get(name);
        if (olderEntry != null) {
            return some(handleReDefinition(olderEntry, newEntry));
        } else {
            target.put(name, newEntry);
        }
        return Option.none();
    }

    public Option<List<GraphQLError>> merge(TypeRegistry typeRegistry) {
        List<GraphQLError> errors = new ArrayList<>();

        Map<String, TypeDefinition> tempTypes = new LinkedHashMap<>();
        typeRegistry.types.values().forEach(newEntry -> define(this.types, tempTypes, newEntry).forEach(errors::add));

        Map<String, ScalarTypeDefinition> tempScalarTypes = new LinkedHashMap<>();
        typeRegistry.scalarTypes.values().forEach(newEntry -> define(this.scalarTypes, tempScalarTypes, newEntry).forEach(errors::add));

        Map<String, TypeExtensionDefinition> tempTypeExtensions = new LinkedHashMap<>();
        typeRegistry.typeExtensions.values().forEach(newEntry -> define(this.typeExtensions, tempTypeExtensions, newEntry).forEach(errors::add));

        if (typeRegistry.schema != null && this.schema != null) {
            errors.add(new SchemaRedefinitionError(this.schema, typeRegistry.schema));
        }

        if (!errors.isEmpty()) {
            return some(errors);
        }

        // ok commit to the merge
        this.schema = typeRegistry.schema;
        this.types.putAll(tempTypes);
        this.typeExtensions.putAll(tempTypeExtensions);
        this.scalarTypes.putAll(tempScalarTypes);

        return Option.none();
    }


    public Map<String, TypeDefinition> types() {
        return new LinkedHashMap<>(types);
    }

    public Map<String, ScalarTypeDefinition> scalars() {
        LinkedHashMap<String, ScalarTypeDefinition> scalars = new LinkedHashMap<>(standardScalarTypes);
        scalars.putAll(scalarTypes);
        return scalars;
    }

    public Map<String, TypeExtensionDefinition> typeExtensions() {
        return new LinkedHashMap<>(typeExtensions);
    }

    public Option<SchemaDefinition> schemaDefinition() {
        return option(schema);
    }

    private GraphQLError handleReDefinition(TypeDefinition oldEntry, TypeDefinition newEntry) {
        return new TypeRedefinitionError(newEntry, oldEntry);
    }

    public boolean hasType(TypeName typeName) {
        String name = typeName.getName();
        return types.containsKey(name) || standardScalarTypes.containsKey(name) || scalarTypes.containsKey(name) || typeExtensions.containsKey(name);
    }

    public Option<TypeDefinition> getType(Type type) {
        String typeName = TypeInfo.typeInfo(type).getName();
        TypeDefinition typeDefinition = types.get(typeName);
        if (typeDefinition != null) {
            return some(typeDefinition);
        }
        typeDefinition = scalars().get(typeName);
        if (typeDefinition != null) {
            return some(typeDefinition);
        }
        return none();
    }
}
