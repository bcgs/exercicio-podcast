package br.ufpe.cin.if710.podcast.ui;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import br.ufpe.cin.if710.podcast.R;
import br.ufpe.cin.if710.podcast.db.PodcastProvider;
import br.ufpe.cin.if710.podcast.db.PodcastProviderContract;
import br.ufpe.cin.if710.podcast.domain.ItemFeed;
import br.ufpe.cin.if710.podcast.domain.XmlFeedParser;
import br.ufpe.cin.if710.podcast.service.RssPlayerService;
import br.ufpe.cin.if710.podcast.ui.adapter.XmlFeedAdapter;

public class MainActivity extends Activity {
    public static final String DOWNLOAD_COMPLETE = "DOWNLOAD_COMPLETE";
    public static final String EPISODE_COMPLETE = "EPISODE_COMPLETE";
    public static final String PAUSE_KEY = "pause";

    private final String RSS_FEED = "http://leopoldomt.com/if710/fronteirasdaciencia.xml";
    private final String DLINKS_KEY = "dlinks";
    private final String LBDATE_KEY = "lBuildDate";
    private final String UPDATE_KEY = "update";
    private final String STATUS_KEY = "status";
    private final String SIZE_KEY = "listSize";

    public static XmlFeedAdapter adapter;
    public static SharedPreferences prefs;
    public static SharedPreferences.Editor prefsEditor;

    public static int[][] status;

    private PodcastProvider provider;
    private HashSet<String> dlinks;
    private ListView items;
    private Timer timer;

    private int listSize;
    private String lastBuildDate;
    private boolean updated, upCheckerOn, onForeground;

    private NotificationManager nManager;
    private ConnectivityManager connectivityManager;
    private NotificationCompat.Builder nBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        items = findViewById(R.id.items);

        connectivityManager = (ConnectivityManager)
                getSystemService(CONNECTIVITY_SERVICE);

        prefs = getPreferences(MODE_PRIVATE);
        prefsEditor = prefs.edit();
        lastBuildDate = prefs.getString(LBDATE_KEY, "");
        updated = prefs.getBoolean(UPDATE_KEY, false);
        listSize = prefs.getInt(SIZE_KEY, 0);

        restoreStatus();

        provider = new PodcastProvider();
        // Avoid null pointer exception
        // to make sure the DB was instantiated.
        provider.onCreate();

        if (hasInternetConnection()) {
            new DownloadXmlTask().execute(RSS_FEED);
            print("Checking update...", Toast.LENGTH_LONG);
        } else new RestoreFeedList().execute();

        nBuilder = buildNotification();

