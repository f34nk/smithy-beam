defmodule BasicServicePaginatorsTest do
  use ExUnit.Case, async: true

  alias BasicTypes.ListBasicItemsInput

  describe "paginate_list_basic_items/2" do
    test "collects all pages through the generated client" do
      config = %{base_url: "https://api.example", http_client: RuntimeHttpMock}
      input = %ListBasicItemsInput{}

      assert {:ok, items} = BasicServicePaginators.paginate_list_basic_items(config, input)
      assert length(items) == 2
      assert Enum.at(items, 0) == %{"name" => "alpha", "count" => 1}
      assert Enum.at(items, 1) == %{"name" => "beta", "count" => 2}
    end
  end

  describe "exports" do
    test "defines paginate_list_basic_items/2 and /3" do
      assert {:paginate_list_basic_items, 2} in BasicServicePaginators.__info__(:functions)
      assert {:paginate_list_basic_items, 3} in BasicServicePaginators.__info__(:functions)
    end
  end
end
