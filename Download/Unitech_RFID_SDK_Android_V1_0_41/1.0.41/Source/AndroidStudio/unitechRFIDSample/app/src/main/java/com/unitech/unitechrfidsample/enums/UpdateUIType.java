package com.unitech.unitechrfidsample.enums;

public enum UpdateUIType {
    Reset(0),
    Enable(1),
    SetValue(2),
    Connected(3),
    Disconnected(4),
    Icon(5),
    RSSI(6),
    Text(7),
    Dialog(8),
    Custom(255),
    ;

    private final int type;

    UpdateUIType(int type) {
        this.type = type;
    }

    public int value() {
        return type;
    }

    public static UpdateUIType valueOf(int value) throws IllegalArgumentException {
        for (UpdateUIType e : UpdateUIType.values()) {
            if (e.type == value) {
                return e;
            }
        }

        return Custom;
    }
}
