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
 * for reserved-word shape names and reserved member field names.
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

  /** Escaped field names for RwKwStruct members {@code after} and {@code receive}. */
  private static final String FIELD_AFTER = "after_";

  private static final String FIELD_RECEIVE = "receive_";
  private static final String RW_KW_STRUCT_RECORD = "rw_kw_struct";

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

    assertErlangCodecUsesEscapedRecordNamesFromTypes(types, codec);
    assertErlangCodecUsesEscapedMemberFieldsFromTypes(types, codec);
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

    assertErlangCodecUsesEscapedRecordNamesFromTypes(types, serverCodec);
    assertErlangCodecUsesEscapedMemberFieldsFromTypes(types, serverCodec);
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
    assertElixirCodecUsesEscapedMemberFieldsFromTypes(types, codec);
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
    assertElixirCodecUsesEscapedMemberFieldsFromTypes(types, serverCodec);
  }

  private static void assertErlangCodecUsesEscapedRecordNamesFromTypes(String types, String codec) {
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

  /**
   * RwKwStruct members {@code after} / {@code receive} must appear as escaped fields in types and
   * as the same field atoms in codec record access (not bare reserved atoms).
   */
  private static void assertErlangCodecUsesEscapedMemberFieldsFromTypes(
      String types, String codec) {
    Pattern rwKwStructRec =
        Pattern.compile(
            "-record\\(\\s*" + Pattern.quote(RW_KW_STRUCT_RECORD) + "\\s*,\\s*\\{([^}]*)\\}",
            Pattern.DOTALL);
    Matcher recMatcher = rwKwStructRec.matcher(types);
    assertThat(recMatcher.find())
        .as("types must define -record(%s, ...)", RW_KW_STRUCT_RECORD)
        .isTrue();
    String recordBody = recMatcher.group(1);
    assertThat(recordBody).contains(FIELD_AFTER).contains(FIELD_RECEIVE);
    assertThat(recordBody).doesNotContain("after ::").doesNotContain("receive ::");

    assertThat(codec).contains("#" + RW_KW_STRUCT_RECORD + "{");
    assertThat(codec).contains(FIELD_AFTER).contains(FIELD_RECEIVE);
    // Bare reserved names must not appear as Erlang record fields (wire JSON keys may still use
    // the original member names). Word-boundary patterns avoid false hits on after_ / receive_.
    assertThat(codec).doesNotMatch("(?s).*\\.after(?!\\w).*");
    assertThat(codec).doesNotMatch("(?s).*\\.receive(?!\\w).*");
    assertThat(codec).doesNotMatch("(?s).*(?<!\\w)after\\s*=.*");
    assertThat(codec).doesNotMatch("(?s).*(?<!\\w)receive\\s*=.*");
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

  /**
   * RwKwStruct defstruct must list escaped member atoms, and the codec must reference those same
   * atoms (not bare {@code :after} / {@code :receive} as struct fields).
   */
  private static void assertElixirCodecUsesEscapedMemberFieldsFromTypes(
      String types, String codec) {
    assertThat(types).contains(":" + FIELD_AFTER).contains(":" + FIELD_RECEIVE);
    Pattern defstructPat = Pattern.compile("defstruct\\s*\\[([^\\]]+)\\]");
    Matcher m = defstructPat.matcher(types);
    boolean foundEscapedFields = false;
    while (m.find()) {
      String fields = m.group(1);
      if (fields.contains(":" + FIELD_AFTER) && fields.contains(":" + FIELD_RECEIVE)) {
        foundEscapedFields = true;
        break;
      }
    }
    assertThat(foundEscapedFields)
        .as("types defstruct must list :%s and :%s", FIELD_AFTER, FIELD_RECEIVE)
        .isTrue();

    assertThat(codec).contains(":" + FIELD_AFTER).contains(":" + FIELD_RECEIVE);
    // Word-boundary patterns avoid false hits on :after_ / :receive_.
    assertThat(codec).doesNotMatch("(?s).*:after(?!\\w).*");
    assertThat(codec).doesNotMatch("(?s).*:receive(?!\\w).*");
    assertThat(codec).doesNotMatch("(?s).*\\.after(?!\\w).*");
    assertThat(codec).doesNotMatch("(?s).*\\.receive(?!\\w).*");
  }
}
