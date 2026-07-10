-module(xml_decode_test).

-include_lib("eunit/include/eunit.hrl").
-include("amazon_s3_types.hrl").
-include("runtime_types.hrl").

-define(BUCKET_NAME, <<"smithy-beam-minimal-s3-erlang">>).

sample_body() ->
    <<"<?xml version='1.0' encoding='utf-8'?>",
      "<ListAllMyBucketsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">",
      "<Owner><DisplayName>webfile</DisplayName></Owner>",
      "<Buckets><Bucket><Name>smithy-beam-minimal-s3-erlang</Name>",
      "<CreationDate>2026-06-03T12:00:09.000Z</CreationDate></Bucket></Buckets>",
      "</ListAllMyBucketsResult>">>.

decode_sample_test() ->
    Resp = #http_response{status = 200, headers = [], body = sample_body()},
    ?assertMatch(
        {ok, #list_buckets_output{buckets = [#bucket{name = ?BUCKET_NAME} | _]}},
        amazon_s3_rest_xml:decode_list_buckets_response(Resp)
    ).
