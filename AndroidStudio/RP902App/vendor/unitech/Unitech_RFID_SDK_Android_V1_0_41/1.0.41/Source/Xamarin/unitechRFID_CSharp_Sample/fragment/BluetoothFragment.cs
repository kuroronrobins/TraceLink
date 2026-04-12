using Android.App;
using Android.Bluetooth;
using Android.Content;
using Android.OS;
using Android.Runtime;
using Android.Util;
using Android.Views;
using Android.Widget;
using Com.Unitech.Lib.Util.Diagnotics;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using unitechRFID_CSharp_Sample.enums;

namespace unitechRFID_CSharp_Sample.fragment
{
    [Obsolete]
    public class BluetoothFragment : BaseFragment
    {
        #region Button
        private Button buttonConnect;
        #endregion

        #region TextView
        private TextView editBluetoothMACAddress;
        #endregion

        public BluetoothFragment(MainActivity activity)
        {
            initFragment(activity);
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
            return inflater.Inflate(Resource.Layout.fragment_bluetooth, container, false);

            //return base.OnCreateView(inflater, container, savedInstanceState);
        }

        [Obsolete]
        public override void OnViewCreated(View view, Bundle savedInstanceState)
        {
            base.OnViewCreated(view, savedInstanceState);

            FindViewById(view);

            SetButtonClick();

            editBluetoothMACAddress.Text = _activity.mainModel.bluetoothMACAddress;

            if (_activity.baseReader != null)
            {
                _activity.baseReader.Disconnect();
                _activity.baseReader = null;
            }
        }

        /// <summary>
        /// Initial UI controler
        /// </summary>
        /// <param name="view"></param>
        private void FindViewById(View view)
        {
            #region Button
            buttonConnect = view.FindViewById<Button>(Resource.Id.button_connect);
            #endregion

            #region TextView
            editBluetoothMACAddress = view.FindViewById<TextView>(Resource.Id.edit_bluetoothMACAddress);
            #endregion
        }

        private void SetButtonClick()
        {
            buttonConnect.Click += delegate
            {
                _activity.mainModel.bluetoothMACAddress = editBluetoothMACAddress.Text;

                _activity.SwitchFragment(FragmentType.Sample);
            };

            editBluetoothMACAddress.Click += delegate
            {
                editTagDataDialog("Bluetooth MAC Address", editBluetoothMACAddress.Text, editBluetoothMACAddress);
            };
        }

        public override void ReceiveHandler(Bundle bundle)
        {
        }

        private void editTagDataDialog(string title, string data, TextView editDataView)
        {
            View view = _activity.LayoutInflater.Inflate(Resource.Layout.dialog_edit_text, null);

            EditText editData = view.FindViewById<EditText>(Resource.Id.edit_data);

            if (data != null)
            {
                editData.Text = data;
            }
            else
            {
                editData.Text = editDataView.Text;
            }

            AlertDialog.Builder dialog = new AlertDialog.Builder(_activity)
                    .SetTitle(title)
                    .SetView(view)
                    .SetCancelable(true);

            dialog.SetPositiveButton(Resource.String.ok, (senderAlert, args) =>
            {
                string newData = editData.Text;
                string retData = "";
                if (StringUtil.IsNullOrEmpty(newData))
                {
                    editDataView.Text = newData;
                    dialog.Dispose();
                }

                try
                {
                    retData = CheckAddressFormat(newData);
                }
                catch (Exception e)
                {
                    MainActivity.ShowToast("Bluetooth MAC address is invalid");
                    Log.Error(Tag, e.Message);
                    return;
                }

                editDataView.Text = retData;

                _activity.mainModel.bluetoothMACAddress = retData;

                dialog.Dispose();
            });


            dialog.SetNegativeButton(Resource.String.cancel, (IDialogInterfaceOnClickListener)null);

            AlertDialog mAlertDialog = dialog.Create();

            mAlertDialog.Show();
        }

        string CheckAddressFormat(string address)
        {
            if (StringUtil.IsNullOrEmpty(address))
            {
                throw new Exception("address is null or empty");
            }

            if (address.Length == 17)
            {
                if (BluetoothAdapter.CheckBluetoothAddress(address))
                {
                    return address.ToUpper();
                }
                else
                {
                    throw new Exception("address is invalid");
                }
            }
            else if (address.Length == 12)
            {
                for (int i = 0; i < address.Length; i++)
                {
                    try
                    {
                        int.Parse(address.Substring(i, 1), System.Globalization.NumberStyles.HexNumber);
                    }
                    catch (Exception e)
                    {
                        Log.Error(Tag, e.Message);
                        throw new Exception("address is invalid");
                    }
                }
                StringBuilder tmp = new StringBuilder();

                for (int i = 0; i < 12; i += 2)
                {
                    tmp.Append(address.ToUpper().Substring(i, 2));

                    if (i != 10)
                    {
                        tmp.Append(":");
                    }
                }

                return tmp.ToString();
            }

            throw new Exception("address is invalid");
        }
    }
}