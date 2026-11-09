package com.porteiro.client.net

object WsType {
    const val HELLO = "hello"
    const val INCOMING_CALL = "incoming_call"
    const val CALL_CANCELLED = "call_cancelled"
    const val CALL_ENDED = "call_ended"
    const val WEBRTC_OFFER = "webrtc_offer"
    const val WEBRTC_ANSWER = "webrtc_answer"
    const val WEBRTC_ICE = "webrtc_ice"
    const val UNLOCK = "unlock"
    const val HANGUP = "hangup"
    const val REJECT = "reject"
    const val UNLOCK_RESULT = "unlock_result"
    const val ERROR = "error"
    const val PING = "ping"
    const val PONG = "pong"
}
