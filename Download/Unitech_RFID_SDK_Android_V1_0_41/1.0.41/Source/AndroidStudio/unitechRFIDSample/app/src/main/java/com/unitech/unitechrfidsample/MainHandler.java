package com.unitech.unitechrfidsample;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.unitech.lib.types.DeviceType;
import com.unitech.unitechrfidsample.enums.FragmentType;
import com.unitech.unitechrfidsample.enums.HandlerMsg;
import com.unitech.unitechrfidsample.fragment.BaseFragment;

import org.tinylog.Logger;

public class MainHandler extends Handler {
    private MainActivity _activity;
    private AlertDialog alertDialog;

    public MainHandler(MainActivity activity) {
        this._activity = activity;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        super.handleMessage(msg);

        FragmentType fragmentType = FragmentType.valueOf(msg.what);

        BaseFragment baseFragment = null;

        switch (fragmentType) {
            case MainFragment:
                baseFragment = (BaseFragment) _activity.getFragment(R.id.MainFragment);
                break;
            case SampleFragment:
                baseFragment = (BaseFragment) _activity.getFragment(R.id.SampleFragment);
                break;
            case None:
                handlerProcess(msg.getData());
                break;
        }

        if (baseFragment != null) {
            baseFragment.receiveHandler(msg.getData());
        }
    }

    private void handlerProcess(Bundle bundle) {
        HandlerMsg msgType = HandlerMsg.valueOf(bundle.getInt(ExtraName.HandleMsg));

        switch (msgType) {
            case Toast:
                String data = bundle.getString(ExtraName.Text);
                boolean length = (bundle.getInt(ExtraName.Number, 1) == 1);
                Toast.makeText(_activity.getApplicationContext(), data, length ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
                break;
            case Dialog:
                showDialog(bundle);
                break;
//            case HideDialog:
//                hideDialog(bundle);
//                break;
        }
    }

    void showDialog(Bundle dlgData) {
        initAlertDlg();
        alertDialog.setTitle(dlgData.getString(ExtraName.Title));
        alertDialog.setMessage(dlgData.getString(ExtraName.Text));
        alertDialog.show();
    }

    void initAlertDlg() {
        alertDialog = new AlertDialog.Builder(_activity).create();
    }
}
