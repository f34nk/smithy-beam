defmodule AwsEventStream do
  @moduledoc false

  @header_type_true 0
  @header_type_false 1
  @header_type_integer 4
  @header_type_string 7

  def frame(headers, payload) when is_list(headers) and is_binary(payload) do
    headers_bin = encode_headers(headers)
    headers_len = byte_size(headers_bin)
    payload_len = byte_size(payload)
    total_len = headers_len + payload_len + 16
    prelude = <<total_len::32-big, headers_len::32-big>>
    prelude_crc = :erlang.crc32(prelude)

    message_without_crc =
      <<prelude::binary, prelude_crc::32-big, headers_bin::binary, payload::binary>>

    message_crc = :erlang.crc32(message_without_crc)
    <<message_without_crc::binary, message_crc::32-big>>
  end

  def decode_frames(body) when is_binary(body) do
    body
    |> decode_frames([])
    |> Enum.reverse()
  end

  def encode_event_headers(event_type) do
    [
      {":event-type", event_type},
      {":message-type", "event"},
      {":content-type", "application/json"}
    ]
  end

  def header_value(headers, name) do
    Enum.find_value(headers, fn {key, value} -> if key == name, do: value end)
  end

  defp decode_frames(<<>>, acc), do: acc

  defp decode_frames(bin, acc) do
    %{rest: rest} = frame = decode_frame!(bin)
    decode_frames(rest, [Map.delete(frame, :rest) | acc])
  end

  defp decode_frame!(bin) do
    case bin do
      <<total_len::32-big, headers_len::32-big, prelude_crc::32-big, rest::binary>>
      when total_len >= 16 and headers_len <= total_len - 16 ->
        prelude = <<total_len::32-big, headers_len::32-big>>

        if :erlang.crc32(prelude) == prelude_crc do
          decode_frame_body(total_len, headers_len, prelude_crc, rest)
        else
          raise ArgumentError, "bad event stream: invalid_prelude_crc"
        end

      _ ->
        raise ArgumentError, "bad event stream: incomplete"
    end
  end

  defp decode_frame_body(total_len, headers_len, prelude_crc, rest) do
    payload_len = total_len - headers_len - 16

    case rest do
      <<headers_bin::binary-size(headers_len), payload::binary-size(payload_len),
        message_crc::32-big, tail::binary>> ->
        message_without_crc =
          <<total_len::32-big, headers_len::32-big, prelude_crc::32-big, headers_bin::binary,
            payload::binary>>

        if :erlang.crc32(message_without_crc) == message_crc do
          %{
            headers: decode_headers(headers_bin, []),
            payload: payload,
            rest: tail
          }
        else
          raise ArgumentError, "bad event stream: invalid_message_crc"
        end

      _ ->
        raise ArgumentError, "bad event stream: incomplete"
    end
  end

  defp encode_headers(headers), do: Enum.reduce(headers, <<>>, &encode_header/2)

  defp encode_header({name, value}, acc) when is_binary(name) and is_binary(value) do
    value_len = byte_size(value)
    name_len = byte_size(name)

    <<acc::binary, name_len::8, name::binary, @header_type_string::8, value_len::16-big,
      value::binary>>
  end

  defp encode_header({name, true}, acc) when is_binary(name) do
    name_len = byte_size(name)
    <<acc::binary, name_len::8, name::binary, @header_type_true::8>>
  end

  defp encode_header({name, false}, acc) when is_binary(name) do
    name_len = byte_size(name)
    <<acc::binary, name_len::8, name::binary, @header_type_false::8>>
  end

  defp encode_header({name, value}, acc) when is_binary(name) and is_integer(value) do
    name_len = byte_size(name)
    <<acc::binary, name_len::8, name::binary, @header_type_integer::8, value::32-big>>
  end

  defp encode_header({name, _value}, _acc) do
    raise ArgumentError, "bad event stream: unsupported header #{inspect(name)}"
  end

  defp decode_headers(<<>>, acc), do: Enum.reverse(acc)

  defp decode_headers(
         <<name_len::8, name::binary-size(name_len), @header_type_true::8, rest::binary>>,
         acc
       ),
       do: decode_headers(rest, [{name, true} | acc])

  defp decode_headers(
         <<name_len::8, name::binary-size(name_len), @header_type_false::8, rest::binary>>,
         acc
       ),
       do: decode_headers(rest, [{name, false} | acc])

  defp decode_headers(
         <<name_len::8, name::binary-size(name_len), @header_type_integer::8, value::32-big,
           rest::binary>>,
         acc
       ),
       do: decode_headers(rest, [{name, value} | acc])

  defp decode_headers(
         <<name_len::8, name::binary-size(name_len), @header_type_string::8, value_len::16-big,
           value::binary-size(value_len), rest::binary>>,
         acc
       ),
       do: decode_headers(rest, [{name, value} | acc])

  defp decode_headers(_bin, _acc) do
    raise ArgumentError, "bad event stream: invalid_header"
  end
end
