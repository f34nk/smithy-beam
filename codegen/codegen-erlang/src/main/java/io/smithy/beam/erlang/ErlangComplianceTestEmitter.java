package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamComplianceHelperNeeds;
import io.smithy.beam.core.BeamComplianceLiterals;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpComplianceTests;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamSettings;
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

  public static void emit(ErlangContext ctx, ServiceShape service, BeamCodegenKind kind) {
    if (kind != BeamCodegenKind.CLIENT && kind != BeamCodegenKind.SERVER) {
      return;
    }
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
    if (!hasApplicableCases(requestBindings, responseBindings, protocol, kind)) {
      return;
    }

    boolean emitClient = kind == BeamCodegenKind.CLIENT;
    boolean emitServer = kind == BeamCodegenKind.SERVER;

    BeamErlangLayout layout =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service);
    String clientCodecMod = layout.clientCodecModuleName(protocol);
    String serverCodecMod = layout.serverCodecModuleName(protocol);
    SymbolProvider sp = ctx.symbolProvider();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
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

                if (emitClient) {
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
                }
                if (emitServer) {
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
                        helperNeeds);
                  }
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

                if (emitClient) {
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
                }
                if (emitServer) {
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
              }

              emitAssertionHelpers(writer, helperNeeds);
            });
  }

  private static boolean hasApplicableCases(
      List<BeamHttpComplianceTests.OperationRequestTests> requestBindings,
      List<BeamHttpComplianceTests.OperationResponseTests> responseBindings,
      ShapeId protocol,
      BeamCodegenKind kind) {
    boolean emitClient = kind == BeamCodegenKind.CLIENT;
    boolean emitServer = kind == BeamCodegenKind.SERVER;
    for (BeamHttpComplianceTests.OperationRequestTests binding : requestBindings) {
      if (emitClient
          && !BeamHttpComplianceTests.clientRequestTests(binding.cases(), protocol).isEmpty()) {
        return true;
      }
      if (emitServer
          && !BeamHttpComplianceTests.serverRequestTests(binding.cases(), protocol).isEmpty()) {
        return true;
      }
    }
    for (BeamHttpComplianceTests.OperationResponseTests binding : responseBindings) {
      if (emitClient
          && !BeamHttpComplianceTests.clientResponseTests(binding.cases(), protocol).isEmpty()) {
        return true;
      }
      if (emitServer
          && !BeamHttpComplianceTests.serverResponseTests(binding.cases(), protocol).isEmpty()) {
        return true;
      }
    }
    return false;
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
    String fn = testFunctionName(testCase.id() + "_client");
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
    if (!testCase.forbidHeaders().isEmpty()) {
      helperNeeds.needAssertForbidHeaders();
      writer.write(
          "assert_forbid_headers($L, Request#http_request.headers),",
          BeamComplianceLiterals.erlangQueryParamsList(testCase.forbidHeaders()));
    }
    if (!testCase.requireHeaders().isEmpty()) {
      helperNeeds.needAssertRequireHeaders();
      writer.write(
          "assert_require_headers($L, Request#http_request.headers),",
          BeamComplianceLiterals.erlangQueryParamsList(testCase.requireHeaders()));
    }
    if (!testCase.forbidQueryParams().isEmpty()) {
      helperNeeds.needAssertForbidQueryParams();
      writer.write(
          "assert_forbid_query_params($L, Request#http_request.query),",
          BeamComplianceLiterals.erlangQueryParamsList(testCase.forbidQueryParams()));
    }
    if (!testCase.requireQueryParams().isEmpty()) {
      helperNeeds.needAssertRequireQueryParams();
      writer.write(
          "assert_require_query_params($L, Request#http_request.query),",
          BeamComplianceLiterals.erlangQueryParamsList(testCase.requireQueryParams()));
    }
    emitHostAssert(writer, testCase);
    if (testCase.body() != null) {
      emitBodyAssert(
          writer,
          testCase.body(),
          testCase.bodyMediaType(),
          "Request#http_request.body",
          helperNeeds);
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
      BeamComplianceHelperNeeds helperNeeds) {
    String fn = testFunctionName(testCase.id() + "_server");
    String decodeCall;
    if (labels.isEmpty()) {
      decodeCall = codecMod + ":decode_" + opSym.getName() + "_request(Request)";
    } else {
      String labelMap = BeamComplianceLiterals.erlangHttpLabelMap(labels, testCase.params());
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
    String fn = testFunctionName(testCase.id() + "_client");

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
    if (!testCase.forbidHeaders().isEmpty()) {
      helperNeeds.needAssertForbidHeaders();
      writer.write(
          "assert_forbid_headers($L, Response#http_response.headers),",
          BeamComplianceLiterals.erlangQueryParamsList(testCase.forbidHeaders()));
    }
    if (!testCase.requireHeaders().isEmpty()) {
      helperNeeds.needAssertRequireHeaders();
      writer.write(
          "assert_require_headers($L, Response#http_response.headers),",
          BeamComplianceLiterals.erlangQueryParamsList(testCase.requireHeaders()));
    }
    if (testCase.body() != null) {
      emitBodyAssert(
          writer,
          testCase.body(),
          testCase.bodyMediaType(),
          "Response#http_response.body",
          helperNeeds);
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

  private static void emitBodyAssert(
      ErlangWriter writer,
      String body,
      java.util.Optional<String> bodyMediaType,
      String bodyExpr,
      BeamComplianceHelperNeeds helperNeeds) {
    if (BeamComplianceLiterals.bodyCompareMode(bodyMediaType)
        == BeamComplianceLiterals.BodyCompareMode.JSON) {
      helperNeeds.needAssertJsonBody();
      writer.write("assert_json_body(<<\"$L\">>, $L),", escapeErlang(body), bodyExpr);
      return;
    }
    writer.write(
        "?assertEqual(<<\"$L\">>, iolist_to_binary($L)),", escapeErlang(body), bodyExpr);
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
      writer.write("");
    }
    if (helperNeeds.assertForbidHeaders()) {
      writer.write("assert_forbid_headers(Forbidden, Headers) ->");
      writer.indent();
      writer.write("lists:foreach(fun(Name) ->");
      writer.indent();
      writer.write("?assertEqual(undefined, proplists:get_value(Name, Headers))");
      writer.dedent();
      writer.write("end, Forbidden).");
      writer.dedent();
      writer.write("");
    }
    if (helperNeeds.assertRequireHeaders()) {
      writer.write("assert_require_headers(Required, Headers) ->");
      writer.indent();
      writer.write("lists:foreach(fun(Name) ->");
      writer.indent();
      writer.write("?assertNotEqual(undefined, proplists:get_value(Name, Headers))");
      writer.dedent();
      writer.write("end, Required).");
      writer.dedent();
      writer.write("");
    }
    if (helperNeeds.assertForbidQueryParams()) {
      writer.write("assert_forbid_query_params(Forbidden, Query) ->");
      writer.indent();
      writer.write("lists:foreach(fun(Name) ->");
      writer.indent();
      writer.write("?assertEqual(false, maps:is_key(Name, Query))");
      writer.dedent();
      writer.write("end, Forbidden).");
      writer.dedent();
      writer.write("");
    }
    if (helperNeeds.assertRequireQueryParams()) {
      writer.write("assert_require_query_params(Required, Query) ->");
      writer.indent();
      writer.write("lists:foreach(fun(Name) ->");
      writer.indent();
      writer.write("?assertEqual(true, maps:is_key(Name, Query))");
      writer.dedent();
      writer.write("end, Required).");
      writer.dedent();
      writer.write("");
    }
    if (helperNeeds.assertJsonBody()) {
      writer.write("assert_json_body(Expected, Actual) ->");
      writer.indent();
      writer.write(
          "?assertEqual(jsone:decode(Expected), jsone:decode(iolist_to_binary(Actual))).");
      writer.dedent();
    }
  }

  private static void emitHostAssert(
      ErlangWriter writer, BeamHttpComplianceTests.HttpRequestTestCase testCase) {
    String expectedHost = testCase.resolvedHost().orElse(testCase.host().orElse(null));
    if (expectedHost == null) {
      return;
    }
    writer.write(
        "?assertEqual(<<\"$L\">>, Request#http_request.host),", escapeErlang(expectedHost));
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
