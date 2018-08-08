package com.numericcal.classifierdemo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.Log;


import com.numericcal.edge.Dnn;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.functions.Function;

/**
 * Processing required to prepare inputs and interpret outputs from classifier.
 */
public class Classifier {

    private static final String TAG = "AS.classifier";

    public static class ModelParams {
        public List<String> labels;
        ModelParams(Dnn.Handle hdl) throws JSONException, IOException {
            JSONObject json = hdl.info.params;
            Log.wtf(TAG, json.toString());
            this.labels = Utils.loadLines(hdl.loadHyperParamFile(json.getString("labels")));
        }
    }



    /**
     * A class to represent a single class label.
     */
    public static class ClassLabel {

        float confidence;
        String objectClass;

        ClassLabel(float confidence, String objectClass) {
            this.confidence = confidence;
            this.objectClass = objectClass;
        }

        @Override
        /* we match the python code for debug simplicity */
        public String toString() {
            return "["+objectClass+":"+ confidence + "]";
        }
    }


    public static Function<float[], List<ClassLabel>> findMostLikelyClasses(int topN, List<String> labels, int probLen) {
        return tensor -> {
            List<ClassLabel> topLabels = new ArrayList<>();
            for(int k=0; k<topN; k++) {
                int maxPos = Utils.argmax(tensor,0,probLen);
                float curProb = tensor[maxPos];
                String className = labels.get(maxPos);
                tensor[maxPos] = 0.0f;

                topLabels.add(new ClassLabel(curProb,className));
            }
            return topLabels;
        };
    }





    /**
     * Draw labels on a bitmap to be overlaid on top of the camera frame stream.
     * @return
     */
    public static Function<List<ClassLabel>, Bitmap> displayLabels(int w, int h) {
        return topNLabels -> {
            Bitmap boxBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas boxCanvas = new Canvas(boxBmp);

            boxCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            Overlay.drawLabels(topNLabels, Color.GREEN, boxCanvas);

            return boxBmp;
        };
    }


}
