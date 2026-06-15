defmodule BasicServiceClientPaginationTest do
  use ExUnit.Case, async: true

  alias BasicServiceTypes.ListBasicItemsInput

  test "list_basic_items collects all pages" do
    config = %{base_url: "https://api.example", http_client: RuntimeHttpMock}
    input = %ListBasicItemsInput{}

    assert {:ok, items} = BasicServiceClient.list_basic_items(config, input)
    assert length(items) == 2
  end
end
