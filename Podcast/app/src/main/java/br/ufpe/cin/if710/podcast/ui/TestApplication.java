package br.ufpe.cin.if710.podcast.ui;

import android.app.Application;

import com.frogermcs.androiddevmetrics.AndroidDevMetrics;
import com.squareup.leakcanary.LeakCanary;

import br.ufpe.cin.if710.podcast.BuildConfig;

public class TestApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        LeakCanary.install(this);

        if (BuildConfig.DEBUG) {
            AndroidDevMetrics.initWith(this);
        }
    }
}
