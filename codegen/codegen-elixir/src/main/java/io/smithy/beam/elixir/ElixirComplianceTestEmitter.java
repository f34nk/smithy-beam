package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamComplianceLiterals;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpComplianceTests;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

/** Emits {@code test/<service>_compliance_tests.ex} from HTTP protocol compliance traits. */
public final class ElixirComplianceTestEmitter {

  private ElixirComplianceTestEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    BeamSettings settings = ctx.settings();
    Model model = ctx.model();
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    if (protocol == null) {
      return;
    }

    List<BeamHttpComplianceTests.OperationRequestTests> requestBindings =
        BeamHttpComplianceTests.requestTestsForService(model, service);
    List<BeamHttpComplianceTests.OperationResponseTests> responseBindings =
        BeamHttpComplianceTests.responseTestsForService(model, service);
    if (requestBindings.isEmpty() && responseBindings.isEmpty()) {
      return;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    String moduleName = ElixirSymbolProvider.toModuleName(layout.complianceTestsModuleName());
    String clientCodecMod =
        ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(protocol));
    String serverCodecMod =
        ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocol));
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    SymbolProvider sp = ctx.symbolProvider();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    boolean encodeWithConfig =
        ElixirRestJsonSupport.serviceHasHostLabelOperations(model, service)
            || ElixirRestXmlSupport.serviceHasHostLabelOperations(model, service);
    Function<StructureShape, String> structNameFn =
        shape -> ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(shape));

    ctx.writerDelegator()
        .useFileWriter(
            layout.complianceTestsModuleFile(),
            writer -> {
              writer.write("defmodule $L do", moduleName);
              writer.indent();
              writer.write("use ExUnit.Case, async: true");
              writer.write("");
              writer.write("alias $L, as: Types", typesMod);
              writer.write("alias $L", clientCodecMod);
              writer.write("alias $L, as: ServerCodec", serverCodecMod);
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("");

              for (BeamHttpComplianceTests.OperationRequestTests binding : requestBindings) {
                OperationShape operation = binding.operation();
                Symbol opSym = sp.toSymbol(operation);
                StructureShape input =
                    model.expectShape(operation.getInputShape(), StructureShape.class);
                List<HttpBinding> labels =
                    httpIndex.getRequestBindings(operation, HttpBinding.Location.LABEL);

                for (BeamHttpComplianceTests.HttpRequestTestCase testCase :
                    BeamHttpComplianceTests.clientRequestTests(binding.cases(), protocol)) {
                  emitClientRequestTest(
                      writer,
                      model,
                      testCase,
                      opSym,
                      input,
                      clientCodecMod,
                      sp,
                      encodeWithConfig,
                      structNameFn);
                }
                for (BeamHttpComplianceTests.HttpRequestTestCase testCase :
                    BeamHttpComplianceTests.serverRequestTests(binding.cases(), protocol)) {
                  emitServerRequestTest(
                      writer,
                      model,
                      testCase,
                      opSym,
                      input,
                      serverCodecMod,
                      sp,
                      labels,
                      hostLabelIndex,
                      operation,
                      structNameFn);
                }
              }

              for (BeamHttpComplianceTests.OperationResponseTests binding : responseBindings) {
                OperationShape operation = binding.operation();
                Symbol opSym = sp.toSymbol(operation);
                StructureShape outputShape =
                    binding
                        .errorShape()
                        .orElseGet(
                            () ->
                                model.expectShape(
                                    operation.getOutputShape(), StructureShape.class));

                for (BeamHttpComplianceTests.HttpResponseTestCase testCase :
                    BeamHttpComplianceTests.clientResponseTests(binding.cases(), protocol)) {
                  emitClientResponseTest(
                      writer,
                      model,
                      testCase,
                      opSym,
                      outputShape,
                      clientCodecMod,
                      sp,
                      binding.errorShape().isPresent(),
                      structNameFn);
                }
                for (BeamHttpComplianceTests.HttpResponseTestCase testCase :
                    BeamHttpComplianceTests.serverResponseTests(binding.cases(), protocol)) {
                  emitServerResponseTest(
                      writer,
                      model,
                      testCase,
                      opSym,
                      outputShape,
                      serverCodecMod,
                      sp,
                      binding.errorShape().isPresent(),
                      structNameFn);
                }
              }

              emitAssertionHelpers(writer);
              writer.dedent();
              writer.write("end");
            });
  }

  private static void emitClientRequestTest(
      ElixirWriter writer,
      Model model,
      BeamHttpComplianceTests.HttpRequestTestCase testCase,
      Symbol opSym,
      StructureShape input,
      String codecMod,
      SymbolProvider sp,
      boolean encodeWithConfig,
      Function<StructureShape, String> structNameFn) {
    String fn = testFunctionName(testCase.id());
    String inputLiteral =
        BeamComplianceLiterals.elixirStructLiteral(
            model, input, testCase.params(), sp, structNameFn);
    String encodeCall =
        encodeWithConfig
            ? codecMod
                + ".encode_"
                + opSym.getName()
                + "_request(%{region: \"us-east-1\"}, "
                + inputLiteral
                + ")"
            : codecMod + ".encode_" + opSym.getName() + "_request(" + inputLiteral + ")";

    writer.write("test \"$L\" do", testCase.id());
    writer.indent();
    writer.write("request = $L", encodeCall);
    writer.write("assert request.method == \"$L\"", escapeElixir(testCase.method()));
    writer.write("assert request.path == \"$L\"", escapeElixir(testCase.uri()));
    if (!testCase.queryParams().isEmpty()) {
      writer.write(
          "assert_query_params($L, request.query)",
          BeamComplianceLiterals.elixirQueryParamsList(testCase.queryParams()));
    }
    if (!testCase.headers().isEmpty()) {
      writer.write(
          "assert_headers($L, request.headers)",
          BeamComplianceLiterals.elixirHeadersMap(testCase.headers()));
    }
    if (testCase.body() != null) {
      writer.write(
          "assert IO.iodata_to_binary(request.body) == \"$L\"", escapeElixir(testCase.body()));
    }
    writer.dedent();
    writer.write("end");
    writer.write("");
  }

  private static void emitServerRequestTest(
      ElixirWriter writer,
      Model model,
      BeamHttpComplianceTests.HttpRequestTestCase testCase,
      Symbol opSym,
      StructureShape input,
      String codecMod,
      SymbolProvider sp,
      List<HttpBinding> labels,
      BeamHostLabelIndex hostLabelIndex,
      OperationShape operation,
      Function<StructureShape, String> structNameFn) {
    String decodeCall;
    if (labels.isEmpty()) {
      decodeCall = codecMod + ".decode_" + opSym.getName() + "_request(request)";
    } else {
      String labelMap = elixirLabelMap(hostLabelIndex, operation, testCase.params());
      decodeCall = codecMod + ".decode_" + opSym.getName() + "_request(request, " + labelMap + ")";
    }

    writer.write("test \"$L server\" do", testCase.id());
    writer.indent();
    writer.write("request = %RuntimeTypes.HttpRequest{");
    writer.indent();
    writer.write("method: \"$L\",", escapeElixir(testCase.method()));
    writer.write("path: \"$L\",", escapeElixir(testCase.uri()));
    writer.write(
        "query: query_params_to_map($L),",
        BeamComplianceLiterals.elixirQueryParamsList(testCase.queryParams()));
    writer.write(
        "headers: headers_to_list($L),",
        BeamComplianceLiterals.elixirHeadersMap(testCase.headers()));
    writer.write("body: $L", BeamComplianceLiterals.elixirOptionalBinary(testCase.body()));
    writer.dedent();
    writer.write("}");
    writer.write("");
    writer.write("input = $L", decodeCall);
    assertParamsOnStruct(writer, model, input, testCase.params(), sp, "input", structNameFn);
    writer.dedent();
    writer.write("end");
    writer.write("");
  }

  private static void emitClientResponseTest(
      ElixirWriter writer,
      Model model,
      BeamHttpComplianceTests.HttpResponseTestCase testCase,
      Symbol opSym,
      StructureShape outputShape,
      String codecMod,
      SymbolProvider sp,
      boolean errorCase,
      Function<StructureShape, String> structNameFn) {
    writer.write("test \"$L\" do", testCase.id());
    writer.indent();
    writer.write("response = %RuntimeTypes.HttpResponse{");
    writer.indent();
    writer.write("status: $L,", testCase.code());
    writer.write(
        "headers: headers_to_list($L),",
        BeamComplianceLiterals.elixirHeadersMap(testCase.headers()));
    writer.write("body: $L", BeamComplianceLiterals.elixirOptionalBinary(testCase.body()));
    writer.dedent();
    writer.write("}");
    writer.write("");
    if (errorCase) {
      writer.write("{:error, output} = $L.decode_$L_response(response)", codecMod, opSym.getName());
    } else {
      writer.write("{:ok, output} = $L.decode_$L_response(response)", codecMod, opSym.getName());
    }
    assertParamsOnStruct(writer, model, outputShape, testCase.params(), sp, "output", structNameFn);
    writer.dedent();
    writer.write("end");
    writer.write("");
  }

  private static void emitServerResponseTest(
      ElixirWriter writer,
      Model model,
      BeamHttpComplianceTests.HttpResponseTestCase testCase,
      Symbol opSym,
      StructureShape outputShape,
      String codecMod,
      SymbolProvider sp,
      boolean errorCase,
      Function<StructureShape, String> structNameFn) {
    String outputLiteral =
        BeamComplianceLiterals.elixirStructLiteral(
            model, outputShape, testCase.params(), sp, structNameFn);
    String encodeFn =
        errorCase
            ? "encode_" + structName(sp.toSymbol(outputShape)) + "_response"
            : "encode_" + opSym.getName() + "_response";

    writer.write("test \"$L server\" do", testCase.id());
    writer.indent();
    writer.write("response = $L.$L($L)", codecMod, encodeFn, outputLiteral);
    writer.write("assert response.status == $L", testCase.code());
    if (!testCase.headers().isEmpty()) {
      writer.write(
          "assert_headers($L, response.headers)",
          BeamComplianceLiterals.elixirHeadersMap(testCase.headers()));
    }
    if (testCase.body() != null) {
      writer.write(
          "assert IO.iodata_to_binary(response.body) == \"$L\"", escapeElixir(testCase.body()));
    }
    writer.dedent();
    writer.write("end");
    writer.write("");
  }

  private static void assertParamsOnStruct(
      ElixirWriter writer,
      Model model,
      StructureShape shape,
      software.amazon.smithy.model.node.ObjectNode params,
      SymbolProvider sp,
      String structVar,
      Function<StructureShape, String> structNameFn) {
    for (var entry : params.getMembers().entrySet()) {
      String memberName = entry.getKey().getValue();
      shape
          .getMember(memberName)
          .ifPresent(
              member -> {
                String fieldName = BeamNameUtils.toSnakeCase(memberName);
                String expected =
                    BeamComplianceLiterals.elixirMemberValue(
                        model, member, entry.getValue(), sp, structNameFn);
                writer.write("assert $L.$L == $L", structVar, fieldName, expected);
              });
    }
  }

  private static String elixirLabelMap(
      BeamHostLabelIndex hostLabelIndex,
      OperationShape operation,
      software.amazon.smithy.model.node.ObjectNode params) {
    List<String> entries = new ArrayList<>();
    for (var member : hostLabelIndex.hostLabelMembers(operation)) {
      String memberName = member.getMemberName();
      if (params.getMember(memberName).isPresent()) {
        String field = BeamNameUtils.toSnakeCase(memberName);
        String value = BeamComplianceLiterals.elixirNodeValue(params.expectMember(memberName));
        entries.add(field + ": " + value);
      }
    }
    if (entries.isEmpty()) {
      return "%{}";
    }
    return "%{" + String.join(", ", entries) + "}";
  }

  private static void emitAssertionHelpers(ElixirWriter writer) {
    writer.write("defp headers_to_list(headers) do");
    writer.indent();
    writer.write("Enum.map(headers, fn {k, v} -> {k, v} end)");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp query_params_to_map([]), do: %{}");
    writer.write("defp query_params_to_map([param | rest]) do");
    writer.indent();
    writer.write("Map.merge(query_param(param), query_params_to_map(rest))");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp query_param(param) do");
    writer.indent();
    writer.write("case String.split(param, \"=\", parts: 2) do");
    writer.indent();
    writer.write("[key, value] -> %{key => value}");
    writer.write("[key] -> %{key => \"\"}");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp assert_headers(expected, actual) do");
    writer.indent();
    writer.write("Enum.each(expected, fn {key, value} ->");
    writer.indent();
    writer.write("assert Keyword.get(actual, key) == value");
    writer.dedent();
    writer.write("end)");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp assert_query_params(expected, query) do");
    writer.indent();
    writer.write("Enum.each(expected, fn param ->");
    writer.indent();
    writer.write("case String.split(param, \"=\", parts: 2) do");
    writer.indent();
    writer.write("[key, value] -> assert Map.fetch!(query, key) == value");
    writer.write("[key] -> assert Map.has_key?(query, key)");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end)");
    writer.dedent();
    writer.write("end");
  }

  private static String testFunctionName(String id) {
    return BeamNameUtils.toSnakeCase(id);
  }

  private static String structName(Symbol symbol) {
    return symbol.getName();
  }

  private static String escapeElixir(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
