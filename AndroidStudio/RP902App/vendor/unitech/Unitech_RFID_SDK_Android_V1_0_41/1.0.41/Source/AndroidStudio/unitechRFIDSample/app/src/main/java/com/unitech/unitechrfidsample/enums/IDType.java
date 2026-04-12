package com.unitech.unitechrfidsample.enums;

public enum IDType {
    ConnectState(0),
    Temperature(1),
    TagTID(11),
    TagEPC(12),
    AccessResult(13),
    TagRSSI(18),
    Battery(20),
    Inventory(21),
    Data(22),
    Find(23),
;



    private final int type;

    IDType(int type) {
        this.type = type;
    }

    public int value() {
        return type;
    }

    public static IDType valueOf(int value) throws IllegalArgumentException {
        for (IDType e : IDType.values()) {
            if (e.type == value) {
                return e;
            }
        }

        return ConnectState;
    }
}
