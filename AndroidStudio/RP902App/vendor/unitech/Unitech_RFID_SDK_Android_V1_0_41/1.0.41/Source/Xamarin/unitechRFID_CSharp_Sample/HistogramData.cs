namespace unitechRFID_CSharp_Sample
{
    public class HistogramData
    {

        public int size;        // 總格數
        public int value;       // 幾格
        public string name;     // 數值

        public HistogramData(int size)
        {
            this.size = size;
        }

        public void setData(int value, string name)
        {
            this.value = value;
            this.name = name;
        }
    }
}