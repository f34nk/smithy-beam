package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;

class CredentialProviderEmissionTest {

  private static final ShapeId SIGV4_SERVICE =
      ShapeId.from("smithy.beam.test.sigv4#Sigv4TestService");

  private static final ShapeId BASIC_SERVICE = ShapeId.from("smithy.beam.demo.basic#BasicService");

  @Test
  void erlangSigV4ServiceEmitsCredentialsModuleAndDispatchResolves() {
    MockManifest manifest = runErlang(SIGV4_SERVICE, "/model/sigv4_fixture.smithy");

    String credentials = manifest.expectFileString("sigv4test_service_credentials.erl");
    assertThat(credentials).contains("-module(sigv4test_service_credentials).");
    assertThat(credentials).contains("-export([resolve/1]).");
    assertThat(credentials).contains("resolve_from_env");

    String http = manifest.expectFileString("runtime_http.erl");
    assertThat(http).contains("sigv4test_service_credentials:resolve(Config)");
    assertThat(http).contains("Config#{credentials => Creds}");
    assertThat(credentials).contains("resolve_from_ec2(Config).");
    assertThat(credentials).contains("map_size(Acc) > 0 ->");
    assertThat(credentials).doesNotContain("_ when map_size(Acc)");
    assertThat(credentials).doesNotContain("resolve_from_ec2(Config);");
  }

  @Test
  void elixirSigV4ServiceEmitsCredentialsModuleAndDispatchResolves() {
    MockManifest manifest = runElixir(SIGV4_SERVICE, "/model/sigv4_fixture.smithy");

    String credentials = manifest.expectFileString("sigv4test_service_credentials.ex");
    assertThat(credentials).contains("defmodule Sigv4testServiceCredentials do");
    assertThat(credentials).contains("def resolve(config)");

    String http = manifest.expectFileString("runtime_http.ex");
    assertThat(http).contains("Sigv4testServiceCredentials.resolve(config)");
    assertThat(http).contains("Map.put(config, :credentials, creds)");
  }

  @Test
  void basicServiceOmitsCredentialsModule() {
    MockManifest erlang = runErlang(BASIC_SERVICE, "/model/basic.smithy");
    assertThat(erlang.getFileString("basic_service_credentials.erl")).isEmpty();
    assertThat(erlang.expectFileString("runtime_http.erl")).doesNotContain("_credentials:resolve");

    MockManifest elixir = runElixir(BASIC_SERVICE, "/model/basic.smithy");
    assertThat(elixir.getFileString("basic_service_credentials.ex")).isEmpty();
    assertThat(elixir.expectFileString("runtime_http.ex")).doesNotContain("Credentials.resolve");
  }

  private static MockManifest runErlang(ShapeId service, String modelResource) {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(loadModel(modelResource))
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", service.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }

  private static MockManifest runElixir(ShapeId service, String modelResource) {
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(loadModel(modelResource))
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", service.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }

  private static Model loadModel(String resourcePath) {
    URL resource = CredentialProviderEmissionTest.class.getResource(resourcePath);
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }
}
