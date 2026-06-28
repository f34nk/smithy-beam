defp split_base_url(""), do: {"", ""}
defp split_base_url(base_url) do
  case URI.parse(base_url) do
    %URI{scheme: scheme, host: host} = uri when is_binary(host) ->
      port_suffix =
        case {uri.scheme, uri.port} do
          {"https", 443} -> ""
          {"http", 80} -> ""
          {_, nil} -> ""
          {_, port} -> ":#{port}"
        end
  
      {scheme <> "://", host <> port_suffix}
  
    _ ->
      {"", base_url}
  end
end
