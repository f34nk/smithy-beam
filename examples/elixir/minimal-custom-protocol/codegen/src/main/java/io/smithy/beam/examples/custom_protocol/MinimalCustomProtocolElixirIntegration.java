package io.smithy.beam.examples.custom_protocol;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.elixir.ElixirContext;
import io.smithy.beam.elixir.ElixirIntegration;
import io.smithy.beam.elixir.ElixirWriter;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CodeInterceptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class MinimalCustomProtocolElixirIntegration implements ElixirIntegration {

    public static final ShapeId NDJSON_PROTOCOL =
            ShapeId.from("smithy.beam.demo.custom_protocol#ndjsonProtocol");

    public static final String CODEC_SUFFIX = "ndjson_protocol";

    @Override
    public String name() {
        return "minimal-custom-protocol-elixir-integration";
    }

    @Override
    public Optional<BeamProtocolCodegen> createProtocolCodegen(
            Model model, ShapeId protocolTraitId) {
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
    public void customize(ElixirContext codegenContext) {
        if (!NDJSON_PROTOCOL.equals(codegenContext.resolvedProtocolTraitId())) {
            return;
        }
        ServiceShape service = codegenContext.service();
        BeamElixirLayout layout =
                new BeamElixirLayout(
                        codegenContext.settings(),
                        service.getId().getNamespace(),
                        service);
        String codecSnakeName =
                layout.clientCodecModuleName(
                        codegenContext.resolvedProtocolTraitId(),
                        codegenContext.integrations());
        String codecFile = codecSnakeName + ".ex";
        String moduleName = toElixirModuleName(codecSnakeName);
        SymbolProvider sp = codegenContext.symbolProvider();
        List<OperationShape> operations = new ArrayList<>();
        for (ShapeId operationId : service.getOperations()) {
            operations.add(
                    codegenContext
                            .model()
                            .expectShape(operationId, OperationShape.class));
        }
        operations.sort(Comparator.comparing(op -> op.getId().getName()));

        codegenContext
                .writerDelegator()
                .useFileWriter(
                        codecFile,
                        writer -> {
                            writer.write("defmodule $L do", moduleName);
                            writer.indent();
                            writer.write(
                                    "@moduledoc \"NDJSON protocol codec for $L.\"",
                                    service.getId());
                            writer.write("");
                            for (OperationShape op : operations) {
                                Symbol opSym = sp.toSymbol(op);
                                writer.write("def encode_$L_request(input) do", opSym.getName());
                                writer.indent();
                                writer.write(
                                        "%{operation: :$L, input: input}",
                                        opSym.getName());
                                writer.dedent();
                                writer.write("end");
                                writer.write("");
                                writer.write(
                                        "def decode_$L_response(_resp) do",
                                        opSym.getName());
                                writer.indent();
                                writer.write("{:ok, nil}");
                                writer.dedent();
                                writer.write("end");
                                writer.write("");
                            }
                            writer.dedent();
                            writer.write("end");
                        });
    }

    @Override
    public List<? extends CodeInterceptor<? extends software.amazon.smithy.utils.CodeSection, ElixirWriter>>
            interceptors(ElixirContext codegenContext) {
        return List.of();
    }

    @Override
    public void configure(BeamSettings settings, ObjectNode integrationSettings) {}

    private static String toElixirModuleName(String snakeName) {
        StringBuilder sb = new StringBuilder();
        for (String part : snakeName.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

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
