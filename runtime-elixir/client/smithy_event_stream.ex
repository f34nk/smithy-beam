defmodule SmithyEventStream do
  @moduledoc """
  Bidirectional event stream helper for Smithy `@streaming` unions.

  This is a stub that exposes the public surface required by the
  generated `<op>_stream/3` wrappers. The actual transport (HTTP/2
  framing, AWS event-stream binary encoding, websocket fallback) is
  not yet implemented.

  Generated client code calls into this module as follows:

      SmithyEventStream.start_stream(client, input, %{
        initial_message: initial,
        encode_event: fn event -> frame end,
        decode_event: fn frame -> event end,
        on_event: fn event -> :ok end,
        on_error: fn reason -> :ok end
      })

  On success the function returns `{:ok, stream_ref}`, where `stream_ref`
  is an opaque term that the caller can later pass to `send_event/2`,
  `recv_event/1` or `close_stream/1`. Until the runtime is implemented all
  four functions return `{:error, :not_implemented}`.
  """

  @opaque stream_ref :: reference()
  @type stream_opts :: map()

  @doc """
  Opens a new bidirectional event stream against the operation described by
  `input` on the connection carried by `client`.

  The runtime is not yet implemented; the function exists so the generated
  code links and so smoke tests can confirm the shape of the wrapper.
  Returns `{:error, :not_implemented}` until the runtime lands.
  """
  @spec start_stream(map(), term(), stream_opts()) :: {:ok, stream_ref()} | {:error, term()}
  def start_stream(_client, _input, _opts), do: {:error, :not_implemented}

  @doc """
  Sends an event on the input side of an open bidirectional stream.

  Returns `{:error, :not_implemented}` until the runtime lands.
  """
  @spec send_event(stream_ref(), term()) :: :ok | {:error, term()}
  def send_event(_stream_ref, _event), do: {:error, :not_implemented}

  @doc """
  Receives the next event from the output side of an open stream.

  Returns `{:ok, :end_of_stream}` when the remote half has closed.
  Returns `{:error, :not_implemented}` until the runtime lands.
  """
  @spec recv_event(stream_ref()) :: {:ok, term()} | {:ok, :end_of_stream} | {:error, term()}
  def recv_event(_stream_ref), do: {:error, :not_implemented}

  @doc """
  Closes both halves of an open stream.

  Returns `{:error, :not_implemented}` until the runtime lands.
  """
  @spec close_stream(stream_ref()) :: :ok | {:error, term()}
  def close_stream(_stream_ref), do: {:error, :not_implemented}
end
