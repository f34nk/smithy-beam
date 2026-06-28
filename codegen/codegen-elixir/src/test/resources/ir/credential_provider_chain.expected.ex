@spec resolve_chain(client_config()) :: {:ok, aws_credentials()} | {:error, term()}
defp resolve_chain(config) do
  resolve_chain(config, [:env, :profile, :ecs, :ec2])
end

defp resolve_chain(_config, []), do: {:error, :not_found}

defp resolve_chain(
  config,
  [provider | rest]
) do
  case resolve_provider(provider, config) do
    {:ok, creds} -> {:ok, creds}

    _ -> resolve_chain(config, rest)
  end
end

defp resolve_provider(:env, config), do: resolve_from_env(config)
defp resolve_provider(:profile, config), do: resolve_from_profile(config)
defp resolve_provider(:ecs, config), do: resolve_from_ecs(config)
defp resolve_provider(:ec2, config), do: resolve_from_ec2(config)