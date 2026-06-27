package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.ir.erlang.ErlModule;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangResourceIrTest {

    @Test
    void clientModuleMatchesResourceLifecycleExpectations() {
        Model model = resourceLifecycleModel();
        ServiceShape service = model.expectShape(
                ShapeId.from("smithy.beam.demo.resource_lifecycle#ResourceLifecycleService"),
                ServiceShape.class);
        ResourceShape organization = model.expectShape(
                ShapeId.from("smithy.beam.demo.resource_lifecycle#Organization"), ResourceShape.class);
        BeamErlangLayout layout = new BeamErlangLayout(
                new io.smithy.beam.core.BeamSettings(), service.getId().getNamespace(), service);
        SymbolProvider sp = SymbolProvider.cache(
                new ErlangSymbolProvider(
                        new io.smithy.beam.core.BeamSettings(),
                        model,
                        service,
                        layout.clientModuleFile(),
                        io.smithy.beam.core.BeamCodegenKind.CLIENT));
        ErlangContext ctx = new ErlangContext(
                model,
                new io.smithy.beam.core.BeamSettings(),
                sp,
                new MockManifest(),
                new WriterDelegator<>(new MockManifest(), sp, ErlangWriter.factory()),
                List.of(),
                service,
                io.smithy.beam.core.BeamHttpBindings.from(model),
                null,
                null,
                layout.clientModuleName(),
                layout.clientModuleFile(),
                null);
        BeamResourceIndex index = BeamResourceIndex.of(model);
        ErlModule module = ErlangResourceIr.clientModule(
                ctx, organization, index, layout, layout.clientModuleName());
        String org = module.asString();
        assertThat(org).contains("-module(organization_resource).");
        assertThat(org).contains("-type client_config() :: #{binary() => term()}.");
        assertThat(org).contains("read/2");
        assertThat(org).contains("resource_lifecycle_service_client:get_organization(");
        assertThat(org).contains("org_id = org_id");
        assertThat(org).contains("create(Config, Input) ->");
        assertThat(org).contains("resource_lifecycle_service_client:create_organization(Config, Input)");
        assertThat(org).contains("Top-level organization resource.");
    }

    @Test
    void serverModuleMatchesResourceLifecycleExpectations() {
        Model model = resourceLifecycleModel();
        ServiceShape service = model.expectShape(
                ShapeId.from("smithy.beam.demo.resource_lifecycle#ResourceLifecycleService"),
                ServiceShape.class);
        ResourceShape organization = model.expectShape(
                ShapeId.from("smithy.beam.demo.resource_lifecycle#Organization"), ResourceShape.class);
        BeamErlangLayout layout = new BeamErlangLayout(
                new io.smithy.beam.core.BeamSettings(), service.getId().getNamespace(), service);
        SymbolProvider sp = SymbolProvider.cache(
                new ErlangSymbolProvider(
                        new io.smithy.beam.core.BeamSettings(),
                        model,
                        service,
                        layout.serverModuleFile(),
                        io.smithy.beam.core.BeamCodegenKind.SERVER));
        ErlangContext ctx = new ErlangContext(
                model,
                new io.smithy.beam.core.BeamSettings(),
                sp,
                new MockManifest(),
                new WriterDelegator<>(new MockManifest(), sp, ErlangWriter.factory()),
                List.of(),
                service,
                io.smithy.beam.core.BeamHttpBindings.from(model),
                null,
                null,
                layout.serverModuleName(),
                layout.serverModuleFile(),
                null,
                null,
                null);
        BeamResourceIndex index = BeamResourceIndex.of(model);
        ErlModule module = ErlangResourceIr.serverModule(
                ctx, organization, index, layout, layout.serverModuleName());
        String org = module.asString();
        assertThat(org).contains("-module(organization_resource).");
        assertThat(org).doesNotContain("-type client_config()");
        assertThat(org).contains("handle_read/3");
        assertThat(org).contains("resource_lifecycle_service_server:handle_get_organization(");
    }

    private static Model resourceLifecycleModel() {
        String idl = """
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
