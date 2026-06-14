defmodule XmlDecodeTest do
  use ExUnit.Case, async: true

  alias AmazonS3Types.{Bucket, ListBucketsOutput}
  alias RuntimeTypes.HttpResponse

  defp sample_body do
    """
    <?xml version='1.0' encoding='utf-8'?>
    <ListAllMyBucketsResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
    <Owner><DisplayName>webfile</DisplayName></Owner>
    <Buckets><Bucket><Name>smithy-beam-minimal-s3-elixir</Name>
    <CreationDate>2026-06-03T12:00:09.000Z</CreationDate></Bucket></Buckets>
    </ListAllMyBucketsResult>
    """
  end

  test "decodes list buckets sample XML" do
    resp = %HttpResponse{status: 200, headers: [], body: sample_body()}

    assert {:ok, %ListBucketsOutput{buckets: [%Bucket{name: "smithy-beam-minimal-s3-elixir"} | _]}} =
             AmazonS3RestXml.decode_list_buckets_response(resp)
  end
end
