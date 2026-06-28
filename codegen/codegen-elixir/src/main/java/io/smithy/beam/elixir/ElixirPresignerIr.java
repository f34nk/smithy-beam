package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirPresignerIr {
  private static final String CLIENT_CONFIG = "map()";
  private static final String PRESIGN_URL_INPUT =
      CLIENT_CONFIG + ", atom(), RuntimeTypes.HttpRequest.t()";
  private static final String PRESIGN_URL_RESULT = "{:ok, String.t()} | {:error, term()}";

  private ElixirPresignerIr() {}

  static ExModule presignerModule(
      ElixirContext ctx, ServiceShape service, String sigv4ModuleName) {
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String moduleName = ElixirSymbolProvider.toModuleName(layout.presignerModuleName());
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String sigv4Mod = ElixirSymbolProvider.toModuleName(sigv4ModuleName);
    return ExModule.module(
        moduleName,
        List.of(ExModuledoc.moduledoc("false")),
        List.of(
            ExAliasAttr.alias(runtimeMod, "RuntimeTypes"),
            ExAliasAttr.alias(sigv4Mod, "ServiceSigv4")),
        List.of(presignUrl()));
  }

  static ExFunction presignUrl() {
    return ExFunction.functionWithSpec(
        "def",
        "presign_url",
        ExSpec.functionSpec("presign_url", PRESIGN_URL_INPUT, PRESIGN_URL_RESULT),
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
                    ExVarPattern.var("expires"),
                    ExCall.call(
                        "Map",
                        "get",
                        ExVar.var("config"),
                        ExAtom.atom("presign_expires"),
                        ExInteger.integer(900))),
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
                        ExMapEntry.entry(ExAtom.atom("expires"), ExVar.var("expires")),
                        ExMapEntry.entry(ExAtom.atom("unsigned_payload"), ExVar.var("unsigned")),
                        ExMapEntry.entry(
                            ExAtom.atom("endpoint_host"),
                            ExCall.call(
                                "ServiceSigv4",
                                "endpoint_host_from_config",
                                ExVar.var("config"))))),
                ExCall.call(
                    "ServiceSigv4",
                    "presign",
                    ExVar.var("request"),
                    ExVar.var("credentials"),
                    ExVar.var("region"),
                    ExVar.var("service"),
                    ExVar.var("opts")))));
  }
}
