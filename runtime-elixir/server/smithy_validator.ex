defmodule SmithyValidator do
  @moduledoc """
  Input validation helpers for Smithy-generated Elixir server dispatchers.

  Validates that required fields are present in an input map and formats
  human-readable error messages for missing fields.

  Mirrors `smithy_validator.erl` from the Erlang runtime.
  """

  @doc """
  Validate that all `required_fields` are present in `input`.

  Returns `:ok` when every field is present, or
  `{:error, {:missing_required_fields, missing}}` listing the absent keys.
  """
  @spec validate(input :: map(), required_fields :: [term()]) ::
          :ok | {:error, {:missing_required_fields, [term()]}}
  def validate(input, required_fields) do
    missing = Enum.reject(required_fields, &Map.has_key?(input, &1))

    case missing do
      [] -> :ok
      _ -> {:error, {:missing_required_fields, missing}}
    end
  end

  @doc """
  Format a validation error term into a human-readable string.
  """
  @spec format({:missing_required_fields, [term()]}) :: String.t()
  def format({:missing_required_fields, fields}) do
    "Missing required fields: #{Enum.join(fields, ", ")}"
  end
end
