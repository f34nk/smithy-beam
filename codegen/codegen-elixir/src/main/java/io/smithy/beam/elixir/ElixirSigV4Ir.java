package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExConsPattern;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExIf;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExListPattern;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExNil;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExPipeline;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStringPattern;
import io.smithy.beam.ir.elixir.ExStructAccess;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExStructUpdate;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirSigV4Ir {
  private static final String CLIENT_CONFIG = "map()";
  private static final String HTTP_REQUEST = "RuntimeTypes.HttpRequest.t()";
  private static final String SIGN_INPUT = CLIENT_CONFIG + ", atom(), " + HTTP_REQUEST;
  private static final String SIGN_REQUEST_INPUT =
      HTTP_REQUEST + ", map(), String.t(), String.t(), map()";
  private static final String PRESIGN_RESULT = "{:ok, String.t()} | {:error, term()}";

  private ElixirSigV4Ir() {}

  static ExModule sigV4Module(ElixirContext ctx, ServiceShape service) {
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String moduleName = ElixirSymbolProvider.toModuleName(layout.sigv4ModuleName());
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    return ExModule.module(
        moduleName,
        List.of(ExModuledoc.moduledoc("false")),
        List.of(
            ExAliasAttr.alias(runtimeMod, "RuntimeTypes"),
            ExAliasAttr.alias("RuntimeTypes.HttpRequest", "HttpRequest")),
        sigV4Functions());
  }

  static ExFunction sign() {
    return ExFunction.functionWithSpec(
        "def",
        "sign",
        ExSpec.functionSpec("sign", SIGN_INPUT, HTTP_REQUEST),
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(
                    ExVarPattern.var("config"),
                    ExVarPattern.var("operation"),
                    ExVarPattern.var("request")),
                ExMatch.match(
                    ExVarPattern.var("credentials"),
                    ExCall.call("Map", "fetch!", ExVar.var("config"), ExAtom.atom("credentials"))),
                ExMatch.match(
                    ExVarPattern.var("region"),
                    ExCall.call(
                        "Map",
                        "get",
                        ExVar.var("config"),
                        ExAtom.atom("region"),
                        ExString.string("us-east-1"))),
                ExMatch.match(
                    ExVarPattern.var("service"),
                    ExCall.call("Map", "fetch!", ExVar.var("config"), ExAtom.atom("signing_name"))),
                ExMatch.match(
                    ExVarPattern.var("unsigned"),
                    ExCall.call(
                        "Map",
                        "get",
                        ExVar.var("config"),
                        ExTuple.tuple(ExAtom.atom("unsigned_payload"), ExVar.var("operation")),
                        ExCapturedBlock.capturedBlock("false"))),
                ExMatch.match(
                    ExVarPattern.var("opts"),
                    ExMap.map(
                        ExMapEntry.entry(ExAtom.atom("unsigned_payload"), ExVar.var("unsigned")),
                        ExMapEntry.entry(
                            ExAtom.atom("endpoint_host"),
                            ExCallLocal.callLocal(
                                "endpoint_host_from_config", ExVar.var("config"))))),
                ExCallLocal.callLocal(
                    "sign_request",
                    ExVar.var("request"),
                    ExVar.var("credentials"),
                    ExVar.var("region"),
                    ExVar.var("service"),
                    ExVar.var("opts")))));
  }

  static ExFunction presign() {
    ExStructPattern requestPattern =
        ExStructPattern.structFunctionHead("request", "HttpRequest", List.of());
    ExPipeline queryOptsPipeline =
        ExPipeline.pipeline(
            "query_opts",
            ExCapturedBlock.capturedBlock("[{:ttl, ttl}, {:uri_encode_path, service != \"s3\"}]"),
            List.of(
                ExCall.call(
                    "Kernel", "++", ExCallLocal.callLocal("body_digest_option", ExVar.var("opts"))),
                ExCall.call(
                    "Kernel",
                    "++",
                    ExCallLocal.callLocal(
                        "session_token_option",
                        ExCall.call(
                            "Map",
                            "get",
                            ExVar.var("credentials"),
                            ExAtom.atom("session_token"))))));

    return ExFunction.functionWithSpec(
        "def",
        "presign",
        ExSpec.functionSpec(
            "presign", "HttpRequest.t(), map(), String.t(), String.t(), map()", PRESIGN_RESULT),
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(
                    requestPattern,
                    ExVarPattern.var("credentials"),
                    ExVarPattern.var("region"),
                    ExVarPattern.var("service"),
                    ExVarPattern.var("opts")),
                ExMatch.match(
                    ExVarPattern.var("access_key_id"),
                    ExCall.call(
                        "Map", "fetch!", ExVar.var("credentials"), ExAtom.atom("access_key_id"))),
                ExMatch.match(
                    ExVarPattern.var("secret_access_key"),
                    ExCall.call(
                        "Map",
                        "fetch!",
                        ExVar.var("credentials"),
                        ExAtom.atom("secret_access_key"))),
                ExMatch.match(
                    ExVarPattern.var("datetime"), ExCall.call(":calendar", "universal_time")),
                ExMatch.match(
                    ExVarPattern.var("host"),
                    ExCallLocal.callLocal("resolve_host", ExVar.var("request"), ExVar.var("opts"))),
                ExMatch.match(
                    ExVarPattern.var("url"),
                    ExCallLocal.callLocal(
                        "build_url",
                        ExVar.var("host"),
                        ExStructAccess.structAccess(ExVar.var("request"), "path"),
                        ExStructAccess.structAccess(ExVar.var("request"), "query"))),
                ExMatch.match(
                    ExVarPattern.var("ttl"),
                    ExCall.call(
                        "Map",
                        "get",
                        ExVar.var("opts"),
                        ExAtom.atom("expires"),
                        ExInteger.integer(900))),
                queryOptsPipeline,
                ExCapturedBlock.capturedBlock(
                    """
                    try do
                      {:ok,
                       :aws_signature.sign_v4_query_params(
                         to_bin(access_key_id),
                         to_bin(secret_access_key),
                         to_bin(region),
                         to_bin(service),
                         datetime,
                         to_bin(request.method),
                         to_bin(url),
                         query_opts
                       )
                       |> to_string()}
                    catch
                      _, reason -> {:error, reason}
                    end"""))));
  }

  static ExFunction signRequest() {
    ExStructPattern requestPattern =
        ExStructPattern.structFunctionHead("request", "HttpRequest", List.of());
    return ExFunction.defpFunction(
        "sign_request",
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(
                    requestPattern,
                    ExVarPattern.var("credentials"),
                    ExVarPattern.var("region"),
                    ExVarPattern.var("service"),
                    ExVarPattern.var("opts")),
                ExMatch.match(
                    ExVarPattern.var("access_key_id"),
                    ExCall.call(
                        "Map", "fetch!", ExVar.var("credentials"), ExAtom.atom("access_key_id"))),
                ExMatch.match(
                    ExVarPattern.var("secret_access_key"),
                    ExCall.call(
                        "Map",
                        "fetch!",
                        ExVar.var("credentials"),
                        ExAtom.atom("secret_access_key"))),
                ExMatch.match(
                    ExVarPattern.var("datetime"), ExCall.call(":calendar", "universal_time")),
                ExMatch.match(
                    ExVarPattern.var("host"),
                    ExCallLocal.callLocal("resolve_host", ExVar.var("request"), ExVar.var("opts"))),
                ExMatch.match(
                    ExVarPattern.var("url"),
                    ExCallLocal.callLocal(
                        "build_url",
                        ExVar.var("host"),
                        ExStructAccess.structAccess(ExVar.var("request"), "path"),
                        ExStructAccess.structAccess(ExVar.var("request"), "query"))),
                ExMatch.match(
                    ExVarPattern.var("headers0"),
                    ExCallLocal.callLocal(
                        "ensure_host_header",
                        ExStructAccess.structAccess(ExVar.var("request"), "headers"),
                        ExVar.var("host"))),
                ExMatch.match(
                    ExVarPattern.var("headers1"),
                    ExCallLocal.callLocal(
                        "maybe_add_session_token",
                        ExVar.var("headers0"),
                        ExCall.call(
                            "Map", "get", ExVar.var("credentials"), ExAtom.atom("session_token")))),
                ExMatch.match(
                    ExVarPattern.var("sign_opts"),
                    ExCall.call(
                        "Kernel",
                        "++",
                        ExList.list(
                            ExTuple.tuple(
                                ExAtom.atom("uri_encode_path"),
                                ExOp.op("!=", ExVar.var("service"), ExString.string("s3")))),
                        ExCallLocal.callLocal("body_digest_option", ExVar.var("opts")))),
                ExMatch.match(
                    ExVarPattern.var("signed_headers"),
                    ExCall.call(
                        ":aws_signature",
                        "sign_v4",
                        ExCallLocal.callLocal("to_bin", ExVar.var("access_key_id")),
                        ExCallLocal.callLocal("to_bin", ExVar.var("secret_access_key")),
                        ExCallLocal.callLocal("to_bin", ExVar.var("region")),
                        ExCallLocal.callLocal("to_bin", ExVar.var("service")),
                        ExVar.var("datetime"),
                        ExCallLocal.callLocal(
                            "to_bin", ExStructAccess.structAccess(ExVar.var("request"), "method")),
                        ExCallLocal.callLocal("to_bin", ExVar.var("url")),
                        ExCallLocal.callLocal("to_erl_headers", ExVar.var("headers1")),
                        ExCallLocal.callLocal(
                            "to_bin", ExStructAccess.structAccess(ExVar.var("request"), "body")),
                        ExVar.var("sign_opts"))),
                ExStructUpdate.structUpdate(
                    ExVar.var("request"),
                    "HttpRequest",
                    ExMapEntry.entry(
                        ExAtom.atom("headers"),
                        ExCallLocal.callLocal("from_erl_headers", ExVar.var("signed_headers")))))));
  }

  static List<ExFunction> helperFunctions() {
    return List.of(
        endpointHostFromConfig(),
        resolveHost(),
        coalesce(),
        buildUrl(),
        encodeQueryParams(),
        encodeQueryParamValue(),
        ensureHostHeader(),
        headerHost(),
        maybeAddSessionToken(),
        bodyDigestOption(),
        sessionTokenOption(),
        splitBaseUrl(),
        toBin(),
        toErlHeaders(),
        fromErlHeaders());
  }

  private static List<ExFunction> sigV4Functions() {
    List<ExFunction> functions = new ArrayList<>();
    functions.add(sign());
    functions.add(presign());
    functions.add(signRequest());
    functions.addAll(helperFunctions());
    return functions;
  }

  private static ExFunction endpointHostFromConfig() {
    return ExFunction.defFunction(
        "endpoint_host_from_config",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config")),
                ExCase.caseExpr(
                    ExCall.call("Map", "get", ExVar.var("config"), ExAtom.atom("base_url")),
                    ExCaseBranch.branch(
                        ExNilPattern.nil(),
                        ExCase.caseExpr(
                            ExTuple.tuple(
                                ExCall.call(
                                    "Map",
                                    "get",
                                    ExVar.var("config"),
                                    ExAtom.atom("endpoint_prefix")),
                                ExCall.call(
                                    "Map",
                                    "get",
                                    ExVar.var("config"),
                                    ExAtom.atom("region"),
                                    ExString.string("us-east-1"))),
                            ExCaseBranch.branch(
                                ExTuplePattern.tuple(ExNilPattern.nil(), ExVarPattern.var("_")),
                                ExNil.nil()),
                            ExCaseBranch.branch(
                                ExTuplePattern.tuple(
                                    ExVarPattern.var("prefix"), ExVarPattern.var("region")),
                                ExCapturedBlock.capturedBlock(
                                    "\"#{prefix}.#{region}.amazonaws.com\"")))),
                    ExCaseBranch.branch(
                        ExVarPattern.var("base_url"),
                        ExCapturedBlock.capturedBlock(
                            """
                            {_scheme, authority} = split_base_url(base_url)
                            authority"""))))));
  }

  private static ExFunction resolveHost() {
    ExStructPattern requestPattern =
        ExStructPattern.structFunctionHead("request", "HttpRequest", List.of());
    return ExFunction.defpFunction(
        "resolve_host",
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(requestPattern, ExVarPattern.var("opts")),
                ExCallLocal.callLocal(
                    "coalesce",
                    ExList.list(
                        ExStructAccess.structAccess(ExVar.var("request"), "host"),
                        ExCall.call("Map", "get", ExVar.var("opts"), ExAtom.atom("host")),
                        ExCall.call("Map", "get", ExVar.var("opts"), ExAtom.atom("endpoint_host")),
                        ExCallLocal.callLocal(
                            "header_host",
                            ExStructAccess.structAccess(ExVar.var("request"), "headers")))))));
  }

  private static ExFunction coalesce() {
    return ExFunction.defpFunction(
        "coalesce",
        List.of(
            ExClause.inlineClause(
                List.of(ExConsPattern.consPattern(ExNilPattern.nil(), ExVarPattern.var("rest"))),
                ExCallLocal.callLocal("coalesce", ExVar.var("rest"))),
            ExClause.inlineClause(
                List.of(
                    ExConsPattern.consPattern(
                        ExStringPattern.string(""), ExVarPattern.var("rest"))),
                ExCallLocal.callLocal("coalesce", ExVar.var("rest"))),
            ExClause.inlineClause(
                List.of(
                    ExConsPattern.consPattern(ExVarPattern.var("value"), ExVarPattern.var("_"))),
                ExVar.var("value")),
            ExClause.inlineClause(List.of(ExListPattern.list()), ExString.string("localhost"))));
  }

  private static ExFunction buildUrl() {
    return ExFunction.defpFunction(
        "build_url",
        List.of(
            ExClause.clause(
                List.of(
                    ExVarPattern.var("host"), ExVarPattern.var("path"), ExVarPattern.var("query")),
                List.of(
                    ExGuard.exprGuard(
                        ExOp.op(
                            "==",
                            ExCallLocal.callLocal("map_size", ExVar.var("query")),
                            ExInteger.integer(0)))),
                ExCapturedBlock.capturedBlock("\"https://#{host}#{path}\"")),
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("host"), ExVarPattern.var("path"), ExVarPattern.var("query")),
                ExMatch.match(
                    ExVarPattern.var("params"),
                    ExCallLocal.callLocal("encode_query_params", ExVar.var("query"))),
                ExCapturedBlock.capturedBlock("\"https://#{host}#{path}?#{params}\""))));
  }

  private static ExFunction encodeQueryParams() {
    return ExFunction.defpFunction(
        "encode_query_params",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("query")),
                ExCapturedBlock.capturedBlock(
                    "query\n"
                        + "|> Map.to_list()\n"
                        + "|> Enum.flat_map(fn\n"
                        + "  {k, v} when is_list(v) ->\n"
                        + "    Enum.map(v, fn item -> {k, encode_query_param_value(item)} end)\n"
                        + "  {k, v} ->\n"
                        + "    [{k, encode_query_param_value(v)}]\n"
                        + "end)\n"
                        + "|> URI.encode_query()"))));
  }

  private static ExFunction encodeQueryParamValue() {
    return ExFunction.defpFunction(
        "encode_query_param_value",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_boolean", ExVar.var("v"))),
                ExCall.call("Atom", "to_string", ExVar.var("v"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_integer", ExVar.var("v"))),
                ExCall.call("Integer", "to_string", ExVar.var("v"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_float", ExVar.var("v"))),
                ExCall.call("Float", "to_string", ExVar.var("v"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_binary", ExVar.var("v"))),
                ExVar.var("v")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_atom", ExVar.var("v"))),
                ExCall.call("Atom", "to_string", ExVar.var("v")))));
  }

  private static ExFunction ensureHostHeader() {
    return ExFunction.defpFunction(
        "ensure_host_header",
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(ExVarPattern.var("headers"), ExVarPattern.var("host")),
                ExCase.caseExpr(
                    ExCallLocal.callLocal("header_host", ExVar.var("headers")),
                    ExCaseBranch.branch(
                        ExNilPattern.nil(),
                        ExList.cons(
                            ExTuple.tuple(ExString.string("host"), ExVar.var("host")),
                            ExVar.var("headers"))),
                    ExCaseBranch.branch(ExVarPattern.var("_"), ExVar.var("headers"))))));
  }

  private static ExFunction headerHost() {
    return ExFunction.defpFunction(
        "header_host",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("headers")),
                ExCall.call(
                    "Enum",
                    "find_value",
                    ExVar.var("headers"),
                    ExAnonymousFn.fn(
                        ExClause.inlineClause(
                            List.of(
                                ExTuplePattern.tuple(
                                    ExStringPattern.string("host"), ExVarPattern.var("value"))),
                            ExVar.var("value")),
                        ExClause.inlineClause(
                            List.of(
                                ExTuplePattern.tuple(
                                    ExStringPattern.string("Host"), ExVarPattern.var("value"))),
                            ExVar.var("value")),
                        ExClause.inlineClause(List.of(ExVarPattern.var("_")), ExNil.nil()))))));
  }

  private static ExFunction maybeAddSessionToken() {
    return ExFunction.defpFunction(
        "maybe_add_session_token",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("headers"), ExNilPattern.nil()), ExVar.var("headers")),
            ExClause.blockClause(
                List.of(ExVarPattern.var("headers"), ExVarPattern.var("token")),
                ExIf.ifBlock(
                    ExCall.call(
                        "Enum",
                        "any?",
                        ExVar.var("headers"),
                        ExAnonymousFn.compactFn(
                            ExClause.inlineClause(
                                List.of(
                                    ExTuplePattern.tuple(
                                        ExVarPattern.var("k"), ExVarPattern.var("_"))),
                                ExOp.op(
                                    "==",
                                    ExCall.call("String", "downcase", ExVar.var("k")),
                                    ExString.string("x-amz-security-token"))))),
                    ExVar.var("headers"),
                    ExList.cons(
                        ExTuple.tuple(ExString.string("x-amz-security-token"), ExVar.var("token")),
                        ExVar.var("headers"))))));
  }

  private static ExFunction bodyDigestOption() {
    return ExFunction.defpFunction(
        "body_digest_option",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("opts")),
                ExIf.ifBlock(
                    ExCall.call(
                        "Map",
                        "get",
                        ExVar.var("opts"),
                        ExAtom.atom("unsigned_payload"),
                        ExCapturedBlock.capturedBlock("false")),
                    ExList.list(
                        ExTuple.tuple(
                            ExAtom.atom("body_digest"), ExString.string("UNSIGNED-PAYLOAD"))),
                    ExList.list()))));
  }

  private static ExFunction sessionTokenOption() {
    return ExFunction.defpFunction(
        "session_token_option",
        List.of(
            ExClause.inlineClause(List.of(ExNilPattern.nil()), ExList.list()),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("token")),
                ExList.list(
                    ExTuple.tuple(
                        ExAtom.atom("session_token"),
                        ExCallLocal.callLocal("to_bin", ExVar.var("token")))))));
  }

  private static ExFunction splitBaseUrl() {
    return ExFunction.defpFunction(
        "split_base_url",
        List.of(
            ExClause.inlineClause(
                List.of(ExStringPattern.string("")),
                ExTuple.tuple(ExString.string(""), ExString.string(""))),
            ExClause.blockClause(
                List.of(ExVarPattern.var("base_url")),
                ExCapturedBlock.capturedBlock(
                    """
                    case URI.parse(base_url) do
                      %URI{scheme: scheme, host: host} = uri when is_binary(host) ->
                        port_suffix =
                          case uri.port do
                            nil -> ""
                            port -> ":#{port}"
                          end

                        {"#{scheme}://", "#{host}#{port_suffix}"}

                      _ ->
                        {"", base_url}
                    end"""))));
  }

  private static ExFunction toBin() {
    return ExFunction.defpFunction(
        "to_bin",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("value")),
                List.of(ExGuard.guard("is_binary", ExVar.var("value"))),
                ExVar.var("value")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("value")),
                List.of(ExGuard.guard("is_atom", ExVar.var("value"))),
                ExCall.call("Atom", "to_string", ExVar.var("value"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("value")),
                ExCall.call("Kernel", "to_string", ExVar.var("value")))));
  }

  private static ExFunction toErlHeaders() {
    return ExFunction.defpFunction(
        "to_erl_headers",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("headers")),
                ExCall.call(
                    "Enum",
                    "map",
                    ExVar.var("headers"),
                    ExAnonymousFn.compactFn(
                        ExClause.inlineClause(
                            List.of(
                                ExTuplePattern.tuple(ExVarPattern.var("k"), ExVarPattern.var("v"))),
                            ExTuple.tuple(
                                ExCallLocal.callLocal("to_bin", ExVar.var("k")),
                                ExCallLocal.callLocal("to_bin", ExVar.var("v")))))))));
  }

  private static ExFunction fromErlHeaders() {
    return ExFunction.defpFunction(
        "from_erl_headers",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("headers")),
                ExCall.call(
                    "Enum",
                    "map",
                    ExVar.var("headers"),
                    ExAnonymousFn.compactFn(
                        ExClause.inlineClause(
                            List.of(
                                ExTuplePattern.tuple(ExVarPattern.var("k"), ExVarPattern.var("v"))),
                            ExTuple.tuple(
                                ExCall.call("Kernel", "to_string", ExVar.var("k")),
                                ExCall.call("Kernel", "to_string", ExVar.var("v")))))))));
  }
}
