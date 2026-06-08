defmodule GreetingServiceNdjsonProtocolTest do
  use ExUnit.Case, async: true

  alias GreetingServiceTypes.SayHelloInput

  test "encode_say_hello_request/1" do
    input = %SayHelloInput{name: "world"}
    req = GreetingServiceNdjsonProtocol.encode_say_hello_request(input)
    assert req == %{operation: :say_hello, input: input}
  end

  test "decode_say_hello_response/1" do
    assert {:ok, nil} = GreetingServiceNdjsonProtocol.decode_say_hello_response("")
  end

  test "client wires custom codec" do
    client_src = File.read!("lib/generated/greeting_service_client.ex")

    assert client_src =~ "GreetingServiceNdjsonProtocol.encode_say_hello_request"
    refute client_src =~ "not_implemented"
  end
end
