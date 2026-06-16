defmodule HeadBucketHttpMock do
  @moduledoc false

  @responses_key {__MODULE__, :responses}
  @count_key {__MODULE__, :count}

  def reset(responses) when is_list(responses) do
    :persistent_term.put(@responses_key, responses)
    :persistent_term.put(@count_key, 0)
    :ok
  end

  def call_count do
    :persistent_term.get(@count_key, 0)
  end

  def request(req_opts) do
    method = Keyword.fetch!(req_opts, :method)
    url = Keyword.fetch!(req_opts, :url)

    unless method == :head and String.ends_with?(url, "/my-bucket") do
      raise "unexpected request: #{inspect(req_opts)}"
    end

    count = :persistent_term.get(@count_key, 0)
    :persistent_term.put(@count_key, count + 1)

    case Enum.at(:persistent_term.get(@responses_key, []), count) do
      {:status, status} ->
        {:ok, %{status: status, headers: [], body: ""}}

      nil ->
        {:error, {:no_mock_response, count}}
    end
  end
end
