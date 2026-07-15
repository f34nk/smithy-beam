package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamComplianceHelperNeeds;
import io.smithy.beam.core.BeamComplianceLiterals;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpComplianceTests;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamSettings;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

/** Emits {@code test/<service>_compliance_test.erl} from HTTP protocol compliance traits. */
public final class ErlangComplianceTestEmitter {

  private ErlangComplianceTestEmitter() {}

  public static void emit(ErlangContext ctx, ServiceShape service) {
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

    BeamErlangLayout layout =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service);
    String clientCodecMod = layout.clientCodecModuleName(protocol);
    String serverCodecMod = layout.serverCodecModuleName(protocol);
    SymbolProvider sp = ctx.symbolProvider();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    boolean encodeWithConfig =
        ErlangRestJsonSupport.serviceHasHostLabelOperations(model, service)
            || ErlangRestXmlSupport.serviceHasHostLabelOperations(model, service);

    BeamComplianceHelperNeeds helperNeeds = new BeamComplianceHelperNeeds();

    ctx.writerDelegator()
        .useFileWriter(
            layout.complianceTestsModuleFile(),
            writer -> {
              writer.write(
                  "%% HTTP protocol compliance tests for $L (generated).", service.getId());
              writer.write("-module($L).", layout.complianceTestsModuleName());
              writer.write("-include_lib(\"eunit/include/eunit.hrl\").");
              writer.write("-include(\"$L\").", layout.typesHeaderFile());
              writer.write("-include(\"$L\").", layout.runtimeTypesHeaderFile());
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
                      helperNeeds);
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
                      helperNeeds);
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
                      helperNeeds);
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
                      helperNeeds);
                }
              }

              emitAssertionHelpers(writer, helperNeeds);
            });
  }

  private static void emitClientRequestTest(
      ErlangWriter writer,
      Model model,
      BeamHttpComplianceTests.HttpRequestTestCase testCase,
      Symbol opSym,
      StructureShape input,
      String codecMod,
      SymbolProvider sp,
      boolean encodeWithConfig,
      BeamComplianceHelperNeeds helperNeeds) {
    String fn = testFunctionName(testCase.id());
    String inputLiteral =
        BeamComplianceLiterals.erlangRecordLiteral(model, input, testCase.params(), sp);
    String encodeCall =
        encodeWithConfig
            ? codecMod
                + ":encode_"
                + opSym.getName()
                + "_request(#{region => <<\"us-east-1\">>}, "
                + inputLiteral
                + ")"
            : codecMod + ":encode_" + opSym.getName() + "_request(" + inputLiteral + ")";

    writer.write("$L() ->", fn);
    writer.indent();
    writer.write("Request = $L,", encodeCall);
    writer.write(
        "?assertEqual(<<\"$L\">>, Request#http_request.method),", escapeErlang(testCase.method()));
    writer.write(
        "?assertEqual(<<\"$L\">>, Request#http_request.path),", escapeErlang(testCase.uri()));
    if (!testCase.queryParams().isEmpty()) {
      helperNeeds.needAssertQueryParams();
      writer.write(
          "assert_query_params($L, Request#http_request.query),",
          BeamComplianceLiterals.erlangQueryParamsList(testCase.queryParams()));
    }
    if (!testCase.headers().isEmpty()) {
      helperNeeds.needAssertHeaders();
      writer.write(
          "assert_headers($L, Request#http_request.headers),",
          BeamComplianceLiterals.erlangHeadersMap(testCase.headers()));
    }
    if (testCase.body() != null) {
      writer.write(
          "?assertEqual(<<\"$L\">>, iolist_to_binary(Request#http_request.body)),",
          escapeErlang(testCase.body()));
    }
    writer.write("ok.");
    writer.dedent();
    writer.write("");
  }

  private static void emitServerRequestTest(
      ErlangWriter writer,
      Model model,
      BeamHttpComplianceTests.HttpRequestTestCase testCase,
      Symbol opSym,
      StructureShape input,
      String codecMod,
      SymbolProvider sp,
      List<HttpBinding> labels,
      BeamHostLabelIndex hostLabelIndex,
      OperationShape operation,
      BeamComplianceHelperNeeds helperNeeds) {
    String fn = testFunctionName(testCase.id() + "_server");
    String decodeCall;
    if (labels.isEmpty()) {
      decodeCall = codecMod + ":decode_" + opSym.getName() + "_request(Request)";
    } else {
      String labelMap = erlangLabelMap(hostLabelIndex, operation, testCase.params());
      decodeCall = codecMod + ":decode_" + opSym.getName() + "_request(Request, " + labelMap + ")";
    }

    writer.write("$L() ->", fn);
    writer.indent();
    writer.write("Request = #http_request{");
    writer.indent();
    writer.write("method = <<\"$L\">>,", escapeErlang(testCase.method()));
    writer.write("path = <<\"$L\">>,", escapeErlang(testCase.uri()));
    writer.write("query = $L,", erlangQueryExpr(testCase, helperNeeds));
    writer.write("headers = $L,", erlangHeadersExpr(testCase.headers(), helperNeeds));
    writer.write("body = $L", BeamComplianceLiterals.erlangOptionalBinary(testCase.body()));
    writer.dedent();
    writer.write("},");
    writer.write("Input = $L,", decodeCall);
    assertParamsOnRecord(writer, model, input, testCase.params(), sp, "Input");
    writer.write("ok.");
    writer.dedent();
    writer.write("");
  }

  private static void emitClientResponseTest(
      ErlangWriter writer,
      Model model,
      BeamHttpComplianceTests.HttpResponseTestCase testCase,
      Symbol opSym,
      StructureShape outputShape,
      String codecMod,
      SymbolProvider sp,
      boolean errorCase,
      BeamComplianceHelperNeeds helperNeeds) {
    String fn = testFunctionName(testCase.id());

    writer.write("$L() ->", fn);
    writer.indent();
    writer.write("Response = #http_response{");
    writer.indent();
    writer.write("status = $L,", testCase.code());
    writer.write("headers = $L,", erlangHeadersExpr(testCase.headers(), helperNeeds));
    writer.write("body = $L", BeamComplianceLiterals.erlangOptionalBinary(testCase.body()));
    writer.dedent();
    writer.write("},");
    if (errorCase) {
      writer.write("{error, Output} = $L:decode_$L_response(Response),", codecMod, opSym.getName());
    } else {
      writer.write("{ok, Output} = $L:decode_$L_response(Response),", codecMod, opSym.getName());
    }
    assertParamsOnRecord(writer, model, outputShape, testCase.params(), sp, "Output");
    writer.write("ok.");
    writer.dedent();
    writer.write("");
  }

  private static void emitServerResponseTest(
      ErlangWriter writer,
      Model model,
      BeamHttpComplianceTests.HttpResponseTestCase testCase,
      Symbol opSym,
      StructureShape outputShape,
      String codecMod,
      SymbolProvider sp,
      boolean errorCase,
      BeamComplianceHelperNeeds helperNeeds) {
    String fn = testFunctionName(testCase.id() + (errorCase ? "_error_server" : "_server"));
    String outputLiteral =
        BeamComplianceLiterals.erlangRecordLiteral(model, outputShape, testCase.params(), sp);
    String encodeFn =
        errorCase
            ? "encode_" + recordName(sp.toSymbol(outputShape)) + "_response"
            : "encode_" + opSym.getName() + "_response";

    writer.write("$L() ->", fn);
    writer.indent();
    writer.write("Response = $L:$L($L),", codecMod, encodeFn, outputLiteral);
    writer.write("?assertEqual($L, Response#http_response.status),", testCase.code());
    if (!testCase.headers().isEmpty()) {
      helperNeeds.needAssertHeaders();
      writer.write(
          "assert_headers($L, Response#http_response.headers),",
          BeamComplianceLiterals.erlangHeadersMap(testCase.headers()));
    }
    if (testCase.body() != null) {
      writer.write(
          "?assertEqual(<<\"$L\">>, iolist_to_binary(Response#http_response.body)),",
          escapeErlang(testCase.body()));
    }
    writer.write("ok.");
    writer.dedent();
    writer.write("");
  }

  private static String erlangHeadersExpr(
      java.util.Map<String, String> headers, BeamComplianceHelperNeeds helperNeeds) {
    if (headers.isEmpty()) {
      return "[]";
    }
    helperNeeds.needHeadersConverter();
    return "headers_to_proplist(" + BeamComplianceLiterals.erlangHeadersMap(headers) + ")";
  }

  private static String erlangQueryExpr(
      BeamHttpComplianceTests.HttpRequestTestCase testCase, BeamComplianceHelperNeeds helperNeeds) {
    if (testCase.queryParams().isEmpty()) {
      return "#{}";
    }
    helperNeeds.needQueryParamsConverter();
    return "query_params_to_map("
        + BeamComplianceLiterals.erlangQueryParamsList(testCase.queryParams())
        + ")";
  }

  private static void assertParamsOnRecord(
      ErlangWriter writer,
      Model model,
      StructureShape shape,
      software.amazon.smithy.model.node.ObjectNode params,
      SymbolProvider sp,
      String recordVar) {
    String record = recordName(sp.toSymbol(shape));
    for (var entry : params.getMembers().entrySet()) {
      String memberName = entry.getKey().getValue();
      shape
          .getMember(memberName)
          .ifPresent(
              member -> {
                String fieldName = BeamNameUtils.toSnakeCase(memberName);
                String expected =
                    BeamComplianceLiterals.erlangMemberValue(model, member, entry.getValue(), sp);
                writer.write("?assertEqual($L, $L#$L.$L),", expected, recordVar, record, fieldName);
              });
    }
  }

  private static String erlangLabelMap(
      BeamHostLabelIndex hostLabelIndex,
      OperationShape operation,
      software.amazon.smithy.model.node.ObjectNode params) {
    List<String> entries = new ArrayList<>();
    for (var member : hostLabelIndex.hostLabelMembers(operation)) {
      String memberName = member.getMemberName();
      if (params.getMember(memberName).isPresent()) {
        String field = BeamNameUtils.toSnakeCase(memberName);
        String value = BeamComplianceLiterals.erlangNodeValue(params.expectMember(memberName));
        entries.add(field + " => " + value);
      }
    }
    if (entries.isEmpty()) {
      return "#{}";
    }
    return "#{" + String.join(", ", entries) + "}";
  }

  private static void emitAssertionHelpers(
      ErlangWriter writer, BeamComplianceHelperNeeds helperNeeds) {
    if (!helperNeeds.any()) {
      return;
    }
    if (helperNeeds.headersConverter()) {
      writer.write("headers_to_proplist(Headers) ->");
      writer.indent();
      writer.write("[{K, V} || {K, V} <- maps:to_list(Headers)].");
      writer.dedent();
      writer.write("");
    }
    if (helperNeeds.queryParamsConverter()) {
      writer.write("query_params_to_map([]) -> #{};");
      writer.write("query_params_to_map([Param | Rest]) ->");
      writer.indent();
      writer.write("maps:merge(query_param(Param), query_params_to_map(Rest)).");
      writer.dedent();
      writer.write("");
      writer.write("query_param(Param) ->");
      writer.indent();
      writer.write("case binary:split(Param, <<\"=\">>) of");
      writer.indent();
      writer.write("[Key, Value] -> #{Key => Value};");
      writer.write("[Key] -> #{Key => <<>>}");
      writer.dedent();
      writer.write("end.");
      writer.dedent();
      writer.write("");
    }
    if (helperNeeds.assertHeaders()) {
      writer.write("assert_headers(Expected, Actual) ->");
      writer.indent();
      writer.write("maps:foreach(fun(K, V) ->");
      writer.indent();
      writer.write("?assertEqual(V, proplists:get_value(K, Actual))");
      writer.dedent();
      writer.write("end, Expected).");
      writer.dedent();
      writer.write("");
    }
    if (helperNeeds.assertQueryParams()) {
      writer.write("assert_query_params(Expected, Query) ->");
      writer.indent();
      writer.write("lists:foreach(fun(Param) ->");
      writer.indent();
      writer.write("case binary:split(Param, <<\"=\">>) of");
      writer.indent();
      writer.write("[Key, Value] -> ?assertEqual(Value, maps:get(Key, Query));");
      writer.write("[Key] -> ?assertEqual(true, maps:is_key(Key, Query))");
      writer.dedent();
      writer.write("end");
      writer.dedent();
      writer.write("end, Expected).");
      writer.dedent();
    }
  }

  private static String testFunctionName(String id) {
    return BeamNameUtils.toSnakeCase(id) + "_test";
  }

  private static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }

  private static String escapeErlang(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
