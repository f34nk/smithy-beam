defp content_type_matches(
  headers,
  expected
) do
  case List.keyfind(headers, "Content-Type", 0) do
    {_, ct} when ct == expected -> :ok
    {_, ct} when is_binary(ct) -> if(ct_base(ct) == ct_base(expected), do: :ok, else: {:error, {:invalid_content_type, ct}})
    _ -> {:error, {:invalid_content_type, :nil}}
  end
end