package com.unitech.unitechrfidsample.fragment;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.navigation.fragment.NavHostFragment;

import com.unitech.lib.util.diagnotics.StringUtil;
import com.unitech.unitechrfidsample.MainActivity;
import com.unitech.unitechrfidsample.MainViewModel;
import com.unitech.unitechrfidsample.R;
import com.unitech.unitechrfidsample.databinding.DialogEditTextBinding;
import com.unitech.unitechrfidsample.databinding.FragmentBluetoothBinding;

import java.security.InvalidParameterException;

public class BluetoothFragment extends BaseFragment {

    private FragmentBluetoothBinding binding;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        initFragment();

        binding = FragmentBluetoothBinding.inflate(inflater, container, false);

        if (_activity.baseReader != null) {
            _activity.baseReader.disconnect();
            _activity.baseReader = null;
        }

        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.editBluetoothMACAddress.setOnClickListener(view1 -> {
            editTagDataDialog("Bluetooth MAC Address", binding.editBluetoothMACAddress.getText().toString(), binding.editBluetoothMACAddress);
        });

        binding.buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _viewModel.bluetoothMACAddress = binding.editBluetoothMACAddress.getText().toString();

                _activity.switchFragment(R.id.SampleFragment);
            }
        });

        binding.editBluetoothMACAddress.setText(_viewModel.bluetoothMACAddress);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void receiveHandler(Bundle bundle) {

    }


    private void editTagDataDialog(String title, String data, TextView editDataView) {
        View view = _activity.getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        DialogEditTextBinding dlgBinding = DialogEditTextBinding.bind(view);

        if (data != null) {
            dlgBinding.editData.setText(data);
        } else {
            dlgBinding.editData.setText(editDataView.getText().toString());
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(_activity)
                .setTitle(title)
                .setView(view)
                .setCancelable(true)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null);

        AlertDialog mAlertDialog = dialog.create();
        mAlertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(DialogInterface dialog) {

                Button b = mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                b.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        String newData = dlgBinding.editData.getText().toString();
                        String retData = "";
                        if (StringUtil.isNullOrEmpty(newData)) {
                            editDataView.setText(newData);
                            dialog.dismiss();
                        }

                        try {
                            retData = checkAddressFormat(newData);
                        } catch (Exception e) {
                            MainActivity.showToast("Bluetooth MAC address is invalid");
                            return;
                        }

                        editDataView.setText(retData);

                        _viewModel.bluetoothMACAddress = retData;

                        dialog.dismiss();
                    }
                });
            }
        });

        mAlertDialog.show();
    }

    String checkAddressFormat(String address) {
        if (StringUtil.isNullOrEmpty(address)) {
            throw new InvalidParameterException("address is null or empty");
        }

        if (address.length() == 17) {
            if (BluetoothAdapter.checkBluetoothAddress(address)) {
                return address.toUpperCase();
            } else {
                throw new InvalidParameterException("address is invalid");
            }
        } else if (address.length() == 12) {
            for (int i = 0; i < address.length(); i++) {
                try {
                    Integer.parseInt(address.substring(i, i + 1), 16);
                } catch (Exception e) {
                    throw new InvalidParameterException("address is invalid");
                }
            }
            StringBuilder tmp = new StringBuilder();

            for (int i = 0; i < 12; i += 2) {
                tmp.append(address.toUpperCase().substring(i, i + 2));

                if (i != 10) {
                    tmp.append(":");
                }
            }

            return tmp.toString();
        }

        throw new InvalidParameterException("address is invalid");
    }
}
