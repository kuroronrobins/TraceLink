package com.unitech.unitechrfidsample.enums;

public enum HandlerMsg {
    Toast(0),
    Dialog(1),
    Unknown(255),
    ;

    private final int _msg;

    HandlerMsg(int msg) {
        this._msg = msg;
    }

    public int value() {
        return _msg;
    }

    public static HandlerMsg valueOf(int value) {
        for (HandlerMsg e : HandlerMsg.values()) {
            if (e._msg == value) {
                return e;
            }
        }

        return Unknown;
    }
}
