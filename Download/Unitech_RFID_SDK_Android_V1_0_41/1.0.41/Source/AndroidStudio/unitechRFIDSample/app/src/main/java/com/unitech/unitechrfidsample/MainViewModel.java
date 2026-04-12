package com.unitech.unitechrfidsample;

import androidx.lifecycle.ViewModel;

import com.unitech.lib.types.DeviceType;

public class MainViewModel extends ViewModel {
    public String bluetoothMACAddress = "";
    public DeviceType deviceType = DeviceType.Unknown;
}
