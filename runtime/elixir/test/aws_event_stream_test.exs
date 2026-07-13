defmodule AwsEventStreamTest do
  use ExUnit.Case, async: true

  @payload_only_frame <<
    0,
    0,
    0,
    30,
    0,
    0,
    0,
    0,
    186,
    242,
    246,
    138,
    123,
    34,
    102,
    111,
    111,
    34,
    58,
    32,
    34,
    98,
    97,
    114,
    34,
    125,
    174,
    114,
    88,
    228
  >>

  @json_header_frame <<
    0,
    0,
    0,
    62,
    0,
    0,
    0,
    32,
    64,
    93,
    249,
    70,
    12,
    99,
    111,
    110,
    116,
    101,
    110,
    116,
    45,
    116,
    121,
    112,
    101,
    7,
    0,
    16,
    97,
    112,
    112,
    108,
    105,
    99,
    97,
    116,
    105,
    111,
    110,
    47,
    106,
    115,
    111,
    110,
    123,
    34,
    102,
    111,
    111,
    34,
    58,
    32,
    34,
    98,
    97,
    114,
    34,
    125,
    111,
    162,
    191,
    59
  >>

  test "frame/2 round trip" do
    headers = [
      {":event-type", "SubscribeToShardEvent"},
      {":message-type", "event"},
      {":content-type", "application/json"}
    ]

    payload = ~s({"Records":[]})
    framed = AwsEventStream.frame(headers, payload)
    [frame] = AwsEventStream.decode_frames(framed)
    assert frame.headers == headers
    assert frame.payload == payload
  end

  test "decode_frames/1 known payload-only frame" do
    [frame] = AwsEventStream.decode_frames(@payload_only_frame)
    assert frame.headers == []
    assert frame.payload == ~s({"foo": "bar"})
  end

  test "decode_frames/1 known json header frame" do
    [frame] = AwsEventStream.decode_frames(@json_header_frame)
    assert frame.headers == [{"content-type", "application/json"}]
    assert frame.payload == ~s({"foo": "bar"})
  end

  test "frame/2 matches known payload-only frame" do
    assert AwsEventStream.frame([], ~s({"foo": "bar"})) == @payload_only_frame
  end

  test "frame/2 matches known json header frame" do
    assert AwsEventStream.frame([{"content-type", "application/json"}], ~s({"foo": "bar"})) ==
             @json_header_frame
  end

  test "decode_frames/1 multiple frames" do
    frame1 = AwsEventStream.frame([], "one")
    frame2 = AwsEventStream.frame([{":event-type", "event"}], "two")
    frames = AwsEventStream.decode_frames(frame1 <> frame2)
    assert length(frames) == 2
    assert hd(frames).payload == "one"
    assert List.last(frames).payload == "two"
  end

  test "decode_frames/1 empty body" do
    assert AwsEventStream.decode_frames(<<>>) == []
  end

  test "decode_frames/1 invalid prelude crc" do
    bad_frame =
      <<0, 0, 0, 30, 0, 0, 0, 0, 0, 0, 0, 0, 123, 34, 102, 111, 111, 34, 58, 32, 34, 98, 97, 114,
        34, 125, 174, 114, 88, 228>>

    assert_raise ArgumentError, fn -> AwsEventStream.decode_frames(bad_frame) end
  end

  test "decode_frames/1 invalid message crc" do
    bad_frame =
      <<0, 0, 0, 30, 0, 0, 0, 0, 186, 242, 246, 138, 123, 34, 102, 111, 111, 34, 58, 32, 34, 98,
        97, 114, 34, 125, 0, 0, 0, 0>>

    assert_raise ArgumentError, fn -> AwsEventStream.decode_frames(bad_frame) end
  end
end
