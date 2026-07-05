package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BinarySegmentExpr;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.MapEntry;
import io.beam.ir.erlang.MapExpr;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.OpaqueExpr;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.StringExpr;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code aws_endpoint_rules.erl} when the model defines {@code @endpointRuleSet}. */
public final class ErlangAwsEndpointRulesEmitter {

  private ErlangAwsEndpointRulesEmitter() {}

  public static void emitIfNeeded(ErlangContext ctx, ServiceShape service) {
    if (!BeamEndpointRuleSetEmitter.hasRuleSet(ctx.model(), service)) {
      return;
    }

    ErlangCodecEmission.writeModule(ctx, "aws_endpoint_rules.erl", awsEndpointRulesModule());
  }

  private static Module awsEndpointRulesModule() {
    return Module.of(
        "aws_endpoint_rules",
        List.of(evaluate()),
        List.of(
            "Temporary stub endpoint rules evaluator emitted by smithy-beam codegen.",
            "The rule set argument is ignored for now. Endpoint resolution uses a minimal placeholder",
            "until a full AWS rules engine runtime is available."),
        null,
        null,
        null,
        List.of("evaluate/2"));
  }

  private static Function evaluate() {
    return Function.of(
        "evaluate",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("_RuleSet"), VariablePattern.of("Params")),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bindValue(
                            "Region",
                            RemoteCallExpr.of(
                                "maps",
                                "get",
                                List.of(
                                    BinaryExpr.of("Region"),
                                    Variable.of("Params"),
                                    RemoteCallExpr.of(
                                        "maps",
                                        "get",
                                        List.of(
                                            OpaqueExpr.of("'Region'"),
                                            Variable.of("Params"),
                                            AtomExpr.of("undefined")))))),
                        CaseExpr.of(
                            Variable.of("Region"),
                            List.of(
                                Clause.of(
                                    AtomPattern.of("undefined"),
                                    TupleExpr.of(
                                        List.of(
                                            AtomExpr.of("error"),
                                            StringExpr.of(
                                                "Invalid Configuration: Missing Region")))),
                                Clause.of(
                                    VariablePattern.of("Value"),
                                    TupleExpr.of(
                                        List.of(
                                            AtomExpr.of("ok"),
                                            MapExpr.of(
                                                List.of(
                                                    MapEntry.of(
                                                        AtomExpr.of("url"),
                                                        BinaryExpr.of(
                                                            List.of(
                                                                BinarySegmentExpr.literal(
                                                                    "https://ec2."),
                                                                BinarySegmentExpr.of(
                                                                    Variable.of("Value"),
                                                                    "binary"),
                                                                BinarySegmentExpr.literal(
                                                                    ".amazonaws.com")))),
                                                    MapEntry.of(
                                                        AtomExpr.of("headers"),
                                                        MapExpr.of(List.of())))))))))), false))),
        Spec.of(
            "evaluate(map(), map()) -> {ok, #{url := binary(), headers := map()}} | {error, term()}"),
        null,
        null);
  }
}
