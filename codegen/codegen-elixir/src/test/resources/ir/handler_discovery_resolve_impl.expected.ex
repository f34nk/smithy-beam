defp resolve_impl(impl) do
  case Code.ensure_loaded(impl) do
    {:module, _} ->
      handlers =
        for {fun, 3} <-
          BasicServiceBehaviour.callbacks(),
          function_exported?(impl, fun, 3),
          into: %{} do
          {fun, Function.capture(impl, fun, 3)}
        end
      {:ok, handlers}
    {:error, _} -> {:error, {:impl_not_loaded, impl}}
  end
end
