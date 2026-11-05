-module(ejabberd_auth).
-export([does_user_exist/1]).
-include_lib("jid/include/jid.hrl").
does_user_exist(#jid{luser = <<"missing">>}) -> false;
does_user_exist(_) -> true.
