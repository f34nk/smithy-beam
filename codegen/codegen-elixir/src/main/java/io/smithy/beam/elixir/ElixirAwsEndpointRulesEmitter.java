package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code aws_endpoint_rules.ex} when the model defines {@code @endpointRuleSet}. */
public final class ElixirAwsEndpointRulesEmitter {

  private ElixirAwsEndpointRulesEmitter() {}

  public static void emitIfNeeded(ElixirContext ctx, ServiceShape service) {
    if (!BeamEndpointRuleSetEmitter.hasRuleSet(ctx.model(), service)) {
      return;
    }

    ExModule module = awsEndpointRulesModule();
    ctx.writerDelegator()
        .useFileWriter("aws_endpoint_rules.ex", writer -> writer.write("$L", module.asString()));
  }

  private static ExModule awsEndpointRulesModule() {
    return ExModule.module(
        "AwsEndpointRules",
        List.of(
            ExModuledoc.moduledoc(
                """
                Temporary stub endpoint rules evaluator emitted by smithy-beam codegen.

                The rule set argument is ignored for now. Endpoint resolution uses a minimal placeholder
                until a full AWS rules engine runtime is available.""")),
        List.of(),
        List.of(evaluate()));
  }

  private static ExFunction evaluate() {
    return ExFunction.functionWithSpec(
        "def",
        "evaluate",
        ExSpec.functionSpec(
            "evaluate",
            "map(), map()",
            "{:ok, %{url: String.t(), headers: map()}} | {:error, term()}"),
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(ExVarPattern.var("_rule_set"), ExVarPattern.var("params")),
                ExCapturedBlock.capturedBlock(
                    """
                    region = Map.get(params, "Region") || Map.get(params, :Region)

                    case region do
                      nil -> {:error, "Invalid Configuration: Missing Region"}
                      value -> {:ok, %{url: "https://ec2.#{value}.amazonaws.com", headers: %{}}}
                    end"""))));
  }
}
