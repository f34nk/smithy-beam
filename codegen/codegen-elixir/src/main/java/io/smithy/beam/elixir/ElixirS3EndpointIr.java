package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.BlockExpr;
import io.beam.dsl.elixir.CaseExpr;
import io.beam.dsl.elixir.Clause;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.InterpolatedExpr;
import io.beam.dsl.elixir.InterpolatedLiteral;
import io.beam.dsl.elixir.InterpolatedStringExpr;
import io.beam.dsl.elixir.LocalCallExpr;
import io.beam.dsl.elixir.MatchExpr;
import io.beam.dsl.elixir.Module;
import io.beam.dsl.elixir.Moduledoc;
import io.beam.dsl.elixir.Pattern;
import io.beam.dsl.elixir.RemoteCallExpr;
import io.beam.dsl.elixir.Spec;
import io.beam.dsl.elixir.StringExpr;
import io.beam.dsl.elixir.StringPattern;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.beam.dsl.elixir.WildcardPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirS3EndpointIr {
  private ElixirS3EndpointIr() {}

  static Module s3EndpointModule(ServiceShape service) {
    List<Function> functions = new ArrayList<>();
    functions.add(regionHost());
    functions.add(resolveBucketUrl());
    functions.addAll(helperFunctions());
    return new Module(
        "S3Endpoint",
        Moduledoc.falseLiteral(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  static Function regionHost() {
    return new Function(
        "region_host",
        false,
        List.of(FunctionHead.of(List.of(VariablePattern.of("config")))),
        new BlockExpr(
            List.of(
                MatchExpr.bind(
                    "base_url",
                    RemoteCallExpr.of(
                        "Map",
                        "get",
                        List.of(
                            Variable.of("config"), AtomExpr.of("base_url"), StringExpr.of("")))),
                MatchExpr.bind(
                    TuplePattern.of(
                        List.of(VariablePattern.of("_scheme"), VariablePattern.of("authority"))),
                    RemoteCallExpr.of("Utils", "split_base_url", List.of(Variable.of("base_url")))),
                Variable.of("authority"))),
        Spec.of("region_host(map()) :: String.t()"),
        null,
        false);
  }

  static Function resolveBucketUrl() {
    return new Function(
        "resolve_bucket_url",
        false,
        List.of(
            FunctionHead.of(
                List.of(
                    VariablePattern.of("config"),
                    VariablePattern.of("bucket"),
                    VariablePattern.of("key")))),
        new BlockExpr(
            List.of(
                MatchExpr.bind(
                    "style",
                    RemoteCallExpr.of(
                        "Map",
                        "get",
                        List.of(
                            Variable.of("config"),
                            AtomExpr.of("s3_addressing_style"),
                            AtomExpr.of("virtual_host")))),
                MatchExpr.bind(
                    "region_host", LocalCallExpr.of("region_host", List.of(Variable.of("config")))),
                MatchExpr.bind(
                    "key_path", LocalCallExpr.of("key_path", List.of(Variable.of("key")))),
                new CaseExpr(
                    Variable.of("style"),
                    List.of(
                        Clause.of(AtomPattern.of("virtual_host"), virtualHostBucketUrlBody()),
                        Clause.of(
                            AtomPattern.of("path_style"),
                            TupleExpr.of(List.of(Variable.of("region_host"), pathStyleUrlExpr()))),
                        Clause.of(WildcardPattern.of(), virtualHostBucketUrlBody()))))),
        Spec.of("resolve_bucket_url(map(), String.t(), String.t()) :: {String.t(), String.t()}"),
        null,
        false);
  }

  static List<Function> helperFunctions() {
    List<Function> functions = new ArrayList<>();
    functions.addAll(keyPathFunctions());
    functions.add(virtualHost());
    functions.add(s3HostSuffix());
    return functions;
  }

  private static List<Function> keyPathFunctions() {
    return List.of(
        defp("key_path", List.of(StringPattern.of("")), StringExpr.of(""), true),
        defp(
            "key_path",
            List.of(VariablePattern.of("key")),
            new InterpolatedStringExpr(
                List.of(new InterpolatedLiteral("/"), new InterpolatedExpr(Variable.of("key")))),
            true));
  }

  private static Expression virtualHostBucketUrlBody() {
    return MatchExpr.bind(
        "host",
        LocalCallExpr.of(
            "virtual_host",
            List.of(Variable.of("config"), Variable.of("bucket"), Variable.of("region_host"))),
        TupleExpr.of(List.of(Variable.of("host"), Variable.of("key_path"))));
  }

  private static Expression pathStyleUrlExpr() {
    return new InterpolatedStringExpr(
        List.of(
            new InterpolatedLiteral("/"),
            new InterpolatedExpr(Variable.of("bucket")),
            new InterpolatedExpr(Variable.of("key_path"))));
  }

  private static Function virtualHost() {
    return new Function(
        "virtual_host",
        true,
        List.of(
            FunctionHead.of(
                List.of(
                    VariablePattern.of("config"),
                    VariablePattern.of("bucket"),
                    VariablePattern.of("region_host")))),
        new CaseExpr(
            RemoteCallExpr.of(
                "Map",
                "get",
                List.of(
                    Variable.of("config"), AtomExpr.of("s3_use_accelerate"), AtomExpr.of("false"))),
            List.of(
                Clause.of(
                    AtomPattern.of("true"),
                    new InterpolatedStringExpr(
                        List.of(
                            new InterpolatedExpr(Variable.of("bucket")),
                            new InterpolatedLiteral(".s3-accelerate.amazonaws.com")))),
                Clause.of(
                    WildcardPattern.of(),
                    MatchExpr.bind(
                        "suffix",
                        LocalCallExpr.of("s3_host_suffix", List.of(Variable.of("config"))),
                        new InterpolatedStringExpr(
                            List.of(
                                new InterpolatedExpr(Variable.of("bucket")),
                                new InterpolatedExpr(Variable.of("suffix")),
                                new InterpolatedExpr(Variable.of("region_host")))))))),
        null,
        null,
        false);
  }

  private static Function s3HostSuffix() {
    return new Function(
        "s3_host_suffix",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("config")))),
        new CaseExpr(
            RemoteCallExpr.of(
                "Map",
                "get",
                List.of(
                    Variable.of("config"), AtomExpr.of("s3_use_dualstack"), AtomExpr.of("false"))),
            List.of(
                Clause.of(AtomPattern.of("true"), StringExpr.of(".s3.dualstack.")),
                Clause.of(WildcardPattern.of(), StringExpr.of(".s3.")))),
        null,
        null,
        false);
  }

  private static Function defp(
      String name, List<Pattern> params, Expression body, boolean oneLiner) {
    return new Function(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }
}
