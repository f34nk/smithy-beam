package io.smithy.beam.erlang;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangRuntimeHelpersEmitterTest {

    private static final String SERVICE = "smithy.beam.demo.labels#LabelService";

    private static Model labelModel() {
        String idl = """
                $version: "2"
                namespace smithy.beam.demo.labels

                use aws.protocols#restJson1

                string ItemId

                @restJson1
                service LabelService {
                    version: "2026"
                    operations: [GetItem]
                }

                @readonly
                @http(method: "GET", uri: "/items/{id}", code: 200)
                operation GetItem {
                    input: GetItemInput
                    output: GetItemOutput
                }

                structure GetItemInput {
                    @required
                    @httpLabel
                    id: ItemId
                }

                structure GetItemOutput {
                    id: ItemId
                }
                """;
        return Model.assembler()
                .addUnparsedModel("labels.smithy", idl)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static String generateHelpersModule() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(labelModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest.expectFileString("runtime_helpers.erl");
    }

    @Test
    void matchSegmentsCatchAllAndLabelNameAreModuleLevel() {
        String helpers = generateHelpersModule();
        int catchAll = helpers.indexOf("match_segments(_, _, _) ->");
        int labelName = helpers.indexOf("label_name(<<\"{\", Rest/binary>>) ->");
        assertThat(catchAll).isGreaterThan(0);
        assertThat(labelName).isGreaterThan(catchAll);

        int innerEnd = helpers.indexOf("false -> error");
        assertThat(innerEnd).isGreaterThan(0);
        String closing = helpers.substring(innerEnd);
        assertThat(closing).contains("false -> error\n            end\n    end;\nmatch_segments(_, _, _) ->");

        assertThat(helpers.charAt(catchAll)).isEqualTo('m');
        assertThat(helpers.charAt(labelName)).isEqualTo('l');
    }
}
