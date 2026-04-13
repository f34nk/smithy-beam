-module(weather_client_test).
-include_lib("eunit/include/eunit.hrl").

%% Verify the generated module loads correctly.
module_loads_test() ->
    ?assert(code:ensure_loaded(weather_client) =:= {module, weather_client}).

%% new/1 should return {ok, Config}.
new_client_test() ->
    Config = #{endpoint => <<"http://localhost:8080">>},
    {ok, Client} = weather_client:new(Config),
    ?assertEqual(Config, Client).

%% Verify enum type definition is present in the generated file.
enum_type_defined_test() ->
    ModulePath = "src/generated/weather_client.erl",
    {ok, Content} = file:read_file(ModulePath),
    ?assert(binary:match(Content, <<"-type temperature_unit()">>) =/= nomatch).

%% Encode/decode round-trip for TemperatureUnit.
%% Enum atoms are lowercase to follow idiomatic Erlang style.
encode_temperature_unit_test() ->
    ?assertEqual(<<"Celsius">>,    weather_client:encode_temperature_unit(celsius)),
    ?assertEqual(<<"Fahrenheit">>, weather_client:encode_temperature_unit(fahrenheit)).

decode_temperature_unit_test() ->
    ?assertEqual({ok, celsius},    weather_client:decode_temperature_unit(<<"Celsius">>)),
    ?assertEqual({ok, fahrenheit}, weather_client:decode_temperature_unit(<<"Fahrenheit">>)),
    ?assertMatch({error, {invalid_enum_value, _}},
                 weather_client:decode_temperature_unit(<<"unknown">>)).

%% Verify the generated file exports get_weather/2 and create_weather_report/2.
exports_operations_test() ->
    ModulePath = "src/generated/weather_client.erl",
    {ok, Content} = file:read_file(ModulePath),
    ?assert(binary:match(Content, <<"get_weather/2">>) =/= nomatch),
    ?assert(binary:match(Content, <<"create_weather_report/2">>) =/= nomatch).

%% Verify parse_error/2 is exported.
exports_parse_error_test() ->
    ModulePath = "src/generated/weather_client.erl",
    {ok, Content} = file:read_file(ModulePath),
    ?assert(binary:match(Content, <<"parse_error/2">>) =/= nomatch).

%% Verify validation function is generated for CreateWeatherReportInput.
validate_input_test() ->
    %% Input missing required fields
    {error, {missing_required_fields, Missing}} =
        weather_client:validate_create_weather_report_input(#{}),
    ?assert(lists:member(<<"city">>, Missing)).

%% Verify validation passes when all required fields are present.
validate_input_ok_test() ->
    Input = #{
        <<"city">>        => <<"Berlin">>,
        <<"temperature">> => 20.5,
        <<"unit">>        => <<"Celsius">>
    },
    ?assertEqual(ok, weather_client:validate_create_weather_report_input(Input)).
