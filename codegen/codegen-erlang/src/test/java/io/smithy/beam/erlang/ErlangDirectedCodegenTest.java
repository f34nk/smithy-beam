package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangDirectedCodegenTest {

    private static final String SERVICE_ID = "com.variantorder#VariantOrderService";

    private static Model model;
    private static String typesFile;

    @BeforeAll
    static void setup() {
        String idl = """
                $version: "2"
                namespace com.variantorder

                service VariantOrderService {
                    operations: [GetVariants]
                }

                @readonly
                operation GetVariants {
                    output: VariantBundle
                }

                structure VariantBundle {
                    status: OrderStatus
                    priority: OrderPriority
                    narrow: NarrowUnion
                    wide: WideUnion
                }

                enum OrderStatus {
                    ALPHA
                    BETA
                }

                intEnum OrderPriority {
                    LOW = 1
                    HIGH = 2
                }

                union NarrowUnion {
                    only: OrderString
                }

                union WideUnion {
                    a: OrderString
                    b: OrderInteger
                    c: OrderBoolean
                }

                string OrderString
                integer OrderInteger
                boolean OrderBoolean
                """;

        model = Model.assembler()
                .addUnparsedModel("variant_order.smithy", idl)
                .assemble()
                .unwrap();

        ServiceShape service =
                model.expectShape(ShapeId.from(SERVICE_ID), ServiceShape.class);
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        typesFile = new BeamErlangLayout(settings, service.getId().getNamespace()).typesHeaderFile();
    }

    private static String generateTypes() {
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", SERVICE_ID)
                .withMember("edition", "2026")
                .build();
        PluginContext context = PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build();
        new ErlangTypeGeneration().generate(context);
        return manifest.expectFileString(typesFile);
    }

    private static String lastLineOfTypeDeclaration(String content, String typeName) {
        String marker = "-type " + typeName + "() ::";
        boolean inBlock = false;
        String last = null;
        for (String line : content.split("\n", -1)) {
            if (line.contains(marker)) {
                inBlock = true;
                last = line;
                if (line.strip().endsWith(".")) {
                    return line;
                }
                continue;
            }
            if (inBlock) {
                last = line;
                if (line.strip().endsWith(".")) {
                    return line;
                }
            }
        }
        assertThat(last).as("type block for %s", typeName).isNotNull();
        return last;
    }

    private static String singleLineTypeDeclaration(String content, String typeName) {
        String marker = "-type " + typeName + "() ::";
        for (String line : content.split("\n", -1)) {
            if (line.contains(marker)) {
                return line;
            }
        }
        throw new AssertionError("missing type declaration for " + typeName);
    }

    @Test
    void enumUnknownVariantIsLastOnTypeLine() {
        String line = singleLineTypeDeclaration(generateTypes(), "order_status");
        assertThat(line.strip()).endsWith("{unknown, binary()}.");
        assertThat(line).contains("alpha").contains("beta");
        assertThat(line.indexOf("beta")).isLessThan(line.indexOf("{unknown"));
    }

    @Test
    void intEnumUnknownVariantIsLastOnTypeLine() {
        String line = singleLineTypeDeclaration(generateTypes(), "order_priority");
        assertThat(line.strip()).endsWith("{unknown, integer()}.");
        assertThat(line).contains("low").contains("high");
        assertThat(line.indexOf("high")).isLessThan(line.indexOf("{unknown"));
    }

    @Test
    void narrowUnionUnknownVariantIsLast() {
        String line = lastLineOfTypeDeclaration(generateTypes(), "narrow_union");
        assertThat(line.strip()).endsWith("{unknown, binary()}.");
    }

    @Test
    void wideUnionUnknownVariantIsLastVariantBlock() {
        String line = lastLineOfTypeDeclaration(generateTypes(), "wide_union");
        assertThat(line.strip()).endsWith("{unknown, binary()}.");
        String content = generateTypes();
        int wideStart = content.indexOf("-type wide_union() ::");
        int unknownPos = content.indexOf("{unknown, binary()}", wideStart);
        int aPos = content.indexOf("{a,", wideStart);
        int bPos = content.indexOf("{b,", wideStart);
        int cPos = content.indexOf("{c,", wideStart);
        assertThat(aPos).isGreaterThan(-1);
        assertThat(bPos).isGreaterThan(aPos);
        assertThat(cPos).isGreaterThan(bPos);
        assertThat(unknownPos).isGreaterThan(cPos);
    }
}
