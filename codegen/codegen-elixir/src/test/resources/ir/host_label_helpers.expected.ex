defp build_host(%Types.GetTenantDataInput{tenant: tenant}, config) do
  base_url = Map.get(config, :base_url, "")
  {_scheme, authority} = Utils.split_base_url(base_url)
  prefix = URI.encode(Kernel.to_string(tenant)) <> "."
  prefix <> authority
end
