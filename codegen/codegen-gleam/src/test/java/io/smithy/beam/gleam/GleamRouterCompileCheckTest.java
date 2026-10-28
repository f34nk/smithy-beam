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

/** Optional gleam build of a restJson1 router when the Gleam CLI is on PATH. */
class GleamRouterCompileCheckTest {

  @TempDir Path tempDir;

  @Test
  void gleamBuildSucceedsForRestJsonRouterWhenCliPresent() throws Exception {
    assumeTrue(gleamAvailable(), "Gleam CLI not on PATH; skipping optional router compile check");

    Model model =
        Model.assembler()
            .addImport(getClass().getResource("/model/protocol_rest_json_fixture.smithy"))
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
            .withMember("edition", "2026")
            .build();
    new GleamServerPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

    Path srcDir = tempDir.resolve("src");
    Files.createDirectories(srcDir);
    for (Path file : manifest.getFiles()) {
      String name = file.getFileName().toString();
      if (name.endsWith(".gleam")) {
        write(srcDir, name, manifest.expectFileString(file));
      }
    }

    Files.writeString(
        tempDir.resolve("gleam.toml"),
        """
        name = "smithy_beam_gleam_router_compile_check"
        version = "0.1.0"

        [dependencies]
        gleam_stdlib = ">= 0.59.0 and < 2.0.0"
        gleam_http = ">= 4.0.0 and < 5.0.0"
        gleam_json = ">= 3.0.0 and < 4.0.0"
        gleam_time = ">= 1.0.0 and < 2.0.0"
        bigdecimal = ">= 1.0.0 and < 2.0.0"
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
    assertThat(process.exitValue()).as("gleam build failed:%n%s", output).isZero();
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
