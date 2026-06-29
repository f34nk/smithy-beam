package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirResourceIrTest {

  @Test
  void clientModuleMatchesResourceLifecycleExpectations() {
    Model model = resourceLifecycleModel();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.demo.resource_lifecycle#ResourceLifecycleService"),
            ServiceShape.class);
    ResourceShape organization =
        model.expectShape(
            ShapeId.from("smithy.beam.demo.resource_lifecycle#Organization"), ResourceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    SymbolProvider sp =
        SymbolProvider.cache(
            new ElixirSymbolProvider(
                settings,
                model,
                service,
                layout.clientModuleFile(),
                ElixirSymbolProvider.toModuleName(layout.clientModuleName()),
                BeamCodegenKind.CLIENT));
    ElixirContext ctx =
        new ElixirContext(
            model,
            settings,
            sp,
            new MockManifest(),
            new WriterDelegator<>(new MockManifest(), sp, ElixirWriter.factory("client")),
            List.of(),
            service,
            BeamHttpBindings.from(model),
            null,
            null,
            ElixirSymbolProvider.toModuleName(layout.clientModuleName()),
            layout.clientModuleFile());
    BeamResourceIndex index = BeamResourceIndex.of(model);
    String delegateMod = ElixirSymbolProvider.toModuleName(layout.clientModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    ExModule module =
        ElixirResourceIr.clientModule(ctx, organization, index, layout, delegateMod, typesMod);
    String org = module.asString();
    assertThat(org).contains("defmodule OrganizationResource do");
    assertThat(org).contains("alias ResourceLifecycleServiceClient, as: Client");
    assertThat(org).contains("@type client_config :: map()");
    assertThat(org).contains("def read(");
    assertThat(org).contains("Client.get_organization(config,");
    assertThat(org)
        .satisfiesAnyOf(
            s -> assertThat(s).contains("%{input | org_id: org_id}"),
            s ->
                assertThat(s)
                    .contains(
                        "%ResourceLifecycleServiceTypes.GetOrganizationInput{org_id: org_id}"));
    assertThat(org).contains("Top-level organization resource.");
    for (ExFunction fn : module.nestedEntries().stream()
        .filter(ExFunction.class::isInstance)
        .map(ExFunction.class::cast)
        .toList()) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  @Test
  void serverModuleMatchesResourceLifecycleExpectations() {
    Model model = resourceLifecycleModel();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.demo.resource_lifecycle#ResourceLifecycleService"),
            ServiceShape.class);
    ResourceShape organization =
        model.expectShape(
            ShapeId.from("smithy.beam.demo.resource_lifecycle#Organization"), ResourceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    SymbolProvider sp =
        SymbolProvider.cache(
            new ElixirSymbolProvider(
                settings,
                model,
                service,
                layout.serverModuleFile(),
                ElixirSymbolProvider.toModuleName(layout.serverModuleName()),
                BeamCodegenKind.SERVER));
    ElixirContext ctx =
        new ElixirContext(
            model,
            settings,
            sp,
            new MockManifest(),
            new WriterDelegator<>(new MockManifest(), sp, ElixirWriter.factory("server")),
            List.of(),
            service,
            BeamHttpBindings.from(model),
            null,
            null,
            ElixirSymbolProvider.toModuleName(layout.serverModuleName()),
            layout.serverModuleFile());
    BeamResourceIndex index = BeamResourceIndex.of(model);
    String delegateMod = ElixirSymbolProvider.toModuleName(layout.serverModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    ExModule module =
        ElixirResourceIr.serverModule(ctx, organization, index, layout, delegateMod, typesMod);
    String org = module.asString();
    assertThat(org).contains("defmodule OrganizationResource do");
    assertThat(org).contains("alias ResourceLifecycleServiceServer, as: Server");
    assertThat(org).doesNotContain("@type client_config");
    assertThat(org).contains("def handle_read(");
    assertThat(org).contains("Server.handle_get_organization(ctx,");
    assertThat(org).contains("Top-level organization resource.");
    for (ExFunction fn : module.nestedEntries().stream()
        .filter(ExFunction.class::isInstance)
        .map(ExFunction.class::cast)
        .toList()) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  private static Model resourceLifecycleModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.demo.resource_lifecycle

                use aws.protocols#restJson1

                @documentation("Service exercising resource lifecycle helper generation.")
                @restJson1
                service ResourceLifecycleService {
                    version: "2026"
                    resources: [Organization]
                }

                @documentation("Top-level organization resource.")
                resource Organization {
                    identifiers: {
                        orgId: String
                    }
                    read: GetOrganization
                    create: CreateOrganization
                }

                @readonly
                @http(method: "GET", uri: "/orgs/{orgId}", code: 200)
                operation GetOrganization {
                    input: GetOrganizationInput
                    output: GetOrganizationOutput
                }

                structure GetOrganizationInput {
                    @required
                    @httpLabel
                    orgId: String
                }

                structure GetOrganizationOutput {
                    orgId: String
                }

                @http(method: "POST", uri: "/orgs", code: 200)
                operation CreateOrganization {
                    input: CreateOrganizationInput
                    output: CreateOrganizationOutput
                }

                structure CreateOrganizationInput {
                    displayName: String
                }

                structure CreateOrganizationOutput {
                    orgId: String
                }
                """;
    return Model.assembler()
        .addUnparsedModel("resource_lifecycle.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }
}
