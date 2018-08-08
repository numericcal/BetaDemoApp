package com.numericcal.classifierdemo;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import io.fotoapparat.view.CameraView;
import io.reactivex.Completable;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;

import com.numericcal.edge.Dnn;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AS.Main";

    TextView statusText;
    TableLayout tableLayout;
    CameraView cameraView;

    RxPermissions rxPerm;
    Completable camPerm;

    ImageView overlayView;
    ImageView extraOverlay;

    ImageView dbgView;

    Dnn.Manager dnnManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = (TextView) findViewById(R.id.statusText);
        tableLayout = (TableLayout) findViewById(R.id.tableLayout);
        cameraView = (CameraView) findViewById(R.id.cameraView);

        rxPerm = new RxPermissions(this);
        camPerm = Camera.getPermission(this, rxPerm, statusText); // must run in onCreate, see RxPermissions

        overlayView = (ImageView) findViewById(R.id.overlayView);
        extraOverlay = (ImageView) findViewById(R.id.extraOverlay);

        dbgView = (ImageView) findViewById(R.id.dbgView);


    }

    @Override
    protected void onResume() {
        super.onResume();

        Observable<Bitmap> camFrames = Camera.getFeed(this,cameraView,camPerm);

        // set up numericcal DNN manager
        dnnManager = Dnn.createManager(getApplicationContext());

        // get a classifier
        Single<Dnn.Handle> objectDetector = dnnManager.createHandle(Dnn.configBuilder
                .fromAccount("beta_test_user")
                .withAuthToken("8333f43193c833ee3e478972dbb0afa3")
                .getModel("tf_classifier_dep-by-beta_test_user"))
                // & display some info in the UI
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess(handle -> {
                    statusText.setText(handle.info.engine);
                });

        // prepare classifier
        Single<Observable<Tags.TTok<List<Classifier.ClassLabel>>>> objectLabels =
                Examples.MobileNetClassifier.classifyObjects(objectDetector, camFrames);

        Observable<Tags.TTok<Bitmap>> labelOverlay = objectLabels
                .flatMapObservable(labels -> {
                    return labels.compose(
                            Examples.MobileNetClassifier.drawLabels(
                                    extraOverlay.getWidth(), extraOverlay.getHeight()));
                });

        // finally display labels and timing info
        labelOverlay.compose(Utils.mkOT(Utils.lpfTT(0.5f)))
                .observeOn(AndroidSchedulers.mainThread())
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(this::updateUI, Throwable::printStackTrace );
    }



    private <T> void updateUI(Pair<Bitmap,List<Pair<String, Float>>> report) {
        extraOverlay.setImageResource(android.R.color.transparent);
        extraOverlay.setImageBitmap(report.first);
        int cnt = 0;
        tableLayout.removeAllViews();

        List<Pair<String,Float>> tbl = report.second;

        for (Pair<String, Float> p: tbl) {
            TableRow row = new TableRow(this);
            TableRow.LayoutParams lp = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(lp);

            TextView key = new TextView(this);
            key.setText(p.first);
            key.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            TableRow.LayoutParams keyLPs = new TableRow.LayoutParams();
            keyLPs.weight = 1.0f;
            key.setLayoutParams(keyLPs);

            TextView val = new TextView(this);
            val.setText(Long.valueOf(Math.round(p.second)).toString());
            val.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            TableRow.LayoutParams valLPs = new TableRow.LayoutParams();
            valLPs.weight = 1.0f;
            val.setLayoutParams(valLPs);

            row.addView(key);
            row.addView(val);

            tableLayout.addView(row, cnt);
            cnt += 1;
        }
    }

    @Override
    protected void onPause() {

        // for the demo, release all DNNs when not on top
        if (!isChangingConfigurations() && dnnManager != null) {
            Log.i(TAG, "seems to be going in background ...");
            dnnManager.release();
            dnnManager = null;
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }
}
