using Android.App;
using Android.Content;
using Android.OS;
using Android.Runtime;
using Android.Util;
using Android.Views;
using Android.Widget;
using AndroidX.Lifecycle;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using unitechRFID_CSharp_Sample.enums;

namespace unitechRFID_CSharp_Sample.fragment
{
    [Obsolete]
    public abstract class BaseFragment : Fragment
    {
        protected MainActivity _activity;

        protected void initFragment(MainActivity activity)
        {
            this._activity = activity;
        }
        
        /// <summary>
        /// Receive request from handler
        /// </summary>
        /// <param name="bundle">The request bundle</param>
        public abstract void ReceiveHandler(Bundle bundle);

        [Obsolete]
        public override void OnDestroy()
        {
            base.OnDestroy();
        }
    }
}