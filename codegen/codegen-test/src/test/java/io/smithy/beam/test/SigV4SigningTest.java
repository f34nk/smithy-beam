package io.smithy.beam.test;

import io.smithy.beam.core.BeamSigV4Metadata;
import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.net.URL;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SigV4SigningTest {

    private static final ShapeId SERVICE =
            ShapeId.from("smithy.beam.test.sigv4#Sigv4TestService");

    private static Model loadModel() {
        URL resource = SigV4SigningTest.class.getResource("/model/sigv4_fixture.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void beamSigV4MetadataReadsTraitValues() {
        Model model = loadModel();
        ServiceShape service = model.expectShape(SERVICE, ServiceShape.class);

        Optional<BeamSigV4Metadata> meta = BeamSigV4Metadata.from(service);

        assertThat(meta).isPresent();
        assertThat(meta.get().signingName()).isEqualTo("sigv4test");
        assertThat(meta.get().signingRegion()).isEqualTo("us-east-1");
    }

    @Test
    void erlangClientEmitsSigV4SigningModule() {
        MockManifest manifest = runErlangClient();
        String sigv4 = manifest.expectFileString("sigv4test_service_sigv4.erl");

        assertThat(sigv4).contains("-module(sigv4test_service_sigv4).");
        assertThat(sigv4).contains("aws_sigv4:sign(Request, Credentials, Region, Service).");

        String http = manifest.expectFileString("runtime_http.erl");
        assertThat(http).contains("sigv4test_service_sigv4:sign(Config, Request)");
    }

    @Test
    void elixirClientEmitsSigV4SigningModule() {
        MockManifest manifest = runElixirClient();
        String sigv4 = manifest.expectFileString("sigv4test_service_sigv4.ex");

        assertThat(sigv4).contains("defmodule Sigv4testServiceSigv4 do");
        assertThat(sigv4).contains("AwsSignature.sign(request, credentials, region, service)");

        String http = manifest.expectFileString("runtime_http.ex");
        assertThat(http).contains("Sigv4testServiceSigv4.sign(config, req)");
    }

    @Test
    void basicServiceOmitsSigV4Module() {
        URL resource = SigV4SigningTest.class.getResource("/model/basic.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", "smithy.beam.demo.basic#BasicService")
                        .withMember("edition", "2026")
                        .build())
                .build());

        assertThat(manifest.getFileString("basic_service_sigv4.erl")).isEmpty();
        assertThat(manifest.expectFileString("runtime_http.erl"))
                .doesNotContain(":sign(Config, Request)");
    }

    private static MockManifest runErlangClient() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(loadModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    private static MockManifest runElixirClient() {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(loadModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }
}
