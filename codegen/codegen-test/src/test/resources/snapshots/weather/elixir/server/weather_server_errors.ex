defmodule Weather.Server.Errors.NoSuchResourceError do
  defexception [:resource_type]

  @type t() :: %__MODULE__{
    resource_type: String.t()
  }

  def message(%__MODULE__{}), do: "NoSuchResourceError"
end
