package com.unitech.unitechrfidsample;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Message;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.unitech.lib.diagnositics.ReaderException;
import com.unitech.lib.reader.BaseReader;
import com.unitech.lib.reader.params.FindDevice;
import com.unitech.lib.transport.types.ConnectState;
import com.unitech.lib.types.ActionState;
import com.unitech.lib.types.DeviceType;
import com.unitech.lib.types.FindDeviceMode;
import com.unitech.lib.types.OperatingMode;
import com.unitech.lib.types.ReadMode;
import com.unitech.lib.util.diagnotics.UniLog;
import com.unitech.unitechrfidsample.databinding.ActivityMainBinding;
import com.unitech.unitechrfidsample.enums.FragmentType;
import com.unitech.unitechrfidsample.enums.HandlerMsg;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

import org.tinylog.Logger;

public class MainActivity extends AppCompatActivity {
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    private static final int REQUEST_PERMISSION_CODE = 1000;

    private ActivityResultLauncher<Intent> bluetoothResultLauncher;

    NavController _navController;

    static MainHandler _handler = null;

    private static MainActivity instance;

    public BaseReader baseReader = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        File directoryForLogs = getExternalFilesDir(null);
        System.setProperty("tinylog.directory", directoryForLogs.getAbsolutePath());
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        _handler = new MainHandler(this);
        instance = this;

        setSupportActionBar(binding.toolbar);

        _navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(_navController.getGraph()).build();
//        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        bluetoothResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Logger.debug("Bluetooth enable");
                    } else {
                        Logger.error("Reject the bluetooth request, close app");
                        finish();
                    }
                });

        checkPermission();
    }

    @Override
    protected void onStop() {
        if (baseReader != null) {
            if (baseReader.getAction() != ActionState.Stop) {
                baseReader.getRfidUhf().stop();
            }
            baseReader.disconnect();

            baseReader = null;
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_findDevice) {
            try {
                assertReader();
            } catch (Exception e) {
                showToast(e.getMessage());
                return true;
            }

            //Timeout value is from 0 to 255 and unit is 0.1 second
            FindDevice findDevice = new FindDevice(FindDeviceMode.VibrateBeep, 10);

            try {
                baseReader.setFindDevice(findDevice);
            } catch (ReaderException e) {
                showToast(e.getCode().toString());
            }
            return true;
        }

        if (id == R.id.action_readMode) {
            try {
                assertReader();
            } catch (Exception e) {
                showToast(e.getMessage());
                return true;
            }

            try {
                ReadMode readMode = baseReader.getReadMode();

                if (readMode == ReadMode.MultiRead) {
                    baseReader.setReadMode(ReadMode.SingleRead);
                } else if (readMode == ReadMode.SingleRead) {
                    baseReader.setReadMode(ReadMode.MultiRead);
                }
            } catch (ReaderException e) {
                showToast(e.getCode().toString());
            }

            return true;
        }

        if (id == R.id.action_operatingMode) {
            try {
                assertReader();
            } catch (Exception e) {
                showToast(e.getMessage());
                return true;
            }

            try {
                baseReader.setOperatingMode(OperatingMode.BTHID);
            } catch (ReaderException e) {
                showToast(e.getCode().toString());
            }
            return true;
        }

        if (id == R.id.action_factoryReset) {
            try {
                assertReader();
            } catch (Exception e) {
                showToast(e.getMessage());
                return true;
            }

            try {
                baseReader.factoryReset();
            } catch (ReaderException e) {
                showToast(e.getCode().toString());
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (getFragment(R.id.SampleFragment) != null) {
            switchFragment(R.id.MainFragment);
        } else if (getFragment(R.id.MainFragment) != null) {

        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.debug("Get request permissions result: " + REQUEST_PERMISSION_CODE);

        if (requestCode != REQUEST_PERMISSION_CODE) {
            return;
        }

        boolean result = true;
        for (int grantResult : grantResults) {
            result &= (grantResult != PackageManager.PERMISSION_DENIED);
        }
        onPermissionResult(result);
    }

    private void checkPermission() {
        final String[] permissionList = new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
        };

        ArrayList<String> permissions = new ArrayList<>();
        for (String s : permissionList) {
            if (checkSelfPermission(s) == PackageManager.PERMISSION_DENIED) {
                permissions.add(s);
            }
        }

        if (permissions.size() == 0) {
            Logger.debug("No permission need to access");
            checkBT();
        } else {
            askPermission(permissions);
        }
    }

    private void askPermission(ArrayList<String> permissions) {
        if (permissions.size() <= 0) {
            onPermissionResult(true);
        } else {
            String[] requestPermissions = new String[permissions.size()];
            ActivityCompat.requestPermissions(this, permissions.toArray(requestPermissions), REQUEST_PERMISSION_CODE);
        }
    }

    /**
     * Do the thing after check permission
     *
     * @param result The check permission result
     */
    private void onPermissionResult(boolean result) {
        if (result) {
            checkBT();
        } else {
            Logger.error("Reject the permission request, close app");
            finish();
        }
    }

    private void checkBT() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter.isEnabled()) {
            Logger.debug("Bluetooth enable");
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            bluetoothResultLauncher.launch(enableBtIntent);
        }
    }

    public Fragment getFragment(int id) {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        if (id == navController.getCurrentDestination().getId()) {
            Fragment navCtrlFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
            return navCtrlFragment.getChildFragmentManager().getFragments().get(0);
        }

        return null;
    }

    public void switchFragment(int id) {
        _navController.navigate(id);
    }

    static void assertHandler() throws Exception {
        if (_handler == null) {
            throw new Exception("Handler is not ready");
        }
    }

    public void assertReader() throws Exception {
        if (baseReader == null) {
            throw new Exception("Reader is not ready");
        } else if (baseReader.getState() != ConnectState.Connected) {
            throw new Exception("Reader is not connected");
        }
    }

    public static MainActivity getInstance() {
        return instance;
    }

    public static void showToast(String msg, boolean lengthLong) {
        try {
            assertHandler();
        } catch (Exception e) {
            Logger.error(e.toString());
            return;
        }

        Message handlerMsg = new Message();

        handlerMsg.what = FragmentType.None.value();

        Bundle bundle = new Bundle();

        bundle.putInt(ExtraName.HandleMsg, HandlerMsg.Toast.value());
        bundle.putString(ExtraName.Text, msg);
        bundle.putInt(ExtraName.Number, lengthLong ? 1 : 0);

        handlerMsg.setData(bundle);

        _handler.sendMessage(handlerMsg);
    }

    public static void showToast(String msg) {
        showToast(msg, true);
    }

    public static void showDialog(String title, String msg) {
        try {
            assertHandler();
        } catch (Exception e) {
            Logger.error(e.toString());
            return;
        }

        Message handlerMsg = new Message();

        Bundle data = new Bundle();

        data.putString(ExtraName.Title, title);
        data.putString(ExtraName.Text, msg);
        data.putInt(ExtraName.HandleMsg, HandlerMsg.Dialog.value());

        handlerMsg.what = FragmentType.None.value();
        handlerMsg.setData(data);
        _handler.sendMessage(handlerMsg);
    }

    public static void triggerHandler(FragmentType fragmentType, Bundle bundle) {
        try {
            assertHandler();
        } catch (Exception e) {
            Logger.error(e.toString());
            return;
        }

        Message handlerMessage = new Message();

        handlerMessage.what = fragmentType.value();
        handlerMessage.setData(bundle);

        _handler.sendMessage(handlerMessage);
    }
}