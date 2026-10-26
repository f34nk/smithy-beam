package io.smithy.beam.gleam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Optional compile check for stub client and server modules when the Gleam CLI is on PATH.
 */
class GleamStubCompileCheckTest {

  @TempDir Path tempDir;

  @Test
  void gleamBuildSucceedsForStubsWhenCliPresent() throws Exception {
    assumeTrue(gleamAvailable(), "Gleam CLI not on PATH; skipping optional stub compile check");

    Model model =
        Model.assembler()
            .addUnparsedModel(
                "test.smithy",
                """
                $version: "2"
                namespace com.example

                service StubService {
                    operations: [Ping]
                }

                operation Ping {
                    input: PingInput
                    output: PingOutput
                }

                structure PingInput {
                    @required
                    name: NamedString
                }

                structure PingOutput {
                    name: NamedString
                }

                string NamedString
                """)
            .assemble()
            .unwrap();

    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "com.example#StubService")
            .withMember("edition", "2026")
            .build();
    PluginContext context =
        PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build();

    new GleamClientPlugin().execute(context);
    // Server overwrites types on the same manifest; regenerate server into a fresh manifest.
    MockManifest serverManifest = new MockManifest();
    new GleamServerPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(serverManifest)
                .settings(settings)
                .build());

    Path srcDir = tempDir.resolve("src");
    Files.createDirectories(srcDir);
    write(srcDir, "stub_service_types.gleam", manifest.expectFileString("stub_service_types.gleam"));
    write(srcDir, "stub_service_client.gleam", manifest.expectFileString("stub_service_client.gleam"));
    write(
        srcDir,
        "stub_service_behaviour.gleam",
        serverManifest.expectFileString("stub_service_behaviour.gleam"));
    write(
        srcDir, "stub_service_impl.gleam", serverManifest.expectFileString("stub_service_impl.gleam"));
    write(
        srcDir,
        "stub_service_server.gleam",
        serverManifest.expectFileString("stub_service_server.gleam"));
    write(
        srcDir,
        "stub_service_router.gleam",
        serverManifest.expectFileString("stub_service_router.gleam"));

    Files.writeString(
        tempDir.resolve("gleam.toml"),
        """
        name = "smithy_beam_gleam_stub_compile_check"
        version = "0.1.0"

        [dependencies]
        gleam_stdlib = ">= 0.59.0 and < 2.0.0"
        """,
        StandardCharsets.UTF_8);

    Process process =
        new ProcessBuilder("gleam", "build")
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .start();
    boolean finished = process.waitFor(3, TimeUnit.MINUTES);
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(finished).as("gleam build timed out").isTrue();
    assertThat(process.exitValue())
        .as("gleam build failed:%n%s", output)
        .isZero();
  }

  private static void write(Path srcDir, String fileName, String content) throws IOException {
    Files.writeString(srcDir.resolve(fileName), content, StandardCharsets.UTF_8);
  }

  private static boolean gleamAvailable() {
    try {
      Process process =
          new ProcessBuilder("gleam", "--version").redirectErrorStream(true).start();
      boolean finished = process.waitFor(10, TimeUnit.SECONDS);
      return finished && process.exitValue() == 0;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
