@doc "Encodes a list of event stream events into framed binaries."
def encode_event_stream(events) when is_list(events) do
  Enum.map(events, &#encode_event_stream_event/1)
end

@doc "Decodes an event stream body into tagged events."
def decode_event_stream(body) when is_binary(body) do
  body
  |> AwsEventStream.decode_frames()
  |> Enum.map(&decode_event_stream_event/1)
end

defp encode_event_stream_event({:member, value}) do
  payload = Jason.encode!(%{"value" => Map.get(value, :value)}),
  headers = encode_event_headers("member"),
  AwsEventStream.frame(headers, payload)
end
defp encode_event_stream_event({:unknown, _}), do: raise ArgumentError, "unknown event"

defp decode_event_stream_event(%{:headers => headers, :payload => payload}) do
  event_type = header_value(headers, ":event-type"),
  decode_event_stream_event_type(event_type, payload)
end

defp decode_event_stream_event_type(
  "member",
  payload
) do
  {:member, case Jason.decode!(payload) do
  decoded ->
    %EventStreamRestJsonServiceTypes.MemberEvent{
      value: Map.get(decoded, "value")
    }
end}
end
defp decode_event_stream_event_type(
  event_type,
  _payload
) do
  raise ArgumentError, "unknown event type: " <> inspect(event_type)
end