@spec decode_get_name_response(map()) ::
        {:ok, HttpServiceTypes.GetNameOutput.t()} | {:error, term()}
def decode_get_name_response(%{:status => 200, :headers => headers, :body => body}) do
  parsed = case parse_xml_root(body, "GetNameOutput") do
    {:ok, root} -> root
    {:error, _} -> :nil
  end
  name = case parsed do
    nil -> :nil
    _ -> xml_child_text(parsed, "Name")
  end
  {:ok, %Types.GetNameOutput{name: name}}
end
def decode_get_name_response(%{:status => status, :body => body}) do
  {:error, {:unknown_error, status, body}}
end
