package com.unitech.unitechrfidsample.enums;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import org.tinylog.Logger;

public class HistogramView extends View {
    private int w;
    private int h;
    private Paint paint = new Paint();
    private float testTextSize = 48f;
    private int[] colors = {
            Color.rgb(176, 23, 31),
            Color.rgb(227, 23, 13),
            Color.rgb(255, 0, 0),
            Color.rgb(255, 69, 0),
            Color.rgb(255, 97, 0),
            Color.rgb(255, 128, 0),
            Color.rgb(255, 153, 18),
            Color.rgb(255, 180, 100),
            Color.rgb(255, 215, 0),
            Color.rgb(255, 255, 0),
            Color.rgb(250, 255, 50),
            Color.rgb(245, 222, 179),
            Color.rgb(255, 235, 205),
            Color.rgb(250, 255, 240)
    };
    private HistogramData data = null;

    public HistogramView(Context context) {
        super(context);
    }

    public HistogramView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public HistogramView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint paint = new Paint();
        int lineWidth = 5;

        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(1);

        //w: 454, h: 65
        canvas.drawLine(1, (float) (h * 0.1), 1, h, paint);
        canvas.drawLine(1, h, (w - 1), h, paint);
        canvas.drawLine((w - 1), h, (w - 1), (float) (h * 0.1), paint);
        canvas.drawLine((w - 1), (float) (h * 0.1), 1, (float) (h * 0.1), paint);


        if (data == null) {
            return;
        }

        //float x = (float) (w * 0.5);
        float y = (float) (h * 0.55);
        //float perValueHeight = (float) (h * 0.9) / data.size;
        //float perValueWidth = (float) (w * 0.8) - lineWidth;
        float perValueHeight = (float) (h * 0.8) - lineWidth;;
        float perValueWidth = (float) (w * 0.9) / data.size;
        paint.setStrokeWidth(perValueWidth);
        paint.setStyle(Paint.Style.STROKE);
//        paint.setColor(Color.BLUE);
        //float y0, y1 = 0;
        float x0, x1 = 0;
        for (int i = 0; i < data.value; i++) {
            paint.setColor(Color.rgb(255, 128, 0));
            //y0 = h - ((float) lineWidth / 2) - (perValueHeight * i);
            //y1 = y0 - perValueHeight + lineWidth;
            //canvas.drawLine(x, y0, x, y1, paint);
            x0 = 1 + ((float) lineWidth / 2) + (perValueWidth * i);
            x1 = x0 + perValueWidth + lineWidth;
            canvas.drawLine(x0, y, x1, y, paint);

        }

        paint.setTextSize(testTextSize);
        Rect bounds = new Rect();
        paint.getTextBounds("-1000dbm", 0, 8, bounds);
        paint.setTextSize(testTextSize * perValueWidth / bounds.width());
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        //canvas.drawText(data.name, (float) (w * 0.1), y1 - lineWidth, paint);

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        this.w = w - 10;
        this.h = h - 10;
        //Logger.error("onSizeChanged w: " + this.w + ", h: " + this.h);
    }

    public void update(HistogramData data) {
        this.data = data;
        if (data == null) {
            return;
        }
        invalidate();
    }

}
