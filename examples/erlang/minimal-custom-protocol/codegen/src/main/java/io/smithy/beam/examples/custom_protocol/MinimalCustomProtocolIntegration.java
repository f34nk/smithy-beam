package io.smithy.beam.examples.custom_protocol;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.erlang.ErlangContext;
import io.smithy.beam.erlang.ErlangIntegration;
import io.smithy.beam.erlang.ErlangWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CodeInterceptor;

public final class MinimalCustomProtocolIntegration implements ErlangIntegration {

  public static final ShapeId NDJSON_PROTOCOL =
      ShapeId.from("smithy.beam.demo.custom_protocol#ndjsonProtocol");

  public static final String CODEC_SUFFIX = "ndjson_protocol";

  @Override
  public String name() {
    return "minimal-custom-protocol-integration";
  }

  @Override
  public Optional<BeamProtocolCodegen> createProtocolCodegen(Model model, ShapeId protocolTraitId) {
    if (NDJSON_PROTOCOL.equals(protocolTraitId)) {
      return Optional.of(new NdjsonProtocolCodegen(BeamHttpBindings.from(model)));
    }
    return Optional.empty();
  }

  @Override
  public Optional<String> codecModuleSuffix(ShapeId protocolTraitId) {
    if (NDJSON_PROTOCOL.equals(protocolTraitId)) {
      return Optional.of(CODEC_SUFFIX);
    }
    return Optional.empty();
  }

  @Override
  public void customize(ErlangContext codegenContext) {
    if (!NDJSON_PROTOCOL.equals(codegenContext.resolvedProtocolTraitId())) {
      return;
    }
    ServiceShape service = codegenContext.service();
    BeamErlangLayout layout =
        new BeamErlangLayout(codegenContext.settings(), service.getId().getNamespace(), service);
    String codecFile =
        layout.clientCodecModuleName(
                codegenContext.resolvedProtocolTraitId(), codegenContext.integrations())
            + ".erl";
    String codecModule =
        layout.clientCodecModuleName(
            codegenContext.resolvedProtocolTraitId(), codegenContext.integrations());
    SymbolProvider sp = codegenContext.symbolProvider();
    List<OperationShape> operations = new ArrayList<>();
    for (ShapeId operationId : service.getOperations()) {
      operations.add(codegenContext.model().expectShape(operationId, OperationShape.class));
    }
    operations.sort(Comparator.comparing(op -> op.getId().getName()));
    List<String> exports = new ArrayList<>();
    for (OperationShape op : operations) {
      String name = sp.toSymbol(op).getName();
      exports.add("encode_" + name + "_request/1");
      exports.add("decode_" + name + "_response/1");
    }

    codegenContext
        .writerDelegator()
        .useFileWriter(
            codecFile,
            writer -> {
              writer.write("%% NDJSON protocol codec for $L.", service.getId());
              writer.write("-module($L).", codecModule);
              writer.write("-export([$L]).", String.join(", ", exports));
              for (OperationShape op : operations) {
                Symbol opSym = sp.toSymbol(op);
                writer.write(
                    "encode_$L_request(Input) -> #{operation => $L, input => Input}.",
                    opSym.getName(),
                    opSym.getName());
                writer.write("decode_$L_response(_Resp) -> {ok, undefined}.", opSym.getName());
              }
            });
  }

  @Override
  public List<
          ? extends
              CodeInterceptor<? extends software.amazon.smithy.utils.CodeSection, ErlangWriter>>
      interceptors(ErlangContext codegenContext) {
    return List.of();
  }

  @Override
  public void configure(BeamSettings settings, ObjectNode integrationSettings) {}

  private static final class NdjsonProtocolCodegen implements BeamProtocolCodegen {

    private final BeamHttpBindings httpBindings;

    private NdjsonProtocolCodegen(BeamHttpBindings httpBindings) {
      this.httpBindings = httpBindings;
    }

    @Override
    public ShapeId protocolTraitId() {
      return NDJSON_PROTOCOL;
    }

    @Override
    public void emitOperationBindings(
        CodegenContext<?, ?, ?> ctx, ServiceShape service, OperationShape operation) {
      httpBindings.requestBindings(operation);
      httpBindings.responseBindings(operation);
    }
  }
}
