package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.core.BeamSigV4Metadata;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExNestedModule;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExStructFieldPattern;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirHttpDispatchIr {
  private static final String RUNTIME_TYPES = "RuntimeTypes";
  private static final ExStructPattern HTTP_REQUEST_PATTERN =
      ExStructPattern.structFunctionHead("req", RUNTIME_TYPES + ".HttpRequest", List.of());

  private ElixirHttpDispatchIr() {}

  static ExModule httpDispatchModule(ElixirContext ctx, ServiceShape service) {
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String httpModule = ElixirSymbolProvider.toModuleName(layout.runtimeHttpModuleName());
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String helpersModule = ElixirSymbolProvider.toModuleName(layout.runtimeHelpersModuleName());
    boolean sigv4 = BeamSigV4Metadata.from(service).isPresent();
    boolean endpointRules = BeamEndpointRuleSetEmitter.hasRuleSet(ctx.model(), service);
    String endpointsModule = ElixirSymbolProvider.toModuleName(layout.endpointsModuleName());
    String credentialsModule = ElixirSymbolProvider.toModuleName(layout.credentialsModuleName());
    String configVar = sigv4 ? "config1" : "config";

    List<ExFunction> functions = new ArrayList<>();
    functions.add(dispatchArity2());
    functions.add(dispatchArity3());
    functions.add(
        dispatchSigned(sigv4, endpointRules, configVar, endpointsModule, credentialsModule));
    functions.add(ElixirHostLabelIr.splitBaseUrl());

    return ExModule.module(
        httpModule,
        List.of(
            ExModuledoc.moduledoc(
                "Generated HTTP dispatcher for Smithy service clients. Uses Req.")),
        List.of(
            ExAliasAttr.alias(runtimeMod, RUNTIME_TYPES),
            ExAliasAttr.alias(helpersModule, "RuntimeHelpers")),
        List.of(),
        functions,
        List.of(reqClientModule()));
  }

  static ExFunction dispatchArity2() {
    return ExFunction.functionWithSpec(
        "def",
        "dispatch",
        ExSpec.functionSpec(
            "dispatch",
            "map(), RuntimeTypes.HttpRequest.t()",
            "{:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}"),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config"), ExVarPattern.var("req")),
                ExExprBlock.block(
                    ExMatch.match(
                        ExVarPattern.var("http_client"),
                        ExCall.call(
                            "Map",
                            "get",
                            ExVar.var("config"),
                            ExAtom.atom("http_client"),
                            ExCapturedBlock.capturedBlock("__MODULE__.ReqClient"))),
                    ExCallLocal.callLocal(
                        "dispatch",
                        ExVar.var("http_client"),
                        ExVar.var("config"),
                        ExVar.var("req"))))));
  }

  static ExFunction dispatchArity3() {
    return ExFunction.functionWithSpec(
        "def",
        "dispatch",
        ExSpec.functionSpec(
            "dispatch",
            "module(), map(), RuntimeTypes.HttpRequest.t()",
            "{:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}"),
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("http_client"),
                    ExVarPattern.var("config"),
                    HTTP_REQUEST_PATTERN),
                ExCallLocal.callLocal(
                    "dispatch_signed",
                    ExVar.var("http_client"),
                    ExVar.var("config"),
                    ExVar.var("req")))));
  }

  static ExFunction dispatchSigned(
      boolean sigv4,
      boolean endpointRules,
      String configVar,
      String endpointsModule,
      String credentialsModule) {
    return ExFunction.functionWithSpec(
        "defp",
        "dispatch_signed",
        ExSpec.functionSpec(
            "dispatch_signed",
            "module(), map(), RuntimeTypes.HttpRequest.t()",
            "{:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}"),
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("http_client"),
                    ExVarPattern.var("config"),
                    HTTP_REQUEST_PATTERN),
                ExCapturedBlock.capturedBlock(
                    dispatchSignedBody(
                        sigv4, endpointRules, configVar, endpointsModule, credentialsModule)))));
  }

  static String dispatchSignedBody(
      boolean sigv4,
      boolean endpointRules,
      String configVar,
      String endpointsModule,
      String credentialsModule) {
    StringBuilder sb = new StringBuilder();
    if (sigv4) {
      sb.append("config1 =\n  case Map.get(config, :credentials) do\n");
      sb.append("    nil ->\n      case ")
          .append(credentialsModule)
          .append(".resolve(config) do\n");
      sb.append("        {:ok, creds} -> Map.put(config, :credentials, creds)\n");
      sb.append("        _ -> config\n      end\n\n");
      sb.append("    _ -> config\n  end\n\n");
    }
    sb.append("base_url =\n  case Map.get(").append(configVar).append(", :base_url) do\n");
    sb.append("    nil ->\n      case Map.get(")
        .append(configVar)
        .append(", :endpoint_prefix) do\n");
    sb.append("        nil -> \"\"\n");
    if (endpointRules) {
      sb.append("        _ ->\n          case ")
          .append(endpointsModule)
          .append(".resolve(")
          .append(configVar)
          .append(", %{}) do\n");
      sb.append("            {:ok, %{url: url}} -> url\n");
      sb.append("            _ -> RuntimeHelpers.resolve_base_url(")
          .append(configVar)
          .append(")\n");
      sb.append("          end\n");
    } else {
      sb.append("        _ -> RuntimeHelpers.resolve_base_url(").append(configVar).append(")\n");
    }
    sb.append("      end\n\n");
    sb.append("    url ->\n      url\n");
    sb.append("  end\n\n");
    sb.append("{scheme, default_authority} = split_base_url(base_url)\n\n");
    sb.append(
        "authority =\n  case req.host do\n    nil -> default_authority\n    host -> host\n  end\n\n");
    sb.append("url = scheme <> authority <> req.path\n");
    sb.append("req_opts = [\n");
    sb.append("  method: String.downcase(req.method) |> String.to_atom(),\n");
    sb.append("  url: url,\n");
    sb.append("  params: req.query,\n");
    sb.append("  headers: req.headers,\n");
    sb.append("  body: req.body,\n");
    sb.append("  decode_body: false\n");
    sb.append("]\n");
    sb.append("case http_client.request(req_opts) do\n");
    sb.append("  {:ok, %{status: status, headers: headers, body: body}} ->\n");
    sb.append("    {:ok,\n");
    sb.append("     %RuntimeTypes.HttpResponse{\n");
    sb.append("       status: status,\n");
    sb.append("       headers: Enum.map(headers, fn {k, v} -> {k, v} end),\n");
    sb.append("       body: body\n");
    sb.append("     }}\n\n");
    sb.append("  {:error, reason} ->\n");
    sb.append("    {:error, reason}\n");
    sb.append("end");
    return sb.toString().strip();
  }

  private static ExNestedModule reqClientModule() {
    return ExNestedModule.nestedModule(
        "ReqClient",
        List.of(ExModuledoc.moduledoc("false")),
        List.of(),
        List.of(),
        List.of(
            ExFunction.functionWithSpec(
                "def",
                "request",
                ExSpec.functionSpec("request", "keyword()", "{:ok, map()} | {:error, term()}"),
                List.of(
                    ExClause.blockClause(
                        List.of(ExVarPattern.var("req_opts")),
                        ExCase.caseExpr(
                            ExCall.call("Req", "request", ExVar.var("req_opts")),
                            List.of(
                                ExCaseBranch.branch(
                                    ExTuplePattern.tuple(
                                        ExAtomPattern.atom("ok"),
                                        ExStructPattern.struct(
                                            "Req.Response",
                                            ExStructFieldPattern.fieldPattern(
                                                "status", ExVarPattern.var("status")),
                                            ExStructFieldPattern.fieldPattern(
                                                "headers", ExVarPattern.var("headers")),
                                            ExStructFieldPattern.fieldPattern(
                                                "body", ExVarPattern.var("body")))),
                                    ExTuple.tuple(
                                        ExAtom.atom("ok"),
                                        ExCapturedBlock.capturedBlock(
                                            "%{status: status, headers: headers, body: body}"))),
                                ExCaseBranch.branch(
                                    ExTuplePattern.tuple(
                                        ExAtomPattern.atom("error"), ExVarPattern.var("reason")),
                                    ExTuple.tuple(ExAtom.atom("error"), ExVar.var("reason")))),
                            true))))));
  }
}
