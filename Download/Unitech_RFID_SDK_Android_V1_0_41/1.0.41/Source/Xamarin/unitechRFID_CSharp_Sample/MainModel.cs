using Android.App;
using Android.Content;
using Android.OS;
using Android.Runtime;
using Android.Views;
using Android.Widget;
using AndroidX.Lifecycle;
using Com.Unitech.Lib.Types;

namespace unitechRFID_CSharp_Sample
{
    public class MainModel
    {
        public string bluetoothMACAddress = "";
        public DeviceType deviceType = DeviceType.Unknown;
    }
}