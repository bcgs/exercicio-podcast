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

    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    private String lBuildDateKey = "lBuildDate";
    private String lastBuildDate;
    private String updateKey = "update";
    private boolean updated;
    private String positionsKey = "positions";
    private int[] positions;
    private String sizeKey = "listSize";
    private int listSize;

    private PodcastProvider provider;
    private XmlFeedAdapter adapter;
    private List<String> uris;

    private ListView items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getPreferences(MODE_PRIVATE);
        prefsEditor = prefs.edit();
        lastBuildDate = prefs.getString(lBuildDateKey, "");
        updated = prefs.getBoolean(updateKey, false);
        listSize = prefs.getInt(sizeKey, 0);
        if (updated) positions = restorePositions();

        provider = new PodcastProvider();
        // Avoid null pointer exception
        // to make sure the DB was instantiated.
        provider.onCreate();

        uris = new ArrayList<>();
        readUriFile();

        items = findViewById(R.id.items);

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
        if (adapter != null && adapter.currentEpisode != -1) {
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
        super.onDestroy();
        if (isFinishing()) {
            Intent playerIntent = new Intent(this, RssPlayerService.class);
            stopService(playerIntent);
        }
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

        // Change downloaded episodes button state
        if (uris != null)
            for (int i = 0; i < positions.length; ++i)
                if(positions[i] != 0)
                    adapter.setButtonToListen(i);


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
        positions[pos] = 1;
        for (int i = 0; i < positions.length; ++i)
            sBuilder.append(positions[i]).append(",");
        prefsEditor.putString(positionsKey, sBuilder.toString());
        prefsEditor.apply();
    }

    /**
     * Restore button states of all the downloaded episodes.
     * @return Array with positions.
     */
    private int[] restorePositions() {
        if (listSize != 0) {
            int[] pos = new int[listSize];
            String positions = prefs.getString(positionsKey, "");
            StringTokenizer st = new StringTokenizer(positions, ",");
            for (int i = 0; i < pos.length; ++i)
                pos[i] = Integer.parseInt(st.nextToken());
            return pos;
        }
        return null;
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
                positions = new int[feed.size()];
                prefsEditor.putInt(sizeKey, feed.size());
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
            prefsEditor.putBoolean(updateKey, true);
            lastBuildDate = XmlFeedParser.lastBuildDate;
            prefsEditor.putString(lBuildDateKey, lastBuildDate);
            prefsEditor.apply();

            // Rearrange position of episodes already downloaded
            for (int i = 0; i < uris.size(); i++)
                positions[i] = 1;
            for (int j = uris.size(); j < (positions.length - uris.size()); j++)
                if (positions[j] != 0)
                    positions[j] = 0;

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
