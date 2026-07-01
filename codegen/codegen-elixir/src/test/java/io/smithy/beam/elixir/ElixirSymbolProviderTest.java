package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;

class ElixirSymbolProviderTest {

  static final String DEF_FILE = "test_types.ex";
  static final String MODULE_NAMESPACE = "Example";

  private static BeamSettings testSettings() {
    BeamSettings s = new BeamSettings();
    s.edition("2026");
    return s;
  }

  static Model model;
  static ServiceShape service;
  static ElixirSymbolProvider provider;

  @BeforeAll
  static void setup() {
    String idl =
        """
                $version: "2"
                namespace com.example

                service TestService {
                    operations: [TestOperation]
                }

                operation TestOperation {
                    input: TestInput
                    output: TestOutput
                }

                /// Aggregates all shape kinds so the Walker closure includes them.
                structure TestInput {
                    stringField: TestString
                    intField: TestInteger
                    boolField: TestBoolean
                    blobField: TestBlob
                    byteField: TestByte
                    shortField: TestShort
                    longField: TestLong
                    floatField: TestFloat
                    doubleField: TestDouble
                    timestampField: TestTimestamp
                    documentField: TestDocument
                    myDocField: MyDoc
                    bigIntField: TestBigInteger
                    bigDecField: TestBigDecimal
                    enumField: TestStatus
                    intEnumField: TestPriority
                    listField: TestList
                    mapField: TestMap
                    unionField: TestUnion
                    itemField: TestItem
                }

                structure TestOutput {}

                string TestString
                integer TestInteger
                boolean TestBoolean
                blob TestBlob
                byte TestByte
                short TestShort
                long TestLong
                float TestFloat
                double TestDouble
                timestamp TestTimestamp
                document TestDocument
                document MyDoc
                bigInteger TestBigInteger
                bigDecimal TestBigDecimal

                enum TestStatus {
                    ACTIVE
                    INACTIVE
                }

                intEnum TestPriority {
                    LOW = 1
                    HIGH = 2
                }

                list TestList {
                    member: TestString
                }

                map TestMap {
                    key: TestString
                    value: TestInteger
                }

                union TestUnion {
                    text: TestString
                    count: TestInteger
                }

                structure TestItem {
                    name: TestString
                    value: TestInteger
                }
                """;

    model = Model.assembler().addUnparsedModel("test.smithy", idl).assemble().unwrap();
    service = model.expectShape(ShapeId.from("com.example#TestService"), ServiceShape.class);
    provider =
        new ElixirSymbolProvider(
            testSettings(), model, service, DEF_FILE, MODULE_NAMESPACE, BeamCodegenKind.TYPES);
  }

  // ── toSnakeCase ───────────────────────────────────────────────────────────

  @Nested
  class ToSnakeCase {

    @Test
    void convertsLowerCamelCase() {
      assertThat(ElixirSymbolProvider.toSnakeCase("fooBar")).isEqualTo("foo_bar");
    }

    @Test
    void convertsUpperCamelCase() {
      assertThat(ElixirSymbolProvider.toSnakeCase("FooBar")).isEqualTo("foo_bar");
    }

    @Test
    void handlesConsecutiveUppercase() {
      assertThat(ElixirSymbolProvider.toSnakeCase("HTTPRequest")).isEqualTo("http_request");
    }

    @Test
    void preservesAlreadySnakeCase() {
      assertThat(ElixirSymbolProvider.toSnakeCase("foo_bar")).isEqualTo("foo_bar");
    }

    @Test
    void lowercasesSingleWord() {
      assertThat(ElixirSymbolProvider.toSnakeCase("Foo")).isEqualTo("foo");
    }
  }

  // ── Prelude shapes ────────────────────────────────────────────────────────

  @Nested
  class PreludeShapes {

    @Test
    void stringMapsToBuiltinString() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#String")));
      assertThat(sym.getName()).isEqualTo("String.t()");
      assertThat(sym.getDefinitionFile()).isEmpty();
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(true);
    }

