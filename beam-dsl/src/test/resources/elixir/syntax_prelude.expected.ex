decoded =
  case body do
    "" ->
      %{}

    _ ->
      case Jason.decode(body) do
        {:ok, val} -> val
        {:error, _} -> %{}
      end
  end
