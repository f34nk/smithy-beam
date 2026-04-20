defmodule SmithyHandler do
  @moduledoc """
  Behaviour definition for Smithy-generated Elixir server handlers.

  Every generated server operation becomes a required callback that user
  service implementations must provide.  The code generator produces a
  skeleton module with `@behaviour SmithyHandler` and a stub for each
  operation.

  ## Required callback

      @callback handle_request(operation :: atom(), input :: map(), context :: map()) ::
                  {:ok, map()} | {:error, term()}

  `operation` — snake_case operation name (e.g. `:get_weather`).
  `input`     — decoded, validated request body map.
  `context`   — metadata map: path bindings, headers, raw body, …
  Returns `{:ok, output_map}` or `{:error, reason}` (forwarded to
  `SmithyServer.error_response/2`).

  ## Optional callbacks

  Implementations may export `init/1` and `terminate/2` for lifecycle hooks
  called by the dispatcher before and after each request.

  ## Helper functions

  This module provides utility functions that handler implementations can call:

    * `ok/1`              — wraps an output map in `{:ok, output}`.
    * `error/1`           — wraps a reason in `{:error, reason}`.
    * `not_found/0`       — returns `{:error, :not_found}`.
    * `context_binding/2` — fetch a path label from the context.
    * `context_header/2`  — fetch a request header from the context.
  """

  @doc """
  Dispatch a single Smithy operation.

  Called by the generated dispatcher with the decoded, validated input.
  Must return `{:ok, output}` or `{:error, reason}`.
  """
  @callback handle_request(
              operation :: atom(),
              input :: map(),
              context :: map()
            ) :: {:ok, map()} | {:error, term()}

  @doc """
  Called before the first request in a connection/request lifecycle.

  Receives the initial context and may return an augmented context.
  """
  @callback init(context :: map()) :: {:ok, map()} | {:error, term()}

  @doc """
  Called after the response has been sent.
  """
  @callback terminate(reason :: term(), context :: map()) :: :ok

  @optional_callbacks [init: 1, terminate: 2]

  # ---------------------------------------------------------------------------
  # Response builders
  # ---------------------------------------------------------------------------

  @doc """
  Wrap a successful output map in the `{:ok, output}` tuple expected by
  the dispatcher.
  """
  @spec ok(map()) :: {:ok, map()}
  def ok(output) when is_map(output), do: {:ok, output}

  @doc """
  Wrap an error reason in `{:error, reason}`.

  The dispatcher forwards the reason to `SmithyServer.error_response/2`.
  """
  @spec error(term()) :: {:error, term()}
  def error(reason), do: {:error, reason}

  @doc """
  Signal that the requested resource or operation does not exist.
  """
  @spec not_found() :: {:error, :not_found}
  def not_found, do: {:error, :not_found}

  # ---------------------------------------------------------------------------
  # Context helpers
  # ---------------------------------------------------------------------------

  @doc """
  Retrieve a path-label binding from the request context.

  Returns `{:ok, value}` when the binding exists, otherwise
  `{:error, :not_found}`.

  ## Example

      context_binding(context, "id")   # => {:ok, "42"}
  """
  @spec context_binding(map(), String.t()) :: {:ok, String.t()} | {:error, :not_found}
  def context_binding(context, name) do
    bindings = Map.get(context, :path_bindings, %{})

    case Map.fetch(bindings, name) do
      {:ok, _} = ok -> ok
      :error        -> {:error, :not_found}
    end
  end

  @doc """
  Retrieve a request header value from the request context.

  Header names are lowercased in the context map.  Returns `{:ok, value}`
  or `{:error, :not_found}`.

  ## Example

      context_header(context, "content-type")   # => {:ok, "application/json"}
  """
  @spec context_header(map(), String.t()) :: {:ok, String.t()} | {:error, :not_found}
  def context_header(context, name) do
    headers   = Map.get(context, :headers, %{})
    lower_name = String.downcase(name)

    case Map.fetch(headers, lower_name) do
      {:ok, _} = ok -> ok
      :error        -> {:error, :not_found}
    end
  end
end
