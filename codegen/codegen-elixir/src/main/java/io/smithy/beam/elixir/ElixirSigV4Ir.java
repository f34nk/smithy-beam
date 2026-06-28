package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExPipeline;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStructAccess;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirSigV4Ir {
  private static final String CLIENT_CONFIG = "map()";
  private static final String HTTP_REQUEST = "RuntimeTypes.HttpRequest.t()";
  private static final String SIGN_INPUT = CLIENT_CONFIG + ", atom(), " + HTTP_REQUEST;
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
                    ExCall.call(
                        "Map", "fetch!", ExVar.var("config"), ExAtom.atom("credentials"))),
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
                    ExCall.call(
                        "Map", "fetch!", ExVar.var("config"), ExAtom.atom("signing_name"))),
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
            ExCapturedBlock.capturedBlock(
                "[{:ttl, ttl}, {:uri_encode_path, service != \"s3\"}]"),
            List.of(
                ExCall.call(
                    "Kernel",
                    "++",
                    ExCallLocal.callLocal("body_digest_option", ExVar.var("opts"))),
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
            "presign",
            "HttpRequest.t(), map(), String.t(), String.t(), map()",
            PRESIGN_RESULT),
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
                        "Map",
                        "fetch!",
                        ExVar.var("credentials"),
                        ExAtom.atom("access_key_id"))),
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
                    ExCallLocal.callLocal(
                        "resolve_host", ExVar.var("request"), ExVar.var("opts"))),
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
    throw new UnsupportedOperationException("implemented in step 41b");
  }

  static List<ExFunction> helperFunctions() {
    throw new UnsupportedOperationException("implemented in step 41b");
  }

  private static List<ExFunction> sigV4Functions() {
    List<ExFunction> functions = new ArrayList<>();
    functions.add(sign());
    functions.add(presign());
    return functions;
  }
}
