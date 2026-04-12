using Android.App;
using Android.Content;
using Android.OS;
using Android.Runtime;
using Android.Util;
using Android.Views;
using Android.Widget;
using Com.Unitech.Lib.Types;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using unitechRFID_CSharp_Sample.enums;

namespace unitechRFID_CSharp_Sample.fragment
{
    [Obsolete]
    public class MainFragment : BaseFragment
    {
        #region Button
        private Button buttonRP902;
        private Button buttonHT730;
        private Button buttonRG768;
        #endregion

        public MainFragment(MainActivity activity)
        {
            initFragment(activity);
            _activity.mainModel.deviceType = DeviceType.Unknown;
        }

        [Obsolete]
        public override void OnCreate(Bundle savedInstanceState)
        {
            base.OnCreate(savedInstanceState);

            // Create your fragment here
        }

        [Obsolete]
        public override View OnCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
        {
            // Use this to return your custom view for this Fragment
            return inflater.Inflate(Resource.Layout.fragment_main, container, false);

            //return base.OnCreateView(inflater, container, savedInstanceState);
        }

        [Obsolete]
        public override void OnViewCreated(View view, Bundle savedInstanceState)
        {
            base.OnViewCreated(view, savedInstanceState);

            FindViewById(view);

            SetButtonClick();

            if (_activity.baseReader != null)
            {
                _activity.baseReader.Disconnect();
                _activity.baseReader = null;
            }
        }

        public override void ReceiveHandler(Bundle bundle)
        {
        }

        /// <summary>
        /// Initial UI controler
        /// </summary>
        /// <param name="view"></param>
        private void FindViewById(View view) {
            buttonRP902 = view.FindViewById<Button>(Resource.Id.button_rp902);
            buttonHT730 = view.FindViewById<Button>(Resource.Id.button_ht730);
            buttonRG768 = view.FindViewById<Button>(Resource.Id.button_rg768);
        }

        private void SetButtonClick()
        {
            buttonRP902.Click += delegate
            {
                _activity.mainModel.deviceType = DeviceType.Rp902;
                _activity.SwitchFragment(FragmentType.Bluetooth);
            };

            buttonHT730.Click += delegate
            {
                _activity.mainModel.deviceType = DeviceType.Ht730;
                _activity.SwitchFragment(FragmentType.Sample);
            };

            buttonRG768.Click += delegate
            {
                _activity.mainModel.deviceType = DeviceType.Rg768;
                _activity.SwitchFragment(FragmentType.Sample);
            };
        }
    }
}