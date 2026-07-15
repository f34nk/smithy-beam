package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Function;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ElixirHandlerDiscoveryIrTest {
  @Test
  void resolveImplAsStringMatchesGolden() {
    Function fn = ElixirHandlerDiscoveryIr.resolveImpl("BasicServiceBehaviour");
    ElixirIrTestSupport.assertStructural(fn);
    String text = ElixirRenderer.renderFunction(fn);
    assertThat(text).contains("defp resolve_impl(impl) do");
    assertThat(text).contains("BasicServiceBehaviour.callbacks()");
    assertThat(text).contains("Enum.reduce");
    assertThat(text).contains("function_exported?(impl, fun, 3)");
    assertThat(text).contains("Map.put(acc, fun, Function.capture(impl, fun, 3))");
    assertThat(text).doesNotContain("when function_exported?");
  }

  @Test
  void initHandlersAsStringMatchesGolden() {
    Function fn = ElixirHandlerDiscoveryIr.initHandlers();
    ElixirIrTestSupport.assertStructural(fn);
    String text = ElixirRenderer.renderFunction(fn);
    assertThat(text).contains("@spec init_handlers() :: :ok | {:error, term()}");
    assertThat(text).contains("def init_handlers do");
    assertThat(text).contains("resolve_impl(@default_impl)");
    assertThat(text).contains(":persistent_term.put(@handlers_key, handlers)");
  }

  @Test
  void dispatchHandlerAsStringMatchesGolden() {
    Function fn = ElixirHandlerDiscoveryIr.dispatchHandler();
    ElixirIrTestSupport.assertStructural(fn);
    String text = ElixirRenderer.renderFunction(fn);
    assertThat(text).contains("defp dispatch_handler(fun, ctx, input, meta) do");
    assertThat(text).contains(":persistent_term.get(@handlers_key, %{})");
    assertThat(text).contains("handler when is_function(handler, 3) -> handler.(ctx, input, meta)");
    assertThat(text).contains("{:error, :not_implemented}");
  }

  @Test
  void operationDispatchAsStringMatchesGolden() {
    Function fn = ElixirHandlerDiscoveryIr.operationDispatch("handle_get_name");
    ElixirIrTestSupport.assertStructural(fn);
    String text = ElixirRenderer.renderFunction(fn);
    assertThat(text).contains("def handle_get_name(ctx, input, meta) do");
    assertThat(text).contains("dispatch_handler(:handle_get_name, ctx, input, meta)");
  }
}
