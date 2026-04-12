using Android.App;
using Android.Content;
using Android.OS;
using Android.Runtime;
using Android.Views;
using Android.Widget;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using unitechRFID_CSharp_Sample.enums;
using unitechRFID_CSharp_Sample.fragment;

namespace unitechRFID_CSharp_Sample
{
    [Obsolete]
    public class MainHandler : Handler
    {
        private MainActivity _activity;
        private AlertDialog alertDialog;

        public MainHandler(MainActivity activity)
        {
            this._activity = activity;
        }

        public override void HandleMessage(Message msg)
        {
            base.HandleMessage(msg);

            FragmentType fragmentType = (FragmentType)msg.What;

            BaseFragment baseFragment = _activity.GetFragment(fragmentType);

            if (baseFragment != null)
            {
                baseFragment.ReceiveHandler(msg.Data);
            }
            else if (fragmentType == FragmentType.None)
            {
                HandlerProcess(msg.Data);
            }
        }

        private void HandlerProcess(Bundle bundle)
        {
            HandlerMsg msgType = (HandlerMsg)bundle.GetInt(ExtraName.HandleMsg);

            switch (msgType)
            {
                case HandlerMsg.Toast:
                    string data = bundle.GetString(ExtraName.Text);
                    bool length = (bundle.GetInt(ExtraName.Number, 1) == 1);
                    Toast.MakeText(_activity.ApplicationContext, data, length ? ToastLength.Long : ToastLength.Short).Show();
                    break;
                case HandlerMsg.Dialog:
                    ShowDialog(bundle);
                    break;
                    //            case HideDialog:
                    //                hideDialog(bundle);
                    //                break;
            }
        }

        void ShowDialog(Bundle dlgData)
        {
            InitAlertDlg();
            alertDialog.SetTitle(dlgData.GetString(ExtraName.Title));
            alertDialog.SetMessage(dlgData.GetString(ExtraName.Text));
            alertDialog.Show();
        }

        void InitAlertDlg()
        {
            alertDialog = new AlertDialog.Builder(_activity).Create();
        }
    }
}