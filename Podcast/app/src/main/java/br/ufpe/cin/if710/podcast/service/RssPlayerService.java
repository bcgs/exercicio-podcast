package br.ufpe.cin.if710.podcast.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;

import br.ufpe.cin.if710.podcast.ui.MainActivity;

public class RssPlayerService extends Service {
    private static final int NOTIFICATION_ID = 1;
    private MediaPlayer player;
    private int id;

    @Override
    public void onCreate() {
        super.onCreate();

        Intent musicActivityIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, musicActivityIntent, 0);
        Notification notification = new Notification.Builder(getApplicationContext())
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentTitle("RssPlayer")
                .setContentText("Click to access Podcast")
                .setContentIntent(pendingIntent).build();

        // Starts as foreground state in order to get priority in memory
        // Avoids to be easily eliminated by the system.
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Uri fileuri = Uri.parse(intent.getStringExtra("fileuri"));
        player = MediaPlayer.create(this, fileuri);
        if (player != null) {
            id = startId;
            player.setLooping(false);
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopSelf(id);
                }
            });
            player.start();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.stop();
            player.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
