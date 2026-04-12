package com.unitech.unitechrfidsample.fragment;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.unitech.unitechrfidsample.MainActivity;
import com.unitech.unitechrfidsample.MainViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BaseFragment extends Fragment {
    protected MainActivity _activity;
    protected ExecutorService _connectExecutorService;

    protected MainViewModel _viewModel;

    void initFragment() {
        _activity = (MainActivity) requireActivity();

        _viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        _connectExecutorService = Executors.newFixedThreadPool(5);
    }

    /**
     * Receive request from handler
     *
     * @param bundle The request bundle
     */
    public abstract void receiveHandler(Bundle bundle);

    @Override
    public void onDestroy() {
        _connectExecutorService.shutdown();
        super.onDestroy();
    }
}
