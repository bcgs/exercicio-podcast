package br.ufpe.cin.if710.podcast.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import br.ufpe.cin.if710.podcast.ui.MainActivity;
import br.ufpe.cin.if710.podcast.ui.adapter.XmlFeedAdapter;

public class RssPlayerService extends Service {
    private static final int NOTIFICATION_ID = 1;

    private final IBinder binder = new RssBinder();

    private MediaPlayer player;
    private Notification notification;

    private int id;
    private int cEpisode;
    private boolean isForeground;

    @Override
    public void onCreate() {
        super.onCreate();
        Intent musicActivityIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, musicActivityIntent, 0);
        notification = new Notification.Builder(getApplicationContext())
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentTitle("RssPlayer")
                .setContentText("Click to access Podcast")
                .setContentIntent(pendingIntent).build();

        // Starts as foreground state in order to get priority in memory
        // Avoids to be easily eliminated by the system.
        startForeground(NOTIFICATION_ID, notification);
        isForeground = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        cEpisode = XmlFeedAdapter.currentEpisode;

        Uri fileuri = Uri.parse(intent.getStringExtra("fileuri"));
        player = MediaPlayer.create(this, fileuri);
        if (player != null) {
            id = startId;
            player.setLooping(false);
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopForeground(true);
                    isForeground = false;
                    MainActivity.adapter.setButtonToState(
                            XmlFeedAdapter.DOWNLOAD, XmlFeedAdapter.currentEpisode
                    );
                }
            });
            play();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
            stopSelf(id);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void play() {
        if (player != null) {
            int cPos = MainActivity.status[cEpisode][1];
            if (cPos != 0)
                player.seekTo(cPos);
            player.start();

            if (!isForeground) {
                startForeground(id, notification);
                isForeground = true;
            }
        }
    }

    public void pause() {
        if (player != null) {
            player.pause();
            saveCurrentPosition();
        }
    }

    public void stop() {
        if (player != null) player.stop();
    }

    public class RssBinder extends Binder {
        public RssPlayerService getService() {
            return RssPlayerService.this;
        }
    }

    private void saveCurrentPosition() {
        StringBuilder sBuilder = new StringBuilder();
        MainActivity.status[cEpisode][1] = player.getCurrentPosition();
        for (int[] position : MainActivity.status)
            sBuilder.append(position[1]).append(",");
        //Log.e("Log pause", sBuilder.toString());
        MainActivity.prefsEditor.putString(MainActivity.PAUSE_KEY, sBuilder.toString());
        MainActivity.prefsEditor.apply();
    }
}
