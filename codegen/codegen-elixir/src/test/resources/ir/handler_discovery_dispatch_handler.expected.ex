defp dispatch_handler(fun, ctx, input, meta) do
  handlers = :persistent_term.get(@handlers_key, %{})
  case Map.get(handlers, fun) do
    handler when is_function(handler, 3) -> handler.(ctx, input, meta)
    _ -> {:error, :not_implemented}
  end
end
