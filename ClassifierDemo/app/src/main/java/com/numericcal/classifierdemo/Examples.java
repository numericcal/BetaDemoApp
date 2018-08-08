package com.numericcal.classifierdemo;

import android.graphics.Bitmap;

import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.functions.Function;


import com.numericcal.edge.Dnn;

import org.json.JSONObject;

import java.util.List;

/**
 * Pre-configured ObservableTransformers for convenience.
 */
public class Examples {

    // this is to filter out input when the classifier is busy running
    private static boolean chainRunning = false;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private static final int TOP_LABELS = 3;

    public static class MobileNetClassifier {
        private static final String TAG = "Ex.Classifier";

        public static Single<Observable<Tags.TTok<List<Classifier.ClassLabel>>>> classifyObjects(
                Single<Dnn.Handle> classifier, Observable<Bitmap> inStream) {

            return classifier.map(handle -> {
                chainRunning = false;
                int dnnInputWidth = handle.info.inputShape.get(2);
                int dnnInputHeight = handle.info.inputShape.get(1);

                int outputLen = handle.info.outputShape.get(1);

                Classifier.ModelParams mp = new Classifier.ModelParams(handle);

                Observable<Tags.TTok<List<Classifier.ClassLabel>>> stream = inStream
                        // add thread/entry/exit time tagging
                        .map(Tags.srcTag("source"))
                        .filter(__->(!chainRunning))
                        .map(setRunningFlag(true))
                        .observeOn(Schedulers.computation())
                        // resize bitmap to fit the DNN input tensor
                        .compose(scaleTT(dnnInputWidth, dnnInputHeight))
                        .observeOn(Schedulers.computation())
                        // normalize and lay out in memory
                        .compose(classifierFloatPrep())
                        .compose(handle.runInference(Tags.extract(), Tags.combine("classifier")))
                        // extract the top N labels
                        .compose(extractTopNClasses(TOP_LABELS, mp.labels,outputLen))
                        .map(setRunningFlag(false));

                return stream;

            });
        }
        public static <T> Function<Tags.TTok<T>,Tags.TTok<T>> setRunningFlag(boolean flag)
        {
            return src ->
            {
                chainRunning = flag;
                return src;
            };
        }


        public static ObservableTransformer<Tags.TTok<Bitmap>, Tags.TTok<Bitmap>>
        scaleTT(int width, int height) {
            return Utils.mkOT(Camera.scaleTo(width, height), Tags.extract(), Tags.combine("scaling"));
        }


        public static ObservableTransformer<Tags.TTok<Bitmap>, Tags.TTok<float[]>>
        classifierFloatPrep() {
            return Utils.mkOT(Utils.bmpToFloat_HWC_RGB(IMAGE_MEAN, IMAGE_STD), Tags.extract(), Tags.combine("bmp2float"));
        }

        public static ObservableTransformer<Tags.TTok<float[]>, Tags.TTok<List<Classifier.ClassLabel>>>
        extractTopNClasses(int topN, List<String> labels, int outputLen) {
            return Utils.mkOT(Classifier.findMostLikelyClasses(topN, labels, outputLen), Tags.extract(), Tags.combine("extractTopN"));
        }



        public static ObservableTransformer<Tags.TTok<List<Classifier.ClassLabel>>, Tags.TTok<Bitmap>>
        drawLabels(int w, int h) {
            return Utils.mkOT(Classifier.displayLabels(w, h), Tags.extract(), Tags.combine("labeling"));
        }

    }

}
