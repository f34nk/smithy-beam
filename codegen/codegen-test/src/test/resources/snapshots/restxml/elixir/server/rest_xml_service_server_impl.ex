defmodule RestXmlService.Server.Impl do
  @moduledoc false
  @behaviour RestXmlService.Server

  # This file will NOT be overwritten. Add your business logic here.

  @impl true
  def list_buckets(_input, _ctx), do: {:error, :not_implemented}

end