    @Test
    void integerMapsToBuiltinInteger() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#Integer")));
      assertThat(sym.getName()).isEqualTo("integer()");
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(true);
    }

    @Test
    void booleanMapsToBuiltinBoolean() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#Boolean")));
      assertThat(sym.getName()).isEqualTo("boolean()");
    }

    @Test
    void blobMapsToBuiltinBinary() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#Blob")));
      assertThat(sym.getName()).isEqualTo("binary()");
    }

    @Test
    void floatMapsToBuiltinFloat() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#Float")));
      assertThat(sym.getName()).isEqualTo("float()");
    }

    @Test
    void doubleMapsToBuiltinFloat() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#Double")));
      assertThat(sym.getName()).isEqualTo("float()");
    }

    @Test
    void timestampMapsToDateTime() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#Timestamp")));
      assertThat(sym.getName()).isEqualTo("DateTime.t()");
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(true);
    }

    @Test
    void documentMapsToAny() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#Document")));
      assertThat(sym.getName()).isEqualTo("any()");
    }

    @Test
    void preludeHasNoBaseTypeProperty() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#String")));
      assertThat(sym.getProperty("baseType")).isEmpty();
    }
  }

  // ── Named scalar shapes ───────────────────────────────────────────────────

  @Nested
  class NamedScalarShapes {

    @Test
    void namedStringHasSnakeCaseAliasName() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestString")));
      assertThat(sym.getName()).isEqualTo("test_string");
    }

    @Test
    void namedStringHasDefinitionFile() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestString")));
      assertThat(sym.getDefinitionFile()).isEqualTo(DEF_FILE);
    }

    @Test
    void namedStringHasBuiltinFalse() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestString")));
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(false);
    }

    @Test
    void namedStringHasStringBaseType() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestString")));
      assertThat(sym.getProperty("baseType", String.class)).contains("String.t()");
    }

    @Test
    void namedIntegerHasIntegerBaseType() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestInteger")));
      assertThat(sym.getProperty("baseType", String.class)).contains("integer()");
    }

    @Test
    void namedBooleanHasBooleanBaseType() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestBoolean")));
      assertThat(sym.getProperty("baseType", String.class)).contains("boolean()");
    }

    @Test
    void namedFloatHasFloatBaseType() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestFloat")));
      assertThat(sym.getProperty("baseType", String.class)).contains("float()");
    }

    @Test
    void namedTimestampHasDateTimeBaseType() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestTimestamp")));
      assertThat(sym.getProperty("baseType", String.class)).contains("DateTime.t()");
    }

    @Test
    void namedBigDecimalHasDecimalBaseType() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestBigDecimal")));
      assertThat(sym.getProperty("baseType", String.class)).contains("Decimal.t()");
    }

    @Test
    void namedBigIntegerHasIntegerBaseType() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestBigInteger")));
      assertThat(sym.getProperty("baseType", String.class)).contains("integer()");
    }
  }

  // ── Aggregate shapes ──────────────────────────────────────────────────────

  @Nested
  class AggregateShapes {

    @Test
    void listShapeHasSnakeCaseName() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestList")));
      assertThat(sym.getName()).isEqualTo("test_list");
    }

    @Test
    void listShapeHasDefinitionFile() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestList")));
      assertThat(sym.getDefinitionFile()).isEqualTo(DEF_FILE);
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(false);
    }

    @Test
    void listShapeHasNoBaseTypeProperty() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestList")));
      assertThat(sym.getProperty("baseType")).isEmpty();
    }

    @Test
    void mapShapeHasDefinitionFile() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestMap")));
      assertThat(sym.getName()).isEqualTo("test_map");
      assertThat(sym.getDefinitionFile()).isEqualTo(DEF_FILE);
    }

    @Test
    void unionShapeHasDefinitionFile() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestUnion")));
      assertThat(sym.getName()).isEqualTo("test_union");
      assertThat(sym.getDefinitionFile()).isEqualTo(DEF_FILE);
    }

    @Test
    void structureShapeHasDefinitionFile() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestItem")));
      assertThat(sym.getName()).isEqualTo("TestItem");
      assertThat(sym.getDefinitionFile()).isEqualTo(DEF_FILE);
    }
  }

  // ── Enum shapes ───────────────────────────────────────────────────────────

  @Nested
  class EnumShapes {

    @Test
    void enumShapeHasModuleName() {
      Symbol sym =
          provider.toSymbol(
              model.expectShape(ShapeId.from("com.example#TestStatus"), EnumShape.class));
      assertThat(sym.getName()).isEqualTo("TestStatus");
    }

    @Test
    void enumShapeHasDefinitionFile() {
      Symbol sym =
          provider.toSymbol(
              model.expectShape(ShapeId.from("com.example#TestStatus"), EnumShape.class));
      assertThat(sym.getDefinitionFile()).isEqualTo(DEF_FILE);
    }

    @Test
    void enumShapeHasEnumAtomsProperty() {
      Symbol sym =
          provider.toSymbol(
              model.expectShape(ShapeId.from("com.example#TestStatus"), EnumShape.class));
      List<?> atoms = sym.expectProperty("enumAtoms", List.class);
      assertThat(atoms).map(Object::toString).containsExactlyInAnyOrder("active", "inactive");
    }

    @Test
    void intEnumShapeHasModuleName() {
      Symbol sym =
          provider.toSymbol(
              model.expectShape(ShapeId.from("com.example#TestPriority"), IntEnumShape.class));
      assertThat(sym.getName()).isEqualTo("TestPriority");
    }

    @Test
    void intEnumShapeHasEnumAtomsProperty() {
      Symbol sym =
          provider.toSymbol(
              model.expectShape(ShapeId.from("com.example#TestPriority"), IntEnumShape.class));
      List<?> atoms = sym.expectProperty("enumAtoms", List.class);
      assertThat(atoms).map(Object::toString).containsExactlyInAnyOrder("low", "high");
    }
  }

  // ── Member symbols ────────────────────────────────────────────────────────

  @Nested
  class MemberSymbols {

    @Test
    void structureMemberSymbolNameMatchesTargetType() {
      StructureShape struct =
          model.expectShape(ShapeId.from("com.example#TestItem"), StructureShape.class);
      MemberShape member = struct.getMember("name").orElseThrow();
      assertThat(provider.toSymbol(member).getName()).isEqualTo("test_string");
    }

    @Test
    void structureMemberHasFieldNameProperty() {
      StructureShape struct =
          model.expectShape(ShapeId.from("com.example#TestItem"), StructureShape.class);
      MemberShape member = struct.getMember("name").orElseThrow();
      assertThat(provider.toSymbol(member).getProperty("fieldName", String.class)).contains("name");
    }

    @Test
    void structureMemberDoesNotHaveUnionTagProperty() {
      StructureShape struct =
          model.expectShape(ShapeId.from("com.example#TestItem"), StructureShape.class);
      MemberShape member = struct.getMember("name").orElseThrow();
      assertThat(provider.toSymbol(member).getProperty("unionTag")).isEmpty();
    }

    @Test
    void unionMemberSymbolNameMatchesTargetType() {
      UnionShape union = model.expectShape(ShapeId.from("com.example#TestUnion"), UnionShape.class);
      MemberShape member = union.getMember("text").orElseThrow();
      assertThat(provider.toSymbol(member).getName()).isEqualTo("test_string");
    }

    @Test
    void unionMemberHasUnionTagProperty() {
      UnionShape union = model.expectShape(ShapeId.from("com.example#TestUnion"), UnionShape.class);
      MemberShape member = union.getMember("text").orElseThrow();
      assertThat(provider.toSymbol(member).getProperty("unionTag", String.class)).contains("text");
    }

    @Test
    void unionMemberDoesNotHaveFieldNameProperty() {
      UnionShape union = model.expectShape(ShapeId.from("com.example#TestUnion"), UnionShape.class);
      MemberShape member = union.getMember("text").orElseThrow();
      assertThat(provider.toSymbol(member).getProperty("fieldName")).isEmpty();
    }
  }

  // ── Service / Operation / Resource ────────────────────────────────────────

  @Nested
  class NonTypeShapes {

    @Test
    void serviceShapeUsesTypesModuleNameAndClearsDefinitionFileForTypesPass() {
      Symbol sym = provider.toSymbol(service);
      assertThat(sym.getName()).isEqualTo("TestServiceTypes");
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(false);
      assertThat(sym.getDefinitionFile()).isEmpty();
    }

    @Test
    void serviceShapeUsesClientModuleNameWhenClientKind() {
      ElixirSymbolProvider clientProvider =
          new ElixirSymbolProvider(
              testSettings(),
              model,
              service,
              "example_client.ex",
              "Example",
              BeamCodegenKind.CLIENT);
      Symbol sym = clientProvider.toSymbol(service);
      assertThat(sym.getName()).isEqualTo("TestServiceClient");
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(false);
      assertThat(sym.getDefinitionFile()).isEqualTo("example_client.ex");
    }

    @Test
    void serviceShapeUsesServerModuleNameWhenServerKind() {
      ElixirSymbolProvider serverProvider =
          new ElixirSymbolProvider(
              testSettings(),
              model,
              service,
              "example_server.ex",
              "Example",
              BeamCodegenKind.SERVER);
      Symbol sym = serverProvider.toSymbol(service);
      assertThat(sym.getName()).isEqualTo("TestServiceServer");
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(false);
      assertThat(sym.getDefinitionFile()).isEqualTo("example_server.ex");
    }

    @Test
    void operationShapeUsesSnakeCaseNameAndClearsDefinitionFileForTypesPass() {
      Symbol sym =
          provider.toSymbol(
              model.expectShape(ShapeId.from("com.example#TestOperation"), OperationShape.class));
      assertThat(sym.getName()).isEqualTo("test_operation");
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(false);
      assertThat(sym.getDefinitionFile()).isEmpty();
    }

    @Test
    void operationShapeUsesDefinitionFileWhenClientKind() {
      ElixirSymbolProvider clientProvider =
          new ElixirSymbolProvider(
              testSettings(),
              model,
              service,
              "test_service_client.ex",
              "Example",
              BeamCodegenKind.CLIENT);
      Symbol sym =
          clientProvider.toSymbol(
              model.expectShape(ShapeId.from("com.example#TestOperation"), OperationShape.class));
      assertThat(sym.getDefinitionFile()).isEqualTo("test_service_client.ex");
      assertThat(sym.getProperty("beamKind", String.class)).contains("CLIENT");
    }

    @Test
    void resourceShapeUsesServiceScopedSnakeName() {
      String idl =
          """
                    $version: "2"
                    namespace com.res

                    service WidgetService {
                        resources: [Widget]
                    }

                    @readonly
                    operation GetWidget {
                        input: WidgetIn
                        output: WidgetOut
                    }

                    structure WidgetIn {
                        @required
                        id: String
                    }

                    structure WidgetOut {}

                    resource Widget {
                        identifiers: {
                            id: String
                        }
                        read: GetWidget
                    }
                    """;

      Model resModel = Model.assembler().addUnparsedModel("widget.smithy", idl).assemble().unwrap();
      ServiceShape resService =
          resModel.expectShape(ShapeId.from("com.res#WidgetService"), ServiceShape.class);
      ResourceShape resource =
          resModel.expectShape(ShapeId.from("com.res#Widget"), ResourceShape.class);
      ElixirSymbolProvider resProvider =
          new ElixirSymbolProvider(
              testSettings(),
              resModel,
              resService,
              "widget_types.ex",
              "Widget",
              BeamCodegenKind.TYPES);

      Symbol sym = resProvider.toSymbol(resource);
      assertThat(sym.getName()).isEqualTo("widget");
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(false);
      assertThat(sym.getDefinitionFile()).isEmpty();
      assertThat(sym.getProperty("beamKind", String.class)).contains("TYPES");
    }
  }

  // ── Reserved word escaping ────────────────────────────────────────────────

  @Nested
  class ReservedWordEscaping {

    @Test
    void toFunctionNameEscapesCaseKeyword() {
      assertThat(provider.toFunctionName("case")).isEqualTo("case_");
    }

    @Test
    void toFunctionNameEscapesReceiveKeyword() {
      assertThat(provider.toFunctionName("receive")).isEqualTo("receive_");
    }

    @Test
    void toFunctionNameConvertsAndEscapesCamelReservedWord() {
      // "GetCase" -> "get_case" -- "get_case" is not reserved so no suffix
      assertThat(provider.toFunctionName("GetCase")).isEqualTo("get_case");
    }

    @Test
    void toFunctionNameConvertsToSnakeCaseWithoutEscaping() {
      assertThat(provider.toFunctionName("GetItem")).isEqualTo("get_item");
    }

    @Test
    void allElixirKeywordsAreEscaped() {
      for (String keyword :
          List.of(
              "after",
              "begin",
              "case",
              "catch",
              "do",
              "else",
              "end",
              "fn",
              "for",
              "if",
              "receive",
              "rescue",
              "try",
              "when",
              "def",
              "defmodule",
              "import",
              "alias",
              "require",
              "use")) {
        assertThat(provider.toFunctionName(keyword))
            .as("keyword '%s' must be escaped", keyword)
            .isEqualTo(keyword + "_");
      }
    }

    @Test
    void operationNamedDefUsesEscapedFunctionSymbol() {
      String idl =
          """
                    $version: "2"
                    namespace com.shadow

                    service ShadowSvc {
                        operations: [Def]
                    }

                    operation Def {}
                    """;
      Model m = Model.assembler().addUnparsedModel("shadow.smithy", idl).assemble().unwrap();
      ServiceShape svc = m.expectShape(ShapeId.from("com.shadow#ShadowSvc"), ServiceShape.class);
      OperationShape op = m.expectShape(ShapeId.from("com.shadow#Def"), OperationShape.class);
      ElixirSymbolProvider p =
          new ElixirSymbolProvider(
              testSettings(), m, svc, "shadow_types.ex", "Shadow", BeamCodegenKind.CLIENT);
      assertThat(p.toSymbol(op).getName()).isEqualTo("def_");
    }

    @Test
    void operationsDefAndLowercaseDefDeconflictAfterEscape() {
      String idl =
          """
                    $version: "2"
                    namespace com.shadow2

                    service Svc {
                        operations: [Def, def_]
                    }

                    operation Def {}
                    operation def_ {}
                    """;
      Model m = Model.assembler().addUnparsedModel("shadow2.smithy", idl).assemble().unwrap();
      ServiceShape svc = m.expectShape(ShapeId.from("com.shadow2#Svc"), ServiceShape.class);
      OperationShape opDef = m.expectShape(ShapeId.from("com.shadow2#Def"), OperationShape.class);
      OperationShape opDefUnderscore =
          m.expectShape(ShapeId.from("com.shadow2#def_"), OperationShape.class);
      ElixirSymbolProvider p =
          new ElixirSymbolProvider(
              testSettings(), m, svc, "s_types.ex", "Shadow2", BeamCodegenKind.TYPES);
      String first = p.toSymbol(opDef).getName();
      String second = p.toSymbol(opDefUnderscore).getName();
      assertThat(first).isEqualTo("def_");
      assertThat(second).isNotEqualTo(first);
      assertThat(second).isEqualTo("def__2");
    }
  }

  // ── Name helpers ──────────────────────────────────────────────────────────

  @Nested
  class NameHelpers {

    @Test
    void toTypeNameProducesSnakeCaseName() {
      Shape shape = model.expectShape(ShapeId.from("com.example#TestString"));
      assertThat(provider.toTypeName(shape)).isEqualTo("test_string");
    }

    @Test
    void toFieldNameProducesSnakeCaseFieldName() {
      StructureShape struct =
          model.expectShape(ShapeId.from("com.example#TestItem"), StructureShape.class);
      MemberShape member = struct.getMember("name").orElseThrow();
      assertThat(provider.toFieldName(member)).isEqualTo("name");
    }

    @Test
    void toUnionTagNameProducesSnakeCaseTag() {
      UnionShape union = model.expectShape(ShapeId.from("com.example#TestUnion"), UnionShape.class);
      MemberShape member = union.getMember("text").orElseThrow();
      assertThat(provider.toUnionTagName(member)).isEqualTo("text");
    }

    @Test
    void toEnumAtomNamesReturnsLowercaseAtoms() {
      EnumShape shape = model.expectShape(ShapeId.from("com.example#TestStatus"), EnumShape.class);
      assertThat(provider.toEnumAtomNames(shape)).containsExactlyInAnyOrder("active", "inactive");
    }

    @Test
    void toIntEnumAtomNamesReturnsLowercaseAtoms() {
      IntEnumShape shape =
          model.expectShape(ShapeId.from("com.example#TestPriority"), IntEnumShape.class);
      assertThat(provider.toEnumAtomNames(shape)).containsExactlyInAnyOrder("low", "high");
    }
  }

  // ── Name deconfliction ────────────────────────────────────────────────────

  @Nested
  class NameDeconfliction {

    @Test
    void deconflictedNamesUseSuffix() {
      // "MyType" and "My_Type" both normalize to "my_type" via toSnakeCase.
      // They are case-insensitively distinct so Smithy permits both in one namespace.
      String idl =
          """
                    $version: "2"
                    namespace com.clash

                    service ClashService {
                        operations: [ClashOp]
                    }

                    operation ClashOp {
                        input: ClashInput
                        output: ClashOutput
                    }

                    structure ClashInput {
                        a: MyType
                        b: My_Type
                    }

                    structure ClashOutput {}

                    string MyType
                    string My_Type
                    """;

      Model clashModel =
          Model.assembler().addUnparsedModel("clash.smithy", idl).assemble().unwrap();
      ServiceShape clashService =
          clashModel.expectShape(ShapeId.from("com.clash#ClashService"), ServiceShape.class);
      ElixirSymbolProvider clashProvider =
          new ElixirSymbolProvider(
              testSettings(),
              clashModel,
              clashService,
              "clash_types.ex",
              "Clash",
              BeamCodegenKind.TYPES);

      Symbol a = clashProvider.toSymbol(clashModel.expectShape(ShapeId.from("com.clash#MyType")));
      Symbol b = clashProvider.toSymbol(clashModel.expectShape(ShapeId.from("com.clash#My_Type")));

      assertThat(a.getName()).isNotEqualTo(b.getName());
      assertThat(List.of(a.getName(), b.getName()))
          .containsExactlyInAnyOrder("my_type", "my_type_2");
    }
  }

  @Nested
  class RedundantPrimitiveScalars {

    static Model model;
    static ElixirSymbolProvider provider;

    @BeforeAll
    static void setup() {
      String idl =
          """
                    $version: "2"
                    namespace com.awsprimitive

                    service AwsPrimitiveService {
                        operations: [GetScalars]
                    }

                    @readonly
                    operation GetScalars {
                        output: ScalarBundle
                    }

                    structure ScalarBundle {
                        f: Float
                    }

                    float Float
                    integer Integer
                    boolean Boolean
                    string String
                    """;
      model = Model.assembler().addUnparsedModel("aws_primitive.smithy", idl).assemble().unwrap();
      ServiceShape service =
          model.expectShape(
              ShapeId.from("com.awsprimitive#AwsPrimitiveService"), ServiceShape.class);
      provider =
          new ElixirSymbolProvider(
              testSettings(), model, service, DEF_FILE, MODULE_NAMESPACE, BeamCodegenKind.TYPES);
    }

    @Test
    void floatShapeResolvesToBuiltinFloat() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.awsprimitive#Float")));
      assertThat(sym.getName()).isEqualTo("float()");
      assertThat(sym.getDefinitionFile()).isEmpty();
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(true);
      assertThat(sym.getProperty("baseType")).isEmpty();
    }

    @Test
    void integerShapeResolvesToBuiltinInteger() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.awsprimitive#Integer")));
      assertThat(sym.getName()).isEqualTo("integer()");
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(true);
    }

    @Test
    void booleanShapeResolvesToBuiltinBoolean() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.awsprimitive#Boolean")));
      assertThat(sym.getName()).isEqualTo("boolean()");
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(true);
    }

    @Test
    void stringShapeResolvesToBuiltinString() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.awsprimitive#String")));
      assertThat(sym.getName()).isEqualTo("String.t()");
      assertThat(sym.getDefinitionFile()).isEmpty();
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(true);
      assertThat(sym.getProperty("baseType")).isEmpty();
    }
  }

  @Nested
  class ClosureBuiltinRegression {

    @Test
    void smithyApiStringRemainsStringBuiltinWithEmptyDefinitionFile() {
      StringShape preludeString =
          model.expectShape(ShapeId.from("smithy.api#String"), StringShape.class);
      Symbol sym = provider.toSymbol(preludeString);
      assertThat(sym.getName()).isEqualTo("String.t()");
      assertThat(sym.getDefinitionFile()).isEmpty();
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(true);
    }

    @Test
    void customDocumentShapeUsesAnySurface() {
      DocumentShape myDoc =
          model.expectShape(ShapeId.from("com.example#MyDoc"), DocumentShape.class);
      Symbol sym = provider.toSymbol(myDoc);
      assertThat(sym.getName()).isEqualTo("my_doc");
      assertThat(sym.getProperty("baseType", String.class)).contains("any()");
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(false);
      assertThat(sym.getDefinitionFile()).isEqualTo(DEF_FILE);
    }
  }
}
