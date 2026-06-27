package io.smithy.beam.test.support;

import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.elixir.ElixirContext;
import io.smithy.beam.elixir.ElixirIntegration;
import io.smithy.beam.elixir.ElixirWriter;
import io.smithy.beam.erlang.ErlangContext;
import io.smithy.beam.erlang.ErlangIntegration;
import io.smithy.beam.erlang.ErlangWriter;
import java.util.List;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

public final class SerializeHookRecordingIntegration {

  private SerializeHookRecordingIntegration() {}

  public static final class Erlang implements ErlangIntegration {

    @Override
    public String name() {
      return "serialize-hook-recording-erlang";
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
        ErlangContext codegenContext) {
      return List.of();
    }

    @Override
    public void configure(BeamSettings settings, ObjectNode integrationSettings) {}

    @Override
    public void customizeProtocolSerialize(
        ErlangContext context, OperationShape operation, ErlangWriter writer) {
      writer.pushOperationBodySection();
      writer.write("%% serialize-hook-recording-integration");
      writer.popState();
    }

    @Override
    public void customizeProtocolDeserialize(
        ErlangContext context, OperationShape operation, ErlangWriter writer) {
      writer.pushOperationBodySection();
      writer.write("%% deserialize-hook-recording-integration");
      writer.popState();
    }
  }

  public static final class Elixir implements ElixirIntegration {

    @Override
    public String name() {
      return "serialize-hook-recording-elixir";
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors(
        ElixirContext codegenContext) {
      return List.of();
    }

    @Override
    public void configure(BeamSettings settings, ObjectNode integrationSettings) {}

    @Override
    public void customizeProtocolSerialize(
        ElixirContext context, OperationShape operation, ElixirWriter writer) {
      writer.pushOperationBodySection();
      writer.write("# serialize-hook-recording-integration");
      writer.popState();
    }

    @Override
    public void customizeProtocolDeserialize(
        ElixirContext context, OperationShape operation, ElixirWriter writer) {
      writer.pushOperationBodySection();
      writer.write("# deserialize-hook-recording-integration");
      writer.popState();
    }
  }
}
