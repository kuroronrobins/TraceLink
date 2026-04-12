package com.unitech.unitechrfidsample.enums;

public class HistogramData {

    public int size;        // 總格數
    public int value;       // 幾格
    public String name;     // 數值

    public HistogramData(int size){
        this.size = size;
    }

    public void setData(int value, String name){
        this.value = value;
        this.name = name;
    }
}
