%% Record and type definitions for the basic_service_types model.
%%
-record(basic_item, {
    name :: basic_string(),
    count :: basic_integer() | undefined
}).
-type basic_item() :: #basic_item{}.
