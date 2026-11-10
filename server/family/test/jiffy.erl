%% Preserve Jiffy's iodata return contract; always flattening hid video-size failures.
-module(jiffy).
-export([encode/1, decode/2]).
encode(Value) -> json:encode(Value).
decode(Value, [return_maps]) -> json:decode(iolist_to_binary(Value)).
