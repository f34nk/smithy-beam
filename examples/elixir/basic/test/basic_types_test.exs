defmodule BasicTypesTest do
  use ExUnit.Case, async: true

  # ── BasicItem struct ─────────────────────────────────────────────────────────

  describe "BasicItem struct" do
    test "can be created with all fields" do
      item = %Basic.BasicItem{name: "hello", count: 42}
      assert item.name == "hello"
      assert item.count == 42
    end

    test "count defaults to nil when only required fields are set" do
      item = %Basic.BasicItem{name: "hello"}
      assert item.count == nil
    end

    test "supports struct update syntax" do
      item0 = %Basic.BasicItem{name: "a", count: 1}
      item1 = %{item0 | count: 2}
      assert item1.name == "a"
      assert item1.count == 2
    end
  end

  # ── BasicStatus (string enum as tagged-union type) ───────────────────────────

  describe "BasicStatus enum" do
    test "known wire values map to atoms" do
      assert Basic.BasicStatus.from_string("ACTIVE") == :active
      assert Basic.BasicStatus.from_string("INACTIVE") == :inactive
      assert Basic.BasicStatus.from_string("PENDING") == :pending
    end

    test "unknown wire values round-trip via {:unknown, value}" do
      assert Basic.BasicStatus.from_string("FUTURE_STATUS") ==
               {:unknown, "FUTURE_STATUS"}

      assert Basic.BasicStatus.to_string({:unknown, "FUTURE_STATUS"}) ==
               "FUTURE_STATUS"
    end

    test "values/0 returns all known atoms" do
      assert Basic.BasicStatus.values() == [:active, :inactive, :pending]
    end
  end

  # ── BasicPriority (integer enum as tagged-union type) ────────────────────────

  describe "BasicPriority intEnum" do
    test "known integer values map to atoms" do
      assert Basic.BasicPriority.from_integer(1) == :low
      assert Basic.BasicPriority.from_integer(2) == :medium
      assert Basic.BasicPriority.from_integer(3) == :high
    end

    test "unknown integer values round-trip via {:unknown, value}" do
      assert Basic.BasicPriority.from_integer(99) == {:unknown, 99}
      assert Basic.BasicPriority.to_integer({:unknown, 99}) == 99
    end

    test "values/0 returns all known atoms" do
      assert Basic.BasicPriority.values() == [:low, :medium, :high]
    end
  end

  # ── BasicUnion (tagged union) ────────────────────────────────────────────────

  describe "BasicUnion tagged tuple" do
    test "text variant" do
      v = {:text, "hello"}
      assert {:text, str} = v
      assert str == "hello"
    end

    test "number variant" do
      v = {:number, 42}
      assert {:number, n} = v
      assert n == 42
    end

    test "flag variant" do
      v = {:flag, true}
      assert {:flag, b} = v
      assert b == true
    end

    test "unknown variant preserves wire tag" do
      v = {:unknown, "new_field"}
      assert {:unknown, tag} = v
      assert tag == "new_field"
    end
  end
end
