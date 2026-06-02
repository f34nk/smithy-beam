package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class PaginatorEmissionTest {

    private static final String SERVICE = "smithy.beam.test.paginated#PaginatedService";

    private Model paginatedModel() {
        return Model.assembler()
                .addImport(getClass().getResource("/model/paginated_fixture.smithy"))
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void erlangPaginatorModuleWiresTokenAndAccumulate() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(paginatedModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());
        String paginators = manifest.getFileString("paginated_service_paginators.erl").orElse("");
        assertThat(paginators).contains("-module(paginated_service_paginators).");
        assertThat(paginators).contains("paginate_list_widgets/2");
        assertThat(paginators).contains("next_token");
        assertThat(paginators).contains("widgets");
    }

    @Test
    void elixirPaginatorModuleWiresTokenAndAccumulate() {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(paginatedModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());
        String paginators = manifest.getFileString("paginated_service_paginators.ex").orElse("");
        assertThat(paginators).contains("defmodule PaginatedServicePaginators");
        assertThat(paginators).contains("def paginate_list_widgets");
        assertThat(paginators).contains("next_token");
        assertThat(paginators).contains("widgets");
    }
}
