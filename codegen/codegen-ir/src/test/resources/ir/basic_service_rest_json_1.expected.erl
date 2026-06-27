%% Generated REST JSON codec.
-moduledoc "REST JSON 1 codecs for basic_service (generated).".
-module(basic_service_rest_json_1).
-include("basic_types.hrl").
-export([decode_basic_item/1]).

-spec decode_basic_item(undefined | null | map()) -> undefined | #basic_item{}.
decode_basic_item(undefined) ->
    undefined;
decode_basic_item(null) ->
    undefined;
decode_basic_item(Map) when is_map(Map) ->
    #basic_item{
        name = maps:get(<<"name">>, Map, undefined),
        count = maps:get(<<"count">>, Map, undefined)
    }.
