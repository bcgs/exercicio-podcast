package br.ufpe.cin.if710.podcast.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadService extends IntentService {
    public static final String DOWNLOAD_COMPLETE = "br.ufpe.cin.if710.podcast.service.action.DOWNLOAD_COMPLETE";

    public DownloadService() {
        super("DownloadService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            // Downloaded file - path and filename
            File file = new File(path, intent.getData().getLastPathSegment());

            // Connection
            URL url = new URL(intent.getData().toString());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Setting up output stream
            FileOutputStream fos = new FileOutputStream(file.getPath());
            BufferedOutputStream bos = new BufferedOutputStream(fos);

            byte[] buffer = new byte[8192];
            InputStream is = connection.getInputStream();
            for (int len; (len = is.read(buffer)) != -1; ) {
                bos.write(buffer, 0, len);
            }

            // Force bos to write buffered output bytes out to fos
            bos.flush();

            // Ensure that data is physically written to device(disk)
            fos.getFD().sync();

            bos.close();
            connection.disconnect();

            int position = intent.getExtras().getInt("position");
            Intent broadcastIntent = new Intent(DOWNLOAD_COMPLETE);
            broadcastIntent.putExtra("position", position);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);

        } catch (IOException ie) {
            Log.e(getClass().getName(), "Exception durante download", ie);
        }
    }
}
