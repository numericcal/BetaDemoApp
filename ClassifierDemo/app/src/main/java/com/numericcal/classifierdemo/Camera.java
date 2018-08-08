package com.numericcal.classifierdemo;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import java.io.ByteArrayOutputStream;

import io.fotoapparat.Fotoapparat;
import io.fotoapparat.parameter.ScaleType;
import io.fotoapparat.preview.Frame;
import io.fotoapparat.view.CameraView;
import static io.fotoapparat.selector.LensPositionSelectorsKt.back;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.Function;
import io.reactivex.subjects.CompletableSubject;


/**
 * Set up and use camera streams.
 */
public class Camera {
    private static final String TAG = "AS.Camera";

    /**
     * RxPermissions seems to require calling this in onCreate.
     * @param act - calling activity
     * @param rxp - RxPermissions manager
     * @return We annoy the user until they give the permission. Complete when granted.
     */
    public static Completable getPermission(AppCompatActivity act, RxPermissions rxp, TextView statusText) {
        CompletableSubject sig = CompletableSubject.create();
        rxp
                .request(Manifest.permission.CAMERA)
                .map(grant -> { if (grant) return ""; else throw new Exception("");})
                .retry(5)
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(act)))
                .subscribe(__ -> sig.onComplete(),
                        thr -> statusText.setText("We need CAMERA permission! Please restart."));

        return sig;
    }

    /**
     * Set up preview and Frame stream using Fotoapparat. Close Fotoapparat instance on disposal.
     * @param act - activity
     * @param preview - Fotoapparat view
     * @param permission - completable obtaining CAMERA permission
     * @return a stream of frames grabbed by Fotoapparat
     */
    public static Observable<Bitmap> getFeed(
            AppCompatActivity act, CameraView preview, Completable permission) {

        Observable<Frame> obs = Observable.create(emitter -> {
            Fotoapparat fotoapparat = Fotoapparat
                    .with(act)
                    .into(preview)
                    .previewScaleType(ScaleType.CenterCrop)
                    .lensPosition(back())
                    .frameProcessor(emitter::onNext)
                    .build();
            fotoapparat.start();
            emitter.setCancellable(() -> {
                Log.i(TAG, "REMOVING CAMERA!");
                fotoapparat.stop();
            });
        });
        return permission
                .andThen(obs).compose(yuv2bmp())
                .compose(bmpRotate(90));
    }


    /**
     * Performs center crop. Does not check if sizes are reasonable.
     * @param w - desired width
     * @param h - desired height
     * @return return new bitmap flowable
     */
    public static Function<Bitmap, Bitmap> centerCropTo(int w, int h) {
        return bmp -> {
            int upper = (bmp.getHeight() - h)/2;
            int left = (bmp.getWidth() - w)/2;
            return Bitmap.createBitmap(bmp, left, upper, w, h);
        };
    }

    public static Function<Bitmap, Bitmap> scaleTo(int w, int h) {
        return bmp -> Bitmap.createScaledBitmap(bmp, w, h, true);
    }

    /**
     * Convert YUV NV21 to Bitmap. Fotoapparat will produce NV21 but we need Bitmap for DNN.
     * @return new bitmap flowable
     */
    public static ObservableTransformer<Frame, Bitmap> yuv2bmp() {
        return upstream ->
                upstream
                        .map((Frame f) -> {
                            int width = f.getSize().width;
                            int height = f.getSize().height;
                            YuvImage yuv = new YuvImage(f.getImage(), ImageFormat.NV21, width, height, null);
                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            yuv.compressToJpeg(new Rect(0, 0, width, height), 100, os);
                            byte[] jpegByteArray = os.toByteArray();
                            return BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);
                        });
    }

    /**
     * Simple bitmap rotation.
     * @param angle - clockwise angle to rotate.
     * @return rotated bitmap
     */
    public static ObservableTransformer<Bitmap, Bitmap> bmpRotate(float angle) {
        return upstream ->
                upstream
                        .map(bmp -> {
                            Matrix mat = new Matrix();
                            mat.postRotate(angle);
                            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mat, true);
                        });
    }





}
