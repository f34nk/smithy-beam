-module(weather_client_types).

-record(get_weather_input, {
    city :: binary()
}).
-type get_weather_input() :: #get_weather_input{}.


-record(get_weather_output, {
    temperature :: float()
}).
-type get_weather_output() :: #get_weather_output{}.


-record(list_cities_input, {
    filter :: binary(),
    custom_header :: binary(),
    next_token :: binary(),
    max_results :: integer()
}).
-type list_cities_input() :: #list_cities_input{}.


-record(list_cities_output, {
    next_token :: binary(),
    cities :: [binary()]
}).
-type list_cities_output() :: #list_cities_output{}.