        setUpdateChecker();
        upCheckerOn = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this,SettingsActivity.class));
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (adapter != null && XmlFeedAdapter.currentEpisode != -1) {
            Intent playerIntent = new Intent(this, RssPlayerService.class);
            adapter.isBound = adapter.getContext().bindService(playerIntent,
                    adapter.sConn, BIND_AUTO_CREATE);
            adapter.isBound = true;
        }

        if (!upCheckerOn && hasInternetConnection()) {
            print("Updating...", Toast.LENGTH_SHORT);
            new DownloadXmlTask().execute(RSS_FEED);
            setUpdateChecker();
            upCheckerOn = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        onForeground = true;

        IntentFilter downloadFilter = new IntentFilter(DOWNLOAD_COMPLETE);
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(onDownloadCompleteEvent, downloadFilter);

        IntentFilter episodeFilter = new IntentFilter(EPISODE_COMPLETE);
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(onEpisodeCompleteEvent, episodeFilter);
    }

    @Override
    protected void onPause() {
        onForeground = false;

        super.onPause();
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(onDownloadCompleteEvent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (adapter.isBound) {
            adapter.getContext().unbindService(adapter.sConn);
            adapter.rssPlayer = null;
            adapter.isBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            stopService(new Intent(this, RssPlayerService.class));

            LocalBroadcastManager.getInstance(this)
                    .unregisterReceiver(onEpisodeCompleteEvent);

            // Prevent memory leak
            adapter = null;

            timer.cancel();
        }
        super.onDestroy();
    }

    /**
     * Notification to be sent when the app is on
     * background and a new feed update is available.
     * @return Notification ready to be send.
     */
    private NotificationCompat.Builder buildNotification() {
        nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Intent nIntent = new Intent(this, MainActivity.class);
        nIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        nIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, nIntent, 0
        );

        String ticker = "Update available";
        String title = "Podcast";
        String text = "New feed update available";

        return nBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setTicker(ticker)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent);
    }

    /**
     * Set an update checker.
     */
    private void setUpdateChecker() {
        timer = new Timer();
        TimerTask caller = new TimerTask() {
            @Override
            public void run() {
                if (hasInternetConnection()) {
                    new DownloadXmlTask().execute(RSS_FEED);
                }
            }
        };
        // 1800000 = 30 minutes
        timer.schedule(caller, 1800000, 1800000);
    }

    private void initializeDlinkList() {
        dlinks = new HashSet<>();
        readDlinks();
    }

    /**
     * Check if device is connected to the internet.
     * @return Connected or disconnected.
     */
    private boolean hasInternetConnection() {
        assert connectivityManager != null;
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * Display list of objects once it gets downloaded
     * or restored from DB.
     * @param feed List of feed item.
     */
    private void setLayout(List<ItemFeed> feed) {
        adapter = new XmlFeedAdapter(getApplicationContext(), R.layout.itemlista, feed);
        items.setAdapter(adapter);

        if (dlinks == null)
            initializeDlinkList();

        if (dlinks.size() != 0) {
            int i = 0;
            for (int j = 0; j < status.length; j++) {
                if (i >= dlinks.size()) break;
                if (status[j][0] != 0) {
                    adapter.setButtonToState(XmlFeedAdapter.LISTEN, j);
                    i++;
                }
            }
        }

        items.setTextFilterEnabled(true);
        items.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                XmlFeedAdapter adapter = (XmlFeedAdapter) parent.getAdapter();
                ItemFeed item = adapter.getItem(position);

                if (item != null) {
                    Intent episodeDetail = new Intent(getApplicationContext(), EpisodeDetailActivity.class);
                    episodeDetail.putExtra("item_title", item.getTitle());
                    episodeDetail.putExtra("item_description", item.getDescription());
                    episodeDetail.putExtra("item_pubdate", item.getPubDate());
                    episodeDetail.putExtra("item_link", item.getLink());
                    episodeDetail.putExtra("item_dlink", item.getDownloadLink());
                    startActivity(episodeDetail);
                }
            }
        });
    }

    /**
     * Save position of a downloaded episode so that its
     * state button will be saved after quit.
     * @param pos Position of the downloaded episode.
     */
    private void savePosition(int pos) {
        StringBuilder sBuilder = new StringBuilder();
        status[pos][0] = 1;
        for (int[] position : status)
            sBuilder.append(position[0]).append(",");
        prefsEditor.putString(STATUS_KEY, sBuilder.toString());
        prefsEditor.apply();
    }

    /**
     * Restore status of each episode like its button state
     * and if played the time in milliseconds it was paused.
     */
    private void restoreStatus() {
        String pos = prefs.getString(STATUS_KEY, "");
        String lPos = prefs.getString(PAUSE_KEY, "");

        if (!pos.equals("")) {

            // ! Important when the app run outta memory
            if (status == null)
                status = new int[listSize][2];

            StringTokenizer sT1 = new StringTokenizer(pos, ",");
            StringTokenizer sT2 = new StringTokenizer(lPos, ",");
            for (int i = 0; i < status.length; i++) {
                status[i][0] = Integer.parseInt(sT1.nextToken());
                status[i][1] = Integer.parseInt(sT2.nextToken());
            }
        }
    }

    /**
     * Save status of all episodes like its button state and
     * if played the time in milliseconds it was paused.
     */
    private void saveStatus() {
        StringBuilder sBuilder1 = new StringBuilder();
        StringBuilder sBuilder2 = new StringBuilder();

        for (int[] position : status) {
            sBuilder1.append(position[0]).append(",");
            sBuilder2.append(position[1]).append(",");
        }
        prefsEditor.putString(STATUS_KEY, sBuilder1.toString());
        prefsEditor.putString(PAUSE_KEY, sBuilder2.toString());
        prefsEditor.apply();
    }

    private void readDlinks() {
        String dlinks = prefs.getString(DLINKS_KEY, "");
        if (!dlinks.equals("")) {
            StringTokenizer sT = new StringTokenizer(dlinks, ",");
            while (sT.hasMoreTokens())
                this.dlinks.add(sT.nextToken());
        }
    }

    private void saveDlinks() {
        StringBuilder sBuilder = new StringBuilder();
        Iterator<String> it = dlinks.iterator();
        while (it.hasNext())
            sBuilder.append(it.next()).append(",");
        prefsEditor.putString(DLINKS_KEY, sBuilder.toString());
        prefsEditor.apply();
    }

    //TODO Opcional - pesquise outros meios de obter arquivos da internet
    private String getRssFeed(String feed) throws IOException {
        InputStream in = null;
        String rssFeed;
        try {
            URL url = new URL(feed);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            for (int count; (count = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, count);
            }
            byte[] response = out.toByteArray();
            rssFeed = new String(response, "UTF-8");
        } finally {
            if (in != null) {
                in.close();
            }
        }
        return rssFeed;
    }

    private BroadcastReceiver onDownloadCompleteEvent = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Update downloaded file URI to DB
            String dlink = intent.getStringExtra("downloadlink");
            Uri fileuri = (Uri) intent.getExtras().get("uri");
            if(fileuri != null)
                new UpdateUriTask().execute(dlink, fileuri.toString());

            dlinks.add(dlink);
            saveDlinks();

            // Update download button state
            int position = intent.getExtras().getInt("position");
            savePosition(position);
            adapter.setButtonToState(XmlFeedAdapter.LISTEN, position);
            print("Download finalizado!", Toast.LENGTH_SHORT);
        }
    };

    private BroadcastReceiver onEpisodeCompleteEvent = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String fileUri = intent.getStringExtra("fileUri");
            int position = intent.getIntExtra("position", -1);
            new RemoveEpisodeTask().execute(fileUri, String.valueOf(position));
        }
    };

    private class DownloadXmlTask extends AsyncTask<String, Void, List<ItemFeed>> {
        @Override
        protected List<ItemFeed> doInBackground(String... params) {
            List<ItemFeed> itemList = new ArrayList<>();
            try {
                itemList = XmlFeedParser.parse(getRssFeed(params[0]));
                if (!lastBuildDate.equals(XmlFeedParser.lastBuildDate)) {
                    updated = false;
                }
            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
            }
            return itemList;
        }

        @Override
        protected void onPostExecute(List<ItemFeed> feed) {
            if (!updated) {
                if (onForeground) {
                    listSize = feed.size();
                    prefsEditor.putInt(SIZE_KEY, listSize);
                    prefsEditor.apply();
                    initializeDlinkList();
                    Log.e("===>", "Saving...");
                    try {
                        // Avoid multithreading concurrency
                        // Wait the thread to finish
                        new SaveFeedList().execute(feed).get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                } else {
                    // Send notification
                    nManager.notify(1, nBuilder.build());
                    timer.cancel();
                    upCheckerOn = false;
                }
            } else {
                new RestoreFeedList().execute();
                Log.e("===>", "Already updated! Restoring...");
            }
        }
    }

    private class SaveFeedList extends AsyncTask<List<ItemFeed>, Void, Void> {
        @SafeVarargs
        @Override
        protected final Void doInBackground(List<ItemFeed>... items) {
            // Clear DB except downloaded files.
            String where = PodcastProviderContract.EPISODE_URI + " LIKE ?";
            String[] whereArgs = { "" };
            provider.delete(PodcastProviderContract.EPISODE_LIST_URI, where, whereArgs);

            // Refresh DB list
            ContentValues values = new ContentValues();
            values.put(PodcastProviderContract.EPISODE_URI, "");
            for (ItemFeed item : items[0]) {
                if (!dlinks.isEmpty() && dlinks.contains(item.getDownloadLink()))
                    continue;
                values.put(PodcastProviderContract.TITLE, item.getTitle());
                values.put(PodcastProviderContract.DESCRIPTION, item.getDescription());
                values.put(PodcastProviderContract.DATE, item.getPubDate());
                values.put(PodcastProviderContract.EPISODE_LINK, item.getLink());
                values.put(PodcastProviderContract.DOWNLOAD_LINK, item.getDownloadLink());
                provider.insert(PodcastProviderContract.EPISODE_LIST_URI, values);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            // Update SharedPreferences w/ lastBuildDate
            updated = true;
            prefsEditor.putBoolean(UPDATE_KEY, true);
            lastBuildDate = XmlFeedParser.lastBuildDate;
            prefsEditor.putString(LBDATE_KEY, lastBuildDate);
            prefsEditor.apply();

            // Rearrange position of episodes already downloaded
            if (status == null) {
                status = new int[listSize][2];
                saveStatus();
            } else {
                if (!dlinks.isEmpty()) {
                    int[][] aux = new int[listSize][2];
                    int i = 0;
                    for (int j = 0; j < listSize; j++) {
                        if (i >= dlinks.size()) break;
                        if (status[j][0] != 0) {
                            aux[i] = status[j];
                            i++;
                        }
                    }
                    status = aux;
                    saveStatus();
                }
            }
            new RestoreFeedList().execute();
            Log.e("Last build date", lastBuildDate);
            Log.e("===>", "Restoring...");
        }
    }

    private class RestoreFeedList extends AsyncTask<Void, Void, Cursor> {
        @Override
        protected Cursor doInBackground(Void... voids) {
            return provider.query(
                    PodcastProviderContract.EPISODE_LIST_URI,
                    PodcastProviderContract.ALL_COLUMNS,
                    null,
                    null,
                    PodcastProviderContract._ID
            );
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            List<ItemFeed> list = new ArrayList<>();
            ItemFeed item;
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                item = new ItemFeed(
                        cursor.getString(cursor.getColumnIndex(PodcastProviderContract.TITLE)),
                        cursor.getString(cursor.getColumnIndex(PodcastProviderContract.DESCRIPTION)),
                        cursor.getString(cursor.getColumnIndex(PodcastProviderContract.DATE)),
                        cursor.getString(cursor.getColumnIndex(PodcastProviderContract.EPISODE_LINK)),
                        cursor.getString(cursor.getColumnIndex(PodcastProviderContract.DOWNLOAD_LINK))
                );
                list.add(item);
            }
            setLayout(list);
            cursor.close();
        }
    }

    private class UpdateUriTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            String selection = PodcastProviderContract.DOWNLOAD_LINK + " LIKE ?";
            String[] selectionArgs = { params[0] };
            ContentValues values = new ContentValues();
            values.put(PodcastProviderContract.EPISODE_URI, params[1]);
            provider.update(
                    PodcastProviderContract.EPISODE_LIST_URI,
                    values,
                    selection,
                    selectionArgs
            );
            return null;
        }
    }

    private class RemoveEpisodeTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... strings) {

            String selection = PodcastProviderContract.EPISODE_URI + " = ?";
            String[] selectionArgs = { strings[0] };

            // Get the download_link
            String[] columns = { PodcastProviderContract.DOWNLOAD_LINK };
            Cursor cursor = provider.query(
                    PodcastProviderContract.EPISODE_LIST_URI,
                    columns,
                    selection,
                    selectionArgs,
                    null
            );

            // Remove it from HashSet so that
            // it won't be count as a download.
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                String dLink = cursor.getString(cursor.getColumnIndex(
                        PodcastProviderContract.DOWNLOAD_LINK)
                );
                dlinks.remove(dLink);
                saveDlinks();
            }
            cursor.close();

            // Remove from status
            int pos = Integer.parseInt(strings[1]);
            status[pos][0] = 0;
            status[pos][1] = 0;
            saveStatus();

            // Remove from ext. memory
            URI fileUri = URI.create(strings[0]);
            File file = new File(fileUri);
            if (file.delete())
                Log.e("RemoveEpisodeTask", "Episode removed.");

            // Update the existing file's Uri to DB so that
            // the file will be removed on the next update.
            ContentValues values = new ContentValues();
            values.put(PodcastProviderContract.EPISODE_URI, "");
            provider.update(
                    PodcastProviderContract.EPISODE_LIST_URI,
                    values,
                    selection,
                    selectionArgs
            );

            return null;
        }
    }

    private void print(String message, int duration) {
        Toast.makeText(this, message, duration).show();
    }
}