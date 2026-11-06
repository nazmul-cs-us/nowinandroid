%% Test double for MongooseIM's JSON dependency; OTP 27 provides the real JSON codec.
-module(jiffy).
-export([encode/1, decode/2]).
encode(Value) -> iolist_to_binary(json:encode(Value)).
decode(Value, [return_maps]) -> json:decode(Value).
