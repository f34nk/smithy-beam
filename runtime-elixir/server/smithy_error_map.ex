defmodule SmithyErrorMap do
  @moduledoc """
  Maps Smithy error terms to `{http_status, message}` pairs.

  This is the generic fallback module used when a service has no modelled errors.
  For services that define error shapes with `@httpError`, the code generator
  produces a per-service override that prepends specific clauses for each modelled
  error before falling through to these defaults.

  Mirrors `smithy_error_map.erl` from the Erlang runtime.
  """

  @doc """
  Map a Smithy error term to `{http_status_code, message}`.

  ## Error terms

  | Term                      | Status | Notes                        |
  |---------------------------|--------|------------------------------|
  | `{:not_found, msg}`       | 404    |                              |
  | `{:conflict, msg}`        | 409    |                              |
  | `{:validation, msg}`      | 400    |                              |
  | `{:internal, msg}`        | 500    |                              |
  | `{:unauthorized, msg}`    | 401    |                              |
  | `{:forbidden, msg}`       | 403    |                              |
  | `:not_implemented`        | 501    |                              |
  | anything else             | 500    | Generic internal server error |
  """
  @spec to_http(term()) :: {pos_integer(), String.t()}
  def to_http({:not_found, msg}),    do: {404, msg}
  def to_http({:conflict, msg}),     do: {409, msg}
  def to_http({:validation, msg}),   do: {400, msg}
  def to_http({:internal, msg}),     do: {500, msg}
  def to_http({:unauthorized, msg}), do: {401, msg}
  def to_http({:forbidden, msg}),    do: {403, msg}
  def to_http(:not_implemented),     do: {501, "Not implemented"}
  def to_http(_),                    do: {500, "Internal server error"}
end
