defmodule HeadBucketHttpMock do
  @moduledoc false
  @behaviour RuntimeHttpClient

  alias RuntimeHttpClient.Request
  alias RuntimeTypes.HttpResponse

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

  @impl RuntimeHttpClient
  def request(%Request{method: :head, url: url}) do
    if String.ends_with?(url, "/my-bucket") do
      count = :persistent_term.get(@count_key, 0)
      :persistent_term.put(@count_key, count + 1)

      case Enum.at(:persistent_term.get(@responses_key, []), count) do
        {:status, status} ->
          {:ok, %HttpResponse{status: status, headers: [], body: ""}}

        nil ->
          {:error, {:no_mock_response, count}}
      end
    else
      {:error, {:unexpected_request, url}}
    end
  end

  def request(%Request{} = req) do
    {:error, {:unexpected_request, req}}
  end
end
