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
 * Optional compile check: when the Gleam CLI is on PATH, generate types into a
 * temporary package and run {@code gleam build}.
 */
class GleamTypesCompileCheckTest {

  @TempDir Path tempDir;

  @Test
  void gleamBuildSucceedsWhenCliPresent() throws Exception {
    assumeTrue(gleamAvailable(), "Gleam CLI not on PATH; skipping optional compile check");

    Model model =
        Model.assembler()
            .addUnparsedModel(
                "test.smithy",
                """
                $version: "2"
                namespace com.example

                use smithy.api#error

                service CompileService {
                    operations: [Get]
                }

                operation Get {
                    input: GetInput
                    output: GetOutput
                    errors: [NotFound]
                }

                structure GetInput {}

                structure GetOutput {
                    name: NamedString
                    when: NamedTimestamp
                    amount: NamedBigDecimal
                    doc: NamedDocument
                    items: NamedList
                    labels: NamedMap
                    status: Status
                    choice: Choice
                    item: Item
                }

                string NamedString
                timestamp NamedTimestamp
                bigDecimal NamedBigDecimal
                document NamedDocument

                list NamedList {
                    member: NamedString
                }

                map NamedMap {
                    key: NamedString
                    value: NamedString
                }

                enum Status {
                    ACTIVE
                    INACTIVE
                }

                union Choice {
                    text: NamedString
                    count: NamedInt
                }

                integer NamedInt

                structure Item {
                    @required
                    name: NamedString
                    count: NamedInt
                }

                @error("client")
                structure NotFound {
                    message: NamedString
                }
                """)
            .assemble()
            .unwrap();

    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "com.example#CompileService")
            .withMember("edition", "2026")
            .build();
    new GleamTypesPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());

    String source = manifest.expectFileString("compile_service_types.gleam");
    Path srcDir = tempDir.resolve("src");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("compile_service_types.gleam"), source, StandardCharsets.UTF_8);
    Files.writeString(
        tempDir.resolve("gleam.toml"),
        """
        name = "smithy_beam_gleam_compile_check"
        version = "0.1.0"

        [dependencies]
        gleam_stdlib = ">= 0.59.0 and < 2.0.0"
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
    assertThat(process.exitValue())
        .as("gleam build failed:%n%s", output)
        .isZero();
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
