package br.ufpe.cin.if710.podcast.ui;

import android.app.Activity;
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
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

import br.ufpe.cin.if710.podcast.R;
import br.ufpe.cin.if710.podcast.db.PodcastProvider;
import br.ufpe.cin.if710.podcast.db.PodcastProviderContract;
import br.ufpe.cin.if710.podcast.domain.ItemFeed;
import br.ufpe.cin.if710.podcast.domain.XmlFeedParser;
import br.ufpe.cin.if710.podcast.service.DownloadService;
import br.ufpe.cin.if710.podcast.service.RssPlayerService;
import br.ufpe.cin.if710.podcast.ui.adapter.XmlFeedAdapter;

public class MainActivity extends Activity {
    private final String RSS_FEED = "http://leopoldomt.com/if710/fronteirasdaciencia.xml";
    private final String URI_FILE = "uris.pc";
    private final String LBDATE_KEY = "lBuildDate";
    private final String UPDATE_KEY = "update";
    private final String STATUS_KEY = "status";
    private final String SIZE_KEY = "listSize";

    public static SharedPreferences prefs;
    public static SharedPreferences.Editor prefsEditor;
    public static int[][] status;

    private String lastBuildDate;
    private boolean updated;
    private int listSize;

    private PodcastProvider provider;
    private XmlFeedAdapter adapter;
    private List<String> uris;
    private ListView items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        items = findViewById(R.id.items);

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

        uris = new ArrayList<>();
        readUriFile();

        if (hasInternetConnection()) {
            new DownloadXmlTask().execute(RSS_FEED);
            Toast.makeText(
                    this,
                    "Checking update...",
                    Toast.LENGTH_LONG
            ).show();
        } else
            new RestoreFeedList().execute();
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter(DownloadService.DOWNLOAD_COMPLETE);
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(onDownloadCompleteEvent, intentFilter);
    }

    @Override
    protected void onPause() {
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
            Intent playerIntent = new Intent(this, RssPlayerService.class);
            stopService(playerIntent);
        }
        super.onDestroy();
    }

    /**
     * Check if device is connected to the internet.
     * @return Connected or disconnected.
     */
    private boolean hasInternetConnection() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
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

        if (uris.size() != 0) {
            int i = 0;
            for (int j = 0; j < status.length; j++) {
                if (i >= uris.size()) break;
                if (status[j][0] != 0) {
                    adapter.setButtonToListen(j);
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
        String lPos = prefs.getString(RssPlayerService.PAUSE_KEY, "");

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
        Log.e("sBuilder1", sBuilder1.toString());
        Log.e("sBuilder2", sBuilder2.toString());
        prefsEditor.putString(STATUS_KEY, sBuilder1.toString());
        prefsEditor.putString(RssPlayerService.PAUSE_KEY, sBuilder2.toString());
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

            saveUriFile(dlink);

            // Update download button state
            int position = intent.getExtras().getInt("position");
            savePosition(position);
            adapter.setButtonToListen(position);
            Toast.makeText(getApplicationContext(),
                    "Download finalizado!",
                    Toast.LENGTH_SHORT
            ).show();
        }
    };

    private void saveUriFile(String input) {
        FileOutputStream fos = null;
        try {
            fos = openFileOutput(URI_FILE, MODE_APPEND);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (fos != null) {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(fos)));
            pw.write(input + '\n');
            pw.close();
        }
    }

    private void readUriFile() {
        uris.clear();
        try {
            FileInputStream fis = openFileInput(URI_FILE);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            for (String row; (row = br.readLine()) != null; ) {
                uris.add(row);
            }
            br.close();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }

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
                listSize = feed.size();
                prefsEditor.putInt(SIZE_KEY, listSize);
                prefsEditor.apply();
                Log.e("===>", "Saving...");
                try {
                    // Avoid multithreading concurrency
                    // Wait the thread to finish
                    new SaveFeedList().execute(feed).get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            else {
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
                if (hasDuplicate(item.getDownloadLink()))
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

        private boolean hasDuplicate(String dlink) {
            if(uris != null)
                for (int i = 0; i < uris.size(); ++i)
                    if (dlink.equals(uris.get(i)))
                        return true;
            return false;
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
                if (uris.size() != 0) {
                    int[][] aux = new int[listSize][2];
                    int i = 0;
                    for (int j = 0; j < status.length; j++) {
                        if (i >= uris.size()) break;
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
}