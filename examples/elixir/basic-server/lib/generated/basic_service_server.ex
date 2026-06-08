defmodule BasicServiceServer do
    @moduledoc """
        Generated Elixir server dispatcher for smithy.beam.demo.basic#BasicService.

        Discovers impl callbacks at startup via init_handlers/0.
    """
    @behaviour BasicServiceBehaviour
    alias BasicServiceTypes
    alias BasicServiceBehaviour
    # Handler discovery and dispatch helpers.

    def handle_get_type_closure(ctx, input, meta) do
        dispatch_handler(:handle_get_type_closure, ctx, input, meta)
    end

    def handle_list_basic_items(ctx, input, meta) do
        dispatch_handler(:handle_list_basic_items, ctx, input, meta)
    end

    @default_impl BasicServiceImpl
    @handlers_key {BasicServiceServer, :handlers}

    defp resolve_impl(impl) do
        case Code.ensure_loaded(impl) do
            {:module, _} ->
                handlers =
                    for {fun, 3} <- BasicServiceBehaviour.callbacks(),
                        function_exported?(impl, fun, 3),
                        into: %{} do
                        {fun, Function.capture(impl, fun, 3)}
                    end

                {:ok, handlers}

            {:error, _} ->
                {:error, {:impl_not_loaded, impl}}
            end
        end

        @spec init_handlers() :: :ok | {:error, term()}
        def init_handlers do
            case resolve_impl(@default_impl) do
                {:ok, handlers} ->
                    :persistent_term.put(@handlers_key, handlers)
                    :ok

                {:error, reason} ->
                    :persistent_term.put(@handlers_key, %{})
                    {:error, reason}
                end
            end

            defp dispatch_handler(fun, ctx, input, meta) do
                handlers = :persistent_term.get(@handlers_key, %{})

                case Map.get(handlers, fun) do
                    handler when is_function(handler, 3) ->
                        handler.(ctx, input, meta)

                    _ ->
                        {:error, :not_implemented}
                    end
                end

    # Call BasicServiceServer.init_handlers/0 during application start before dispatch.
    # Default impl module: BasicServiceImpl.

end
