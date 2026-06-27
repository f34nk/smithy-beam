package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code aws_endpoint_rules.ex} when the model defines {@code @endpointRuleSet}. */
public final class ElixirAwsEndpointRulesEmitter {

  private ElixirAwsEndpointRulesEmitter() {}

  public static void emitIfNeeded(ElixirContext ctx, ServiceShape service) {
    if (!BeamEndpointRuleSetEmitter.hasRuleSet(ctx.model(), service)) {
      return;
    }

    ctx.writerDelegator()
        .useFileWriter(
            "aws_endpoint_rules.ex",
            writer -> {
              for (String line : loadResource("aws_endpoint_rules.ex").split("\n", -1)) {
                writer.write(line);
              }
            });
  }

  private static String loadResource(String name) {
    try (InputStream in = ElixirAwsEndpointRulesEmitter.class.getResourceAsStream("/" + name)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource: " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load resource: " + name, e);
    }
  }
}
