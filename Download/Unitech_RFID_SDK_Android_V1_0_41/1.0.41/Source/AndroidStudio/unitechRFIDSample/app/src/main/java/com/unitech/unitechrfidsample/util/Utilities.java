package com.unitech.unitechrfidsample.util;

import android.os.Bundle;

import com.unitech.unitechrfidsample.ExtraName;
import com.unitech.unitechrfidsample.MainActivity;
import com.unitech.unitechrfidsample.enums.FragmentType;
import com.unitech.unitechrfidsample.enums.UpdateUIType;

public class Utilities {

    public static void updateUIText(FragmentType fragmentType, int id, String data) {
        updateUI(fragmentType, id, UpdateUIType.Text, data);
    }

    /**
     * Update the fragment's UI via handle
     *
     * @param fragmentType The fragment type
     * @param id           The target view's ID, it defined in each fragmetn
     * @param uiType       The update type, for fragment. Ex: text, enable, number
     * @param obj          The update information
     */
    public static void updateUI(FragmentType fragmentType, int id, UpdateUIType uiType, Object obj) {
        Bundle bundle = new Bundle();

        bundle.putInt(ExtraName.Type, uiType.value());
        bundle.putInt(ExtraName.TargetID, id);

        if (obj instanceof String) {
            bundle.putString(ExtraName.Text, (String) obj);
        } else if (obj instanceof Boolean) {
            bundle.putBoolean(ExtraName.Enable, (boolean) obj);
        } else if (obj instanceof Integer) {
            bundle.putInt(ExtraName.Number, (Integer) obj);
        }

        MainActivity.triggerHandler(fragmentType, bundle);
    }
}
