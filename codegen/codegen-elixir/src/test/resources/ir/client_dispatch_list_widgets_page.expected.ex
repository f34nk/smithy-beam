req = PaginatedServiceRestJson1.encode_list_widgets_request(input)
case RuntimeHttp.dispatch(config, req) do
  {:ok, resp} ->
    case PaginatedServiceRestJson1.decode_list_widgets_response(resp) do
      {:ok, output} ->
        new_acc = acc ++ output.widgets || []
        case output.next_token do
          nil -> {:ok, new_acc}
          next_token ->
            next_input = %{input | next_token: next_token}
            list_widgets(config, next_input, new_acc)
        end
      {:error, reason} -> {:error, reason}
    end
  {:error, reason} -> {:error, reason}
end
