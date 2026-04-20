defmodule AwsJson11Service.Server.Impl do
  @moduledoc false
  @behaviour AwsJson11Service.Server

  # This file will NOT be overwritten. Add your business logic here.

  @impl true
  def describe_item(_input, _ctx), do: {:error, :not_implemented}

end
