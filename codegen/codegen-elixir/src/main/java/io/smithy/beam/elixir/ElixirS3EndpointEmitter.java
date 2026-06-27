package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamS3CustomizationIndex;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code S3Endpoint} bucket virtual-host and path-style helpers for S3 REST-XML clients. */
public final class ElixirS3EndpointEmitter {

  private ElixirS3EndpointEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    if (!BeamS3CustomizationIndex.isS3Service(service)) {
      return;
    }

    ctx.writerDelegator()
        .useFileWriter(
            "s3_endpoint.ex",
            writer -> {
              writer.write("defmodule S3Endpoint do");
              writer.indent();
              writer.write("@moduledoc false");
              writer.write("");
              ElixirFormat.writeSpec(writer, "@spec", "region_host", "map()", "String.t()");
              writer.write("def region_host(config) do");
              writer.indent();
              writer.write("base_url = Map.get(config, :base_url, \"\")");
              writer.write("{_scheme, authority} = split_base_url(base_url)");
              writer.write("authority");
              writer.dedent();
              writer.write("end");
              writer.write("");
              ElixirFormat.writeSpec(
                  writer,
                  "@spec",
                  "resolve_bucket_url",
                  "map(), String.t(), String.t()",
                  "{String.t(), String.t()}");
              writer.write("def resolve_bucket_url(config, bucket, key) do");
              writer.indent();
              writer.write("style = Map.get(config, :s3_addressing_style, :virtual_host)");
              writer.write("region_host = region_host(config)");
              writer.write("key_path = key_path(key)");
              writer.write("case style do");
              writer.indent();
              writer.write(":virtual_host ->");
              writer.indent();
              writer.write("{virtual_host(config, bucket, region_host), key_path}");
              writer.dedent();
              writer.write("");
              writer.write(":path_style ->");
              writer.indent();
              writer.write("{region_host, \"/#{bucket}#{key_path}\"}");
              writer.dedent();
              writer.write("");
              writer.write("_ ->");
              writer.indent();
              writer.write("{virtual_host(config, bucket, region_host), key_path}");
              writer.dedent();
              writer.dedent();
              writer.write("end");
              writer.dedent();
              writer.write("end");
              writer.write("");
              writer.write("defp key_path(\"\"), do: \"\"");
              writer.write("defp key_path(key), do: \"/#{key}\"");
              writer.write("");
              writer.write("defp virtual_host(config, bucket, region_host) do");
              writer.indent();
              writer.write("if Map.get(config, :s3_use_accelerate, false) do");
              writer.indent();
              writer.write("\"#{bucket}.s3-accelerate.amazonaws.com\"");
              writer.dedent();
              writer.write("else");
              writer.indent();
              writer.write("suffix = s3_host_suffix(config)");
              writer.write("\"#{bucket}#{suffix}#{region_host}\"");
              writer.dedent();
              writer.write("end");
              writer.dedent();
              writer.write("end");
              writer.write("");
              writer.write("defp s3_host_suffix(config) do");
              writer.indent();
              writer.write(
                  "if Map.get(config, :s3_use_dualstack, false), do: \".s3.dualstack.\", else: \".s3.\"");
              writer.dedent();
              writer.write("end");
              writer.write("");
              writer.write("defp split_base_url(\"\"), do: {\"\", \"\"}");
              writer.write("defp split_base_url(base_url) do");
              writer.indent();
              writer.write("case URI.parse(base_url) do");
              writer.indent();
              writer.write("%URI{scheme: scheme, host: host, port: port} when is_binary(host) ->");
              writer.indent();
              writer.write("port_suffix =");
              writer.indent();
              writer.write("case port do");
              writer.indent();
              writer.write("nil -> \"\";");
              writer.write("");
              writer.write("p -> \":#{p}\"");
              writer.dedent();
              writer.write("end");
              writer.write("{\"#{scheme}://\", \"#{host}#{port_suffix}\"}");
              writer.dedent();
              writer.write("");
              writer.write("_ ->");
              writer.indent();
              writer.write("{\"\", base_url}");
              writer.dedent();
              writer.dedent();
              writer.write("end");
              writer.dedent();
              writer.dedent();
              writer.write("end");
              writer.dedent();
              writer.write("end");
            });
  }
}
