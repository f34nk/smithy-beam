defmodule SmithyRouter do
  @moduledoc """
  Request routing for Smithy-generated Elixir servers.

  A generated dispatcher module builds a route table from its service model
  and delegates to `SmithyRouter.dispatch/4` (or `dispatch/5` with an explicit
  table).  Each route maps an `{method, path_pattern}` pair to a
  `{module, function}` handler.

  ## Route table format

  ```elixir
  [
    {"GET",    "/items",      MyHandler, :list_items},
    {"POST",   "/items",      MyHandler, :create_item},
    {"GET",    "/items/{id}", MyHandler, :get_item},
    {"DELETE", "/items/{id}", MyHandler, :delete_item},
  ]
  ```

  Each entry is a 4-tuple `{method, path_pattern, module, function}`.

  ## Path patterns

  Segments enclosed in `{}` are label captures:

      "/items/{id}"          → matches "/items/42", binds "id" => "42"
      "/prefix/{+proxy}"     → greedy label, captures the rest of the path

  ## Handler protocol

  The handler `module.function/3` is called as:

      module.function(bindings, headers, body)

  where `bindings` is a `%{String.t() => String.t()}` map.

  It must return one of:

      {:ok, output}          → 200 JSON response
      {:ok, status, body}    → custom status
      {:error, reason}       → forwarded to SmithyServer.error_response/2
      :not_found             → 404
  """

  @type method       :: String.t()
  @type path_pattern :: String.t()
  @type route        :: {method(), path_pattern(), module(), atom()}
  @type routes       :: [route()]
  @type bindings     :: %{String.t() => String.t()}
  @type headers      :: map() | [{String.t(), String.t()}]
  @type body         :: binary()

  # ---------------------------------------------------------------------------
  # Public API
  # ---------------------------------------------------------------------------

  @doc """
  Dispatch a request using an explicit route table.

  Iterates routes in order and stops at the first match.
  Calls the matched handler with `(bindings, headers, body)`.

  Returns the handler's result, or `:not_found` when nothing matches.
  """
  @spec dispatch(routes(), method(), String.t(), headers(), body()) :: term()
  def dispatch(routes, method, path, headers, body) do
    case match(routes, method, path) do
      {:ok, bindings, mod, fun} -> apply(mod, fun, [bindings, headers, body])
      :nomatch                  -> :not_found
    end
  end

  @doc """
  Find the first matching route for `method` and `path`.

  Returns `{:ok, bindings, module, function}` or `:nomatch`.
  """
  @spec match(routes(), method(), String.t()) ::
          {:ok, bindings(), module(), atom()} | :nomatch
  def match([], _method, _path), do: :nomatch

  def match([{route_method, pattern, mod, fun} | rest], method, path) do
    if route_method == method or route_method == "*" do
      case match_path(pattern, path) do
        {:ok, bindings} -> {:ok, bindings, mod, fun}
        :nomatch        -> match(rest, method, path)
      end
    else
      match(rest, method, path)
    end
  end

  # ---------------------------------------------------------------------------
  # Path matching
  # ---------------------------------------------------------------------------

  @doc """
  Test whether `path` matches `pattern`, returning label bindings.

  ## Examples

      iex> SmithyRouter.match_path("/items/{id}", "/items/42")
      {:ok, %{"id" => "42"}}

      iex> SmithyRouter.match_path("/items/{id}", "/other/42")
      :nomatch
  """
  @spec match_path(path_pattern(), String.t()) :: {:ok, bindings()} | :nomatch
  def match_path(pattern, path) do
    pattern_segs = split_path(pattern)
    path_segs    = split_path(path)
    match_segments(pattern_segs, path_segs, %{})
  end

  @doc """
  Extract named bindings from a matched path.

  Returns an empty map when the pattern does not match.
  """
  @spec extract_bindings(path_pattern(), String.t()) :: bindings()
  def extract_bindings(pattern, path) do
    case match_path(pattern, path) do
      {:ok, bindings} -> bindings
      :nomatch        -> %{}
    end
  end

  @doc """
  Split a URL path on `/`, discarding leading empty segments.

  ## Examples

      iex> SmithyRouter.split_path("/items/42")
      ["items", "42"]
  """
  @spec split_path(String.t()) :: [String.t()]
  def split_path(path) do
    path
    |> String.trim_leading("/")
    |> String.split("/", trim: true)
  end

  # ---------------------------------------------------------------------------
  # Private helpers
  # ---------------------------------------------------------------------------

  defp match_segments([], [], bindings), do: {:ok, bindings}

  # Greedy label {+name} — consumes all remaining path segments
  defp match_segments(["{+" <> rest | _pat_rest], path_segs, bindings) do
    label = String.trim_trailing(rest, "}")
    value = Enum.join(path_segs, "/")
    {:ok, Map.put(bindings, label, value)}
  end

  # Regular label {name} — matches exactly one segment
  defp match_segments(["{" <> rest | pat_rest], [seg | path_rest], bindings) do
    label = String.trim_trailing(rest, "}")
    match_segments(pat_rest, path_rest, Map.put(bindings, label, seg))
  end

  # Literal segment — must match exactly
  defp match_segments([literal | pat_rest], [literal | path_rest], bindings) do
    match_segments(pat_rest, path_rest, bindings)
  end

  defp match_segments(_, _, _), do: :nomatch
end
