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
