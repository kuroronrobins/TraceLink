package com.unitech.unitechrfidsample.enums;

public enum FragmentType {
    None(0),
    MainFragment(1),
    SampleFragment(2),
    ;

    private final int _type;

    FragmentType(int type) {
        this._type = type;
    }

    public int value() {
        return _type;
    }

    public static FragmentType valueOf(int value) {
        for (FragmentType e : FragmentType.values()) {
            if (e._type == value) {
                return e;
            }
        }

        return None;
    }
}
