package com.unitech.unitechrfidsample.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.unitech.lib.htx.HT730Reader;
import com.unitech.lib.types.DeviceType;
import com.unitech.unitechrfidsample.MainActivity;
import com.unitech.unitechrfidsample.R;
import com.unitech.unitechrfidsample.databinding.FragmentMainBinding;

import org.tinylog.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainFragment extends BaseFragment {

    private FragmentMainBinding binding;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        initFragment();

        binding = FragmentMainBinding.inflate(inflater, container, false);

        if (_activity.baseReader != null) {
            _activity.baseReader.disconnect();
            _activity.baseReader = null;
        }

        _viewModel.deviceType = DeviceType.Unknown;

        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonHt730.setOnClickListener(view1 -> {
            _viewModel.deviceType = DeviceType.HT730;
            _activity.switchFragment(R.id.SampleFragment);
        });

        binding.buttonRp902.setOnClickListener(view1 -> {
            _viewModel.deviceType = DeviceType.RP902;
            _activity.switchFragment(R.id.BluetoothFragment);
        });

        binding.buttonRg768.setOnClickListener(view1 -> {
            _viewModel.deviceType = DeviceType.RG768;
            _activity.switchFragment(R.id.SampleFragment);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void receiveHandler(Bundle bundle) {

    }
}