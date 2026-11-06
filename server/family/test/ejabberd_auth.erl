-module(ejabberd_auth).
-export([does_user_exist/1, try_register/2]).
-include_lib("jid/include/jid.hrl").
does_user_exist(#jid{luser = <<"missing">>}) -> false;
does_user_exist(_) -> true.

try_register(#jid{luser = <<"backend-down">>}, _) -> {error, not_allowed};
try_register(#jid{luser = User}, Password) ->
    case ets:insert_new(signup_test_accounts, {User, Password}) of
        true -> ok; false -> {error, exists}
    end.
