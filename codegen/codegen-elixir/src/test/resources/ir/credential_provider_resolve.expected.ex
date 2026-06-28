@spec resolve(client_config()) :: {:ok, aws_credentials()} | {:error, term()}
def resolve(config) do
  case Map.get(config, :credentials) do
    nil -> resolve_chain(config)

    creds -> {:ok, creds}
  end
end