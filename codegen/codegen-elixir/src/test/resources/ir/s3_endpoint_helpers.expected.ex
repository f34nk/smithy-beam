defp key_path(""), do: ""
defp key_path(key), do: "/#{key}"

defp virtual_host(config, bucket, region_host) do
  if Map.get(config, :s3_use_accelerate, false) do
    "#{bucket}.s3-accelerate.amazonaws.com"
  else
    suffix = s3_host_suffix(config)
    "#{bucket}#{suffix}#{region_host}"
  end
end

defp s3_host_suffix(config) do
  if Map.get(config, :s3_use_dualstack, false), do: ".s3.dualstack.", else: ".s3."
end

defp split_base_url(""), do: {"", ""}
defp split_base_url(base_url) do
  case URI.parse(base_url) do
    %URI{scheme: scheme, host: host, port: port} when is_binary(host) ->
      port_suffix =
        case port do
          nil -> ""
  
          p -> ":#{p}"
        end
  
      {"#{scheme}://", "#{host}#{port_suffix}"}
  
    _ ->
      {"", base_url}
  end
end