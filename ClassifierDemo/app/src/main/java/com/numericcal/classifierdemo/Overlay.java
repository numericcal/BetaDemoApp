package com.numericcal.classifierdemo;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.List;

/**
 * Convenience functions for working with ImageView overlays.
 */
public class Overlay {

    public static void drawLabels(List<Classifier.ClassLabel> topNClasses, int color, Canvas canvas)
    {
        Paint paint = new Paint();

        paint.setColor(color);
        paint.setAntiAlias(true);
        paint.setTextSize(50.0f);
        int top = 52;
        for( Classifier.ClassLabel label : topNClasses )
        {
            canvas.drawText(String.format("%s @ %.2f", label.objectClass, label.confidence), 0, top, paint);
            top+=52;
        }

    }

}
