package io.smithy.beam.gleam;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;

class GleamSymbolProviderTest {

  static final String DEF_FILE = "test_types.gleam";
  static final String MODULE_NAMESPACE = "example";

  private static BeamSettings testSettings() {
    BeamSettings s = new BeamSettings();
    s.edition("2026");
    return s;
  }

  static Model model;
  static ServiceShape service;
  static GleamSymbolProvider provider;

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

                structure TestInput {
                    stringField: TestString
                    intField: TestInteger
                    boolField: TestBoolean
                    blobField: TestBlob
                    timestampField: TestTimestamp
                    bigDecField: TestBigDecimal
                    enumField: TestStatus
                    otherEnumField: OtherStatus
                    intEnumField: TestPriority
                    listField: TestList
                    mapField: TestMap
                    unionField: TestUnion
                    itemField: TestItem
                    typeField: Type
                }

                structure TestOutput {}

                string TestString
                integer TestInteger
                boolean TestBoolean
                blob TestBlob
                timestamp TestTimestamp
                bigDecimal TestBigDecimal
                string Type

                enum TestStatus {
                    ACTIVE
                    INACTIVE
                }

                enum OtherStatus {
                    ACTIVE
                    PENDING
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
        new GleamSymbolProvider(
            testSettings(), model, service, DEF_FILE, MODULE_NAMESPACE, BeamCodegenKind.TYPES);
  }

  @Nested
  class PreludeShapes {

    @Test
    void stringMapsToBuiltinString() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#String")));
      assertThat(sym.getName()).isEqualTo("String");
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(true);
    }

    @Test
    void blobMapsToBitArray() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#Blob")));
      assertThat(sym.getName()).isEqualTo("BitArray");
    }

    @Test
    void timestampMapsToGleamTime() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#Timestamp")));
      assertThat(sym.getName()).isEqualTo("timestamp.Timestamp");
    }

    @Test
    void bigDecimalMapsToHexPackage() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("smithy.api#BigDecimal")));
      assertThat(sym.getName()).isEqualTo("bigdecimal.BigDecimal");
    }
  }

  @Nested
  class NamedScalarShapes {

    @Test
    void namedStringIsPascalCaseAlias() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestString")));
      assertThat(sym.getName()).isEqualTo("TestString");
      assertThat(sym.getProperty("baseType", String.class)).contains("String");
      assertThat(sym.getProperty("builtIn", Boolean.class)).contains(false);
      assertThat(sym.getDefinitionFile()).isEqualTo(DEF_FILE);
    }

    @Test
    void reservedTypeNameIsEscapedThenPascalCased() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#Type")));
      assertThat(sym.getName()).isEqualTo("Type_");
    }

    @Test
    void namedTimestampHasGleamTimeBaseType() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestTimestamp")));
      assertThat(sym.getProperty("baseType", String.class)).contains("timestamp.Timestamp");
    }
  }

  @Nested
  class AggregateAndConstructors {

    @Test
    void listAndMapAreNamedAliases() {
      assertThat(provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestList"))).getName())
          .isEqualTo("TestList");
      assertThat(provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestMap"))).getName())
          .isEqualTo("TestMap");
    }

    @Test
    void enumConstructorsAreShortPascalCaseWhenUnique() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestPriority")));
      @SuppressWarnings("unchecked")
      Map<String, String> byMember =
          sym.getProperty("enumConstructorByMember", Map.class).orElseThrow();
      assertThat(byMember.get("LOW")).isEqualTo("Low");
      assertThat(byMember.get("HIGH")).isEqualTo("High");
    }

    @Test
    void collidingEnumConstructorsAreShapePrefixed() {
      Symbol status = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestStatus")));
      Symbol other = provider.toSymbol(model.expectShape(ShapeId.from("com.example#OtherStatus")));
      @SuppressWarnings("unchecked")
      Map<String, String> statusMembers =
          status.getProperty("enumConstructorByMember", Map.class).orElseThrow();
      @SuppressWarnings("unchecked")
      Map<String, String> otherMembers =
          other.getProperty("enumConstructorByMember", Map.class).orElseThrow();
      assertThat(statusMembers.get("ACTIVE")).isEqualTo("TestStatusActive");
      assertThat(otherMembers.get("ACTIVE")).isEqualTo("OtherStatusActive");
      assertThat(statusMembers.get("INACTIVE")).isEqualTo("Inactive");
      assertThat(otherMembers.get("PENDING")).isEqualTo("Pending");
    }

    @Test
    void unknownConstructorsDeconflictAcrossTypes() {
      Symbol status = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestStatus")));
      Symbol priority =
          provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestPriority")));
      assertThat(status.getProperty("unknownConstructor", String.class).orElseThrow())
          .isNotEqualTo(priority.getProperty("unknownConstructor", String.class).orElseThrow());
      assertThat(status.getProperty("unknownConstructor", String.class).orElseThrow())
          .contains("Unknown");
    }

    @Test
    void structureUsesMatchingConstructorName() {
      Symbol sym = provider.toSymbol(model.expectShape(ShapeId.from("com.example#TestItem")));
      assertThat(sym.getName()).isEqualTo("TestItem");
      assertThat(provider.constructorName(model.expectShape(ShapeId.from("com.example#TestItem")), "TestItem"))
          .isEqualTo("TestItem");
    }
  }
}
