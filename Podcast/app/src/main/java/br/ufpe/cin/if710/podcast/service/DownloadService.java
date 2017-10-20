package br.ufpe.cin.if710.podcast.service;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
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
    private static final int PROGRESS_NOTIFICATION_ID = 1;

    public DownloadService() {
        super("DownloadService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        // Create download notification
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());

        try {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            // Downloaded file - path and filename
            File file = new File(path, intent.getData().getLastPathSegment());
            if (file.exists()) file.delete();

            // Connection
            URL url = new URL(intent.getData().toString());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            FileOutputStream fos = new FileOutputStream(file.getPath());
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            byte[] buffer = new byte[8192];
            InputStream is = connection.getInputStream();

            // Start notifying
            builder.setContentTitle(intent.getData().getLastPathSegment())
                    .setContentText("Downloading...")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);
            notificationManager.notify(PROGRESS_NOTIFICATION_ID, builder.build());

            for (int len; (len = is.read(buffer)) != -1; ) {
                bos.write(buffer, 0, len);
            }

            // Stop notifying
            builder.setContentText("Download concluído")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done);
            notificationManager.notify(PROGRESS_NOTIFICATION_ID, builder.build());

            // Force bos to write buffered output bytes out to fos
            bos.flush();

            // Ensure that data is physically written to device(disk)
            fos.getFD().sync();

            bos.close();
            connection.disconnect();

            // Send broadcast
            int position = intent.getExtras().getInt("position");
            Intent broadcastIntent = new Intent(DOWNLOAD_COMPLETE);
            broadcastIntent.putExtra("position", position);
            broadcastIntent.putExtra("downloadlink", intent.getData().toString());
            broadcastIntent.putExtra("uri", Uri.fromFile(file));
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);

        } catch (IOException ie) {
            Log.e(getClass().getName(), "Exception durante download", ie);
        }
    }
}
