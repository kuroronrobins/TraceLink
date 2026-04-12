using System;
using Android.App;
using Android.OS;
using Android.Runtime;
using Android.Views;
using AndroidX.AppCompat.Widget;
using AndroidX.AppCompat.App;
using Google.Android.Material.FloatingActionButton;
using Google.Android.Material.Snackbar;
using unitechRFID_CSharp_Sample.fragment;
using Com.Unitech.Lib.Types;
using Com.Unitech.Lib.Reader;
using unitechRFID_CSharp_Sample.enums;
using Com.Unitech.Lib.Transport.Types;
using Android.Util;
using Com.Unitech.Lib.Reader.Params;
using Com.Unitech.Lib.Diagnositics;
using Android;
using System.Collections.Generic;
using AndroidX.Core.App;
using Android.Bluetooth;
using Android.Content;

namespace unitechRFID_CSharp_Sample
{
    [Activity(Label = "@string/app_name", Theme = "@style/AppTheme.NoActionBar", MainLauncher = true)]
    public class MainActivity : AppCompatActivity
    {
        static string TAG = typeof(MainActivity).Name;

        private static int REQUEST_PERMISSION_CODE = 1000;

        public BaseReader baseReader;
        public MainModel mainModel;
        [Obsolete]
        static MainHandler _handler = null;

        [Obsolete]
        static MainActivity instance;

        [Obsolete]
        protected override void OnCreate(Bundle savedInstanceState)
        {
            base.OnCreate(savedInstanceState);
            Xamarin.Essentials.Platform.Init(this, savedInstanceState);
            SetContentView(Resource.Layout.activity_main);

            mainModel = new MainModel();
            _handler = new MainHandler(this);
            instance = this;

            if (savedInstanceState == null)
            {
                SwitchFragment(FragmentType.Main);
            }

            Toolbar toolbar = FindViewById<Toolbar>(Resource.Id.toolbar);
            SetSupportActionBar(toolbar);

            CheckPermission();
        }

        protected override void OnStop()
        {
            if (baseReader != null)
            {
                if (baseReader.Action != ActionState.Stop)
                {
                    baseReader.RfidUhf.Stop();
                }
                baseReader.Disconnect();

                baseReader = null;
            }

            base.OnStop();
        }

        public override bool OnCreateOptionsMenu(IMenu menu)
        {
            MenuInflater.Inflate(Resource.Menu.menu_main, menu);
            return true;
        }

        [Obsolete]
        public override bool OnOptionsItemSelected(IMenuItem item)
        {
            // Handle action bar item clicks here. The action bar will
            // automatically handle clicks on the Home/Up button, so long
            // as you specify a parent activity in AndroidManifest.xml.
            int id = item.ItemId;

            //noinspection SimplifiableIfStatement
            if (id == Resource.Id.action_findDevice)
            {
                try
                {
                    AssertReader();
                }
                catch (Exception e)
                {
                    ShowToast(e.Message);
                    return true;
                }

                //Timeout value is from 0 to 255 and unit is 0.1 second
                FindDevice findDevice = new FindDevice(FindDeviceMode.VibrateBeep, 10);

                try
                {
                    baseReader.FindDevice = findDevice;
                }
                catch (ReaderException e)
                {
                    ShowToast(e.Code.ToString());
                }
                return true;
            }

            if (id == Resource.Id.action_readMode)
            {
                try
                {
                    AssertReader();
                }
                catch (Exception e)
                {
                    ShowToast(e.Message);
                    return true;
                }

                try
                {
                    ReadMode readMode = baseReader.ReadMode;

                    if (readMode == ReadMode.MultiRead)
                    {
                        baseReader.ReadMode = ReadMode.SingleRead;
                    }
                    else if (readMode == ReadMode.SingleRead)
                    {
                        baseReader.ReadMode = ReadMode.MultiRead;
                    }
                }
                catch (ReaderException e)
                {
                    ShowToast(e.Code.ToString());
                }

                return true;
            }

            if (id == Resource.Id.action_operatingMode)
            {
                try
                {
                    AssertReader();
                }
                catch (Exception e)
                {
                    ShowToast(e.Message);
                    return true;
                }

                try
                {
                    baseReader.OperatingMode = OperatingMode.Bthid;
                }
                catch (ReaderException e)
                {
                    ShowToast(e.Code.ToString());
                }
                return true;
            }

            if (id == Resource.Id.action_factoryReset)
            {
                try
                {
                    AssertReader();
                }
                catch (Exception e)
                {
                    ShowToast(e.Message);
                    return true;
                }

                try
                {
                    baseReader.FactoryReset();
                }
                catch (ReaderException e)
                {
                    ShowToast(e.Code.ToString());
                }
                return true;
            }

            return base.OnOptionsItemSelected(item);
        }

        [Obsolete]
        public override void OnBackPressed()
        {
            var fragmentsarry = FragmentManager.Fragments;

            foreach (var fragment in fragmentsarry)
            {
                if (fragment.IsVisible)
                {
                    if (fragment.GetType().Equals(typeof(MainFragment)))
                    {
                        base.OnBackPressed();
                    }
                    else
                    {
                        SwitchFragment(FragmentType.Main);
                    }
                }
            }
        }

        public override void OnRequestPermissionsResult(int requestCode, string[] permissions, [GeneratedEnum] Android.Content.PM.Permission[] grantResults)
        {
            Xamarin.Essentials.Platform.OnRequestPermissionsResult(requestCode, permissions, grantResults);

            base.OnRequestPermissionsResult(requestCode, permissions, grantResults);

            Log.Debug(TAG, "Get request permissions result: " + REQUEST_PERMISSION_CODE);

            if (requestCode != REQUEST_PERMISSION_CODE)
            {
                return;
            }

            bool result = true;
            foreach (int grantResult in grantResults)
            {
                result &= (grantResult != (int)Android.Content.PM.Permission.Denied);
            }
            onPermissionResult(result);
        }

