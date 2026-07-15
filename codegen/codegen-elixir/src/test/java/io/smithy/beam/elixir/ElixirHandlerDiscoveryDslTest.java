package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Function;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ElixirHandlerDiscoveryIrTest {
  @Test
  void resolveImplAsStringMatchesGolden() {
    Function fn = ElixirHandlerDiscoveryDsl.resolveImpl("BasicServiceBehaviour");
    ElixirDslTestSupport.assertStructural(fn);
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
    Function fn = ElixirHandlerDiscoveryDsl.initHandlers();
    ElixirDslTestSupport.assertStructural(fn);
    String text = ElixirRenderer.renderFunction(fn);
    assertThat(text).contains("@spec init_handlers() :: :ok | {:error, term()}");
    assertThat(text).contains("def init_handlers do");
    assertThat(text).contains("resolve_impl(@default_impl)");
    assertThat(text).contains(":persistent_term.put(@handlers_key, handlers)");
  }

  @Test
  void dispatchHandlerAsStringMatchesGolden() {
    Function fn = ElixirHandlerDiscoveryDsl.dispatchHandler();
    ElixirDslTestSupport.assertStructural(fn);
    String text = ElixirRenderer.renderFunction(fn);
    assertThat(text).contains("defp dispatch_handler(fun, ctx, input, meta) do");
    assertThat(text).contains(":persistent_term.get(@handlers_key, %{})");
    assertThat(text).contains("handler when is_function(handler, 3) -> handler.(ctx, input, meta)");
    assertThat(text).contains("{:error, :not_implemented}");
  }

  @Test
  void operationDispatchAsStringMatchesGolden() {
    Function fn = ElixirHandlerDiscoveryDsl.operationDispatch("handle_get_name");
    ElixirDslTestSupport.assertStructural(fn);
    String text = ElixirRenderer.renderFunction(fn);
    assertThat(text).contains("def handle_get_name(ctx, input, meta) do");
    assertThat(text).contains("dispatch_handler(:handle_get_name, ctx, input, meta)");
  }
}
