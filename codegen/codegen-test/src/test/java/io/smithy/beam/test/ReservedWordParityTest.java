package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.elixir.ElixirServerPlugin;
import io.smithy.beam.elixir.ElixirTypesPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import io.smithy.beam.erlang.ErlangServerPlugin;
import io.smithy.beam.erlang.ErlangTypesPlugin;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Proves that client and server plugins produce the same escaped identifiers as the types plugin
 * for reserved-word shape names.
 */
class ReservedWordParityTest {

  private static final ObjectNode SETTINGS =
      ObjectNode.builder()
          .withMember("service", "smithy.beam.demo.reserved#ReservedService")
          .withMember("edition", "2026")
          .build();

  private static final String TYPES_FILE = "reserved_service_types.hrl";
  private static final String CLIENT_CODEC_FILE = "reserved_service_rest_json_1.erl";
  private static final String SERVER_CODEC_FILE = "reserved_service_rest_json_1.erl";
  private static final String ELIXIR_TYPES_FILE = "reserved_service_types.ex";
  private static final String ELIXIR_CODEC_FILE = "reserved_service_rest_json_1.ex";

  private static Model loadReservedWordsModel() {
    URL resource = ReservedWordParityTest.class.getResource("/model/reserved_words.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  @Test
  void clientCodecUsesEscapedNamesFromTypesSymbolProvider() {
    Model model = loadReservedWordsModel();

    MockManifest typesManifest = new MockManifest();
    new ErlangTypesPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(typesManifest)
                .settings(SETTINGS)
                .build());

    MockManifest clientManifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(clientManifest)
                .settings(SETTINGS)
                .build());

    String types = typesManifest.getFileString(TYPES_FILE).orElse("");
    String codec = clientManifest.getFileString(CLIENT_CODEC_FILE).orElse("");
    assertThat(codec).isNotEmpty();

    Pattern recPat = Pattern.compile("-record\\(([^,]+),");
    Matcher m = recPat.matcher(types);
    boolean matched = false;
    while (m.find()) {
      String recName = m.group(1).trim();
      if (codec.contains("#" + recName) || codec.contains(recName + "{")) {
        assertThat(codec).contains(recName);
        matched = true;
      }
    }
    assertThat(matched).isTrue();
  }

  @Test
  void serverCodecUsesEscapedNamesFromTypesSymbolProvider() {
    Model model = loadReservedWordsModel();

    MockManifest typesManifest = new MockManifest();
    new ErlangTypesPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(typesManifest)
                .settings(SETTINGS)
                .build());

    MockManifest serverManifest = new MockManifest();
    new ErlangServerPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(serverManifest)
                .settings(SETTINGS)
                .build());

    String types = typesManifest.getFileString(TYPES_FILE).orElse("");
    String serverCodec = serverManifest.getFileString(SERVER_CODEC_FILE).orElse("");
    assertThat(serverCodec).isNotEmpty();

    Pattern recPat = Pattern.compile("-record\\(([^,]+),");
    Matcher m = recPat.matcher(types);
    boolean matched = false;
    while (m.find()) {
      String recName = m.group(1).trim();
      if (serverCodec.contains("#" + recName) || serverCodec.contains(recName + "{")) {
        assertThat(serverCodec).contains(recName);
        matched = true;
      }
    }
    assertThat(matched).isTrue();
  }

  @Test
  void elixirClientCodecUsesEscapedNamesFromTypesSymbolProvider() {
    Model model = loadReservedWordsModel();

    MockManifest typesManifest = new MockManifest();
    new ElixirTypesPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(typesManifest)
                .settings(SETTINGS)
                .build());

    MockManifest clientManifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(clientManifest)
                .settings(SETTINGS)
                .build());

    String types = typesManifest.getFileString(ELIXIR_TYPES_FILE).orElse("");
    String codec = clientManifest.getFileString(ELIXIR_CODEC_FILE).orElse("");
    assertThat(codec).isNotEmpty();
    assertElixirCodecUsesEscapedNamesFromTypes(types, codec);
  }

  @Test
  void elixirServerCodecUsesEscapedNamesFromTypesSymbolProvider() {
    Model model = loadReservedWordsModel();

    MockManifest typesManifest = new MockManifest();
    new ElixirTypesPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(typesManifest)
                .settings(SETTINGS)
                .build());

    MockManifest serverManifest = new MockManifest();
    new ElixirServerPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(serverManifest)
                .settings(SETTINGS)
                .build());

    String types = typesManifest.getFileString(ELIXIR_TYPES_FILE).orElse("");
    String serverCodec = serverManifest.getFileString(ELIXIR_CODEC_FILE).orElse("");
    assertThat(serverCodec).isNotEmpty();
    assertElixirCodecUsesEscapedNamesFromTypes(types, serverCodec);
  }

  private static void assertElixirCodecUsesEscapedNamesFromTypes(String types, String codec) {
    Pattern defstructPat = Pattern.compile("defstruct\\s*\\[([^\\]]+)\\]");
    Matcher m = defstructPat.matcher(types);
    boolean matched = false;
    while (m.find()) {
      String fields = m.group(1);
      for (String field : fields.split(",")) {
        String atom = field.trim();
        if (atom.startsWith(":")) {
          atom = atom.substring(1);
        }
        if (codec.contains(":" + atom) || codec.contains("." + atom)) {
          assertThat(codec).contains(atom);
          matched = true;
        }
      }
    }
    assertThat(matched).isTrue();
  }
}
