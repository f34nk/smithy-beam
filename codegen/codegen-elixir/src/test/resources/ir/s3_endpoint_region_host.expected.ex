@spec region_host(map()) :: String.t()
def region_host(config) do
  base_url = Map.get(config, :base_url, "")
  {_scheme, authority} = split_base_url(base_url)
  authority
end