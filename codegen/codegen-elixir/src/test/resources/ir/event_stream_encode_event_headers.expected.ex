defp encode_event_headers(event_type) do
  [{":event-type", event_type}, {":message-type", "event"}, {":content-type", "application/json"}]
end