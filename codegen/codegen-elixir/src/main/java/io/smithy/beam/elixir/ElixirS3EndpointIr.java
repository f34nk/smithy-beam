package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStringPattern;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirS3EndpointIr {
  private ElixirS3EndpointIr() {}

  static ExModule s3EndpointModule(ServiceShape service) {
    List<ExFunction> functions = new ArrayList<>();
    functions.add(regionHost());
    functions.add(resolveBucketUrl());
    functions.addAll(helperFunctions());
    return ExModule.module(
        "S3Endpoint",
        List.of(ExModuledoc.moduledoc("false")),
        List.of(),
        functions);
  }

  static ExFunction regionHost() {
    return ExFunction.functionWithSpec(
        "def",
        "region_host",
        ExSpec.functionSpec("region_host", "map()", "String.t()"),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config")),
                ExCapturedBlock.capturedBlock(
                    """
                    base_url = Map.get(config, :base_url, "")
                    {_scheme, authority} = split_base_url(base_url)
                    authority"""))));
  }

  static ExFunction resolveBucketUrl() {
    return ExFunction.functionWithSpec(
        "def",
        "resolve_bucket_url",
        ExSpec.functionSpec(
            "resolve_bucket_url", "map(), String.t(), String.t()", "{String.t(), String.t()}"),
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("config"),
                    ExVarPattern.var("bucket"),
                    ExVarPattern.var("key")),
                ExCapturedBlock.capturedBlock(
                    """
                    style = Map.get(config, :s3_addressing_style, :virtual_host)
                    region_host = region_host(config)
                    key_path = key_path(key)
                    case style do
                      :virtual_host ->
                        {virtual_host(config, bucket, region_host), key_path}

                      :path_style ->
                        {region_host, "/#{bucket}#{key_path}"}

                      _ ->
                        {virtual_host(config, bucket, region_host), key_path}
                    end"""))));
  }

  static List<ExFunction> helperFunctions() {
    return List.of(keyPath(), virtualHost(), s3HostSuffix(), splitBaseUrl());
  }

  private static ExFunction keyPath() {
    return ExFunction.defpFunction(
        "key_path",
        List.of(
            ExClause.inlineClause(List.of(ExStringPattern.string("")), ExString.string("")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("key")),
                ExCapturedBlock.capturedBlock("\"/#{key}\""))));
  }

  private static ExFunction virtualHost() {
    return ExFunction.defpFunction(
        "virtual_host",
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(
                    ExVarPattern.var("config"),
                    ExVarPattern.var("bucket"),
                    ExVarPattern.var("region_host")),
                ExCapturedBlock.capturedBlock(
                    """
                    if Map.get(config, :s3_use_accelerate, false) do
                      "#{bucket}.s3-accelerate.amazonaws.com"
                    else
                      suffix = s3_host_suffix(config)
                      "#{bucket}#{suffix}#{region_host}"
                    end"""))));
  }

  private static ExFunction s3HostSuffix() {
    return ExFunction.defpFunction(
        "s3_host_suffix",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config")),
                ExCapturedBlock.capturedBlock(
                    "if Map.get(config, :s3_use_dualstack, false), do: \".s3.dualstack.\", else: \".s3.\""))));
  }

  private static ExFunction splitBaseUrl() {
    return ExFunction.defpFunction(
        "split_base_url",
        List.of(
            ExClause.inlineClause(
                List.of(ExStringPattern.string("")),
                ExCapturedBlock.capturedBlock("{\"\", \"\"}")),
            ExClause.blockClause(
                List.of(ExVarPattern.var("base_url")),
                ExCapturedBlock.capturedBlock(
                    """
                    case URI.parse(base_url) do
                      %URI{scheme: scheme, host: host, port: port} when is_binary(host) ->
                        port_suffix =
                          case port do
                            nil -> ""

                            p -> ":#{p}"
                          end

                        {"#{scheme}://", "#{host}#{port_suffix}"}

                      _ ->
                        {"", base_url}
                    end"""))));
  }
}
