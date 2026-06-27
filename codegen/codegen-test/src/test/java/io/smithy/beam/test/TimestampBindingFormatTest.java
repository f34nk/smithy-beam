package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import java.net.URL;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

class TimestampBindingFormatTest {

  private static final String DATE_TIME_MODEL =
      """
            $version: "2"
            namespace smithy.beam.demo.timestamps

            use aws.protocols#restJson1

            @restJson1
            service DateTimeTimestampService {
                version: "2026"
                operations: [RecordDateTimeTimestamp]
            }

            @http(method: "POST", uri: "/record-date-time", code: 200)
            operation RecordDateTimeTimestamp {
                input: DateTimeTimestampBody
                output: DateTimeTimestampBody
            }

            structure DateTimeTimestampBody {
                @timestampFormat("date-time")
                createdAt: Timestamp
            }
            """;

  private static final String EPOCH_SECONDS_MODEL =
      """
            $version: "2"
            namespace smithy.beam.demo.timestamps

            use aws.protocols#restJson1

            @restJson1
            service EpochTimestampService {
                version: "2026"
                operations: [RecordEpochTimestamp]
            }

            @http(method: "POST", uri: "/record-epoch", code: 200)
            operation RecordEpochTimestamp {
                input: EpochTimestampBody
                output: EpochTimestampBody
            }

            structure EpochTimestampBody {
                @timestampFormat("epoch-seconds")
                recordedAt: Timestamp
            }
            """;

  @Test
  void fixtureChoosesFormatFromBindingIndex() {
    URL resource =
        Objects.requireNonNull(getClass().getResource("/model/protocol_rest_json_fixture.smithy"));
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    BeamHttpBindings bindings = BeamHttpBindings.from(model);
    ShapeId payload = ShapeId.from("smithy.beam.demo.protocoljson#ItemPayload");
    StructureShape payloadShape = model.expectShape(payload, StructureShape.class);
    MemberShape created = payloadShape.getMember("createdAt").get();
    TimestampFormatTrait.Format format =
        bindings.timestampFormat(
            created, HttpBinding.Location.DOCUMENT, TimestampFormatTrait.Format.DATE_TIME);
    assertThat(format).isNotNull();
  }

  @Test
  void dateTimeTimestampMemberUsesDateTimeHelper() {
    Model model =
        Model.assembler()
            .addUnparsedModel("timestamp_date_time.smithy", DATE_TIME_MODEL)
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest =
        runErlangCodec(model, "smithy.beam.demo.timestamps#DateTimeTimestampService");
    String codec = manifest.getFileString("date_time_timestamp_service_rest_json_1.erl").orElse("");
    assertThat(codec).contains("decode_timestamp_date_time(");
    assertThat(codec).contains("encode_timestamp_date_time(");
  }

  @Test
  void epochSecondsTimestampMemberUsesEpochHelper() {
    Model model =
        Model.assembler()
            .addUnparsedModel("timestamp_epoch_seconds.smithy", EPOCH_SECONDS_MODEL)
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest =
        runErlangCodec(model, "smithy.beam.demo.timestamps#EpochTimestampService");
    String codec = manifest.getFileString("epoch_timestamp_service_rest_json_1.erl").orElse("");
    assertThat(codec).contains("encode_timestamp_epoch_seconds(");
  }

  @Test
  void elixirDateTimeTimestampMemberUsesDateTimeHelper() {
    Model model =
        Model.assembler()
            .addUnparsedModel("timestamp_date_time.smithy", DATE_TIME_MODEL)
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest =
        runElixirCodec(model, "smithy.beam.demo.timestamps#DateTimeTimestampService");
    String codec = manifest.getFileString("date_time_timestamp_service_rest_json_1.ex").orElse("");
    assertThat(codec).contains("decode_timestamp_date_time(");
    assertThat(codec).contains("encode_timestamp_date_time(");
  }

  @Test
  void elixirEpochSecondsTimestampMemberUsesEpochHelper() {
    Model model =
        Model.assembler()
            .addUnparsedModel("timestamp_epoch_seconds.smithy", EPOCH_SECONDS_MODEL)
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest =
        runElixirCodec(model, "smithy.beam.demo.timestamps#EpochTimestampService");
    String codec = manifest.getFileString("epoch_timestamp_service_rest_json_1.ex").orElse("");
    assertThat(codec).contains("encode_timestamp_epoch_seconds(");
  }

  private static MockManifest runErlangCodec(Model model, String serviceId) {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", serviceId)
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }

  private static MockManifest runElixirCodec(Model model, String serviceId) {
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", serviceId)
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }
}
