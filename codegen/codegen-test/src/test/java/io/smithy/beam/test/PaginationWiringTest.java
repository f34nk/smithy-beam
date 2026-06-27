package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationWiringTest {

    private static final String SERVICE = "smithy.beam.test.paginated#PaginatedService";

    private Model paginatedModel() {
        return Model.assembler()
                .addImport(getClass().getResource("/model/paginated_fixture.smithy"))
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void erlangClientEmitsPaginationLoopForPaginatedOperations() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(paginatedModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String client = manifest.expectFileString("paginated_service_client.erl");
        assertThat(client).contains("list_widgets(Config, Input) ->");
        assertThat(client).contains("list_widgets(Config, Input, [])");
        assertThat(client).contains("encode_list_widgets_request(Input)");
        assertThat(client).contains("next_token");
        assertThat(client).contains("widgets");
        assertThat(client).contains("#nested_widget_result.items");
    }

    @Test
    void erlangBasicClientPaginatesListBasicItemsOnly() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(Model.assembler()
                        .addImport(getClass().getResource("/model/basic.smithy"))
                        .discoverModels()
                        .assemble()
                        .unwrap())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", "smithy.beam.demo.basic#BasicService")
                        .withMember("edition", "2026")
                        .build())
                .build());

        String client = manifest.expectFileString("basic_service_client.erl");
        assertThat(client).contains("list_basic_items(Config, Input, [])");
        assertThat(client).contains("encode_list_basic_items_request(Input)");
        assertThat(client).doesNotContain("get_type_closure(Config, Input, [])");
    }

    @Test
    void elixirClientEmitsPaginationLoopForPaginatedOperations() {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(paginatedModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String client = manifest.expectFileString("paginated_service_client.ex");
        assertThat(client).contains("def list_widgets(config, input) do");
        assertThat(client).contains("list_widgets(config, input, [])");
        assertThat(client).contains("encode_list_widgets_request(input)");
    }
}