        private void CheckPermission()
        {
            string[] permissionList = new string[]
            {
                Manifest.Permission.AccessFineLocation,
                Manifest.Permission.AccessCoarseLocation
            };

            List<string> permissions = new List<string>();

            foreach (string s in permissionList)
            {
                if (CheckSelfPermission(s) == Android.Content.PM.Permission.Denied)
                {
                    permissions.Add(s);
                }
            }

            if (permissions.Count == 0)
            {
                Log.Debug(TAG, "No permission need to access");
                checkBT();
            }
            else
            {
                askPermission(permissions);
            }
        }

        private void askPermission(List<string> permissions)
        {
            if (permissions.Count <= 0)
            {
                onPermissionResult(true);
            }
            else
            {
                string[] requestPermissions = new string[permissions.Count];

                
                ActivityCompat.RequestPermissions(this, permissions.ToArray(), REQUEST_PERMISSION_CODE);
            }
        }

        /// <summary>
        /// Do the thing after check permission
        /// </summary>
        /// <param name="result">The check permission result</param>
        private void onPermissionResult(bool result)
        {
            if (result)
            {
                checkBT();
            }
            else
            {
                Log.Error(TAG, "Reject the permission request, close app");
                Finish();
            }
        }

        private void checkBT()
        {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.DefaultAdapter;
            if (bluetoothAdapter.IsEnabled)
            {
                Log.Debug(TAG, "Bluetooth enable");
            }
            else
            {
                bluetoothAdapter.Enable();
            }
        }

        [Obsolete]
        public void SwitchFragment(FragmentType fragmentType)
        {
            FragmentTransaction transaction = FragmentManager.BeginTransaction();

            Fragment fragment;

            switch (fragmentType)
            {
                case FragmentType.None:
                case FragmentType.Main:
                default:
                    fragment = new MainFragment(this);
                    break;
                case FragmentType.Sample:
                    fragment = new SampleFragment(this);
                    break;
                case FragmentType.Bluetooth:
                    fragment = new BluetoothFragment(this);
                    break;
            }

            Log.Debug(TAG, "Change fragment to " + fragment);

            transaction.Replace(Resource.Id.content_fragment, fragment, fragment.Tag);
            transaction.Commit();
        }

        [Obsolete]
        public BaseFragment GetFragment(FragmentType fragmentType)
        {
            var fragmentsarry = FragmentManager.Fragments;

            Type type;

            switch (fragmentType)
            {
                case FragmentType.Main:
                    type = typeof(MainFragment);
                    break;
                case FragmentType.Sample:
                    type = typeof(SampleFragment);
                    break;
                case FragmentType.Bluetooth:
                    type = typeof(BluetoothFragment);
                    break;
                case FragmentType.None:
                default:
                    return null;
            }


            foreach (var fragment in fragmentsarry)
            {
                if (fragment.GetType().Equals(type))
                {
                    return (BaseFragment)fragment;
                }
            }

            return null;
        }

        [Obsolete]
        public static MainActivity getInstance()
        {
            return instance;
        }

        [Obsolete]
        public static void ShowToast(string msg, bool lengthLong)
        {
            //try
            //{
            //    assertHandler();
            //}
            //catch (Exception e)
            //{
            //    Logger.error(e.toString());
            //    return;
            //}

            Message handlerMsg = new Message();

            handlerMsg.What = ((int)FragmentType.None);

            Bundle bundle = new Bundle();

            bundle.PutInt(ExtraName.HandleMsg, (int)HandlerMsg.Toast);
            bundle.PutString(ExtraName.Text, msg);
            bundle.PutInt(ExtraName.Number, lengthLong ? 1 : 0);

            handlerMsg.Data = bundle;

            _handler.SendMessage(handlerMsg);
        }

        [Obsolete]
        public static void ShowToast(string msg)
        {
            ShowToast(msg, true);
        }

        [Obsolete]
        public static void ShowDialog(string title, string msg)
        {
            try
            {
                AssertHandler();
            }
            catch (Exception e)
            {
                Log.Error(TAG, e.Message);
                return;
            }

            Message handlerMsg = new Message();

            Bundle data = new Bundle();

            data.PutString(ExtraName.Title, title);
            data.PutString(ExtraName.Text, msg);
            data.PutInt(ExtraName.HandleMsg, (int)HandlerMsg.Dialog);

            handlerMsg.What = (int)FragmentType.None;
            handlerMsg.Data = data;
            _handler.SendMessage(handlerMsg);
        }

        [Obsolete]
        public static void TriggerHandler(FragmentType fragmentType, Bundle bundle)
        {
            try
            {
                AssertHandler();
            }
            catch (Exception e)
            {
                Log.Error(TAG, e.Message);
                return;
            }

            Message handlerMessage = new Message();

            handlerMessage.What = (int)fragmentType;
            handlerMessage.Data = bundle;

            _handler.SendMessage(handlerMessage);
        }

        public void AssertReader()
        {
            if (baseReader == null)
            {
                throw new Exception("Reader is not ready");
            }
            else if (baseReader.State != ConnectState.Connected)
            {
                throw new Exception("Reader is not connected");
            }
        }

        [Obsolete]
        public static void AssertHandler()
        {
            if (_handler == null)
            {
                throw new Exception("Handler is not ready");
            }
        }
    }
}
