package br.ufpe.cin.if710.podcast.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import br.ufpe.cin.if710.podcast.R;
import br.ufpe.cin.if710.podcast.db.PodcastProvider;
import br.ufpe.cin.if710.podcast.db.PodcastProviderContract;
import br.ufpe.cin.if710.podcast.domain.ItemFeed;
import br.ufpe.cin.if710.podcast.domain.XmlFeedParser;
import br.ufpe.cin.if710.podcast.service.DownloadService;
import br.ufpe.cin.if710.podcast.ui.adapter.XmlFeedAdapter;


public class MainActivity extends Activity {

    private final String RSS_FEED = "http://leopoldomt.com/if710/fronteirasdaciencia.xml";

    private PodcastProvider provider;
    private ListView items;
    private boolean feedSaved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        provider = new PodcastProvider();
        // Para evitar null pointer exception
        // e garantir que o DB foi instanciado.
        provider.onCreate();

        items = findViewById(R.id.items);
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
        if (hasInternetConnection())
            new DownloadXmlTask().execute(RSS_FEED);
        else
            new RestoreFeedList().execute();

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
        XmlFeedAdapter adapter = (XmlFeedAdapter) items.getAdapter();
        if (adapter != null) adapter.clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        db.close();
    }

    /**
     * Check if device is connected to the internet.
     * @return Connected or disconnected.
     */
    private boolean hasInternetConnection() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * Display list of objects once it gets downloaded
     * or restored from DB.
     * @param feed List of feed item.
     */
    private void setLayout(List<ItemFeed> feed) {
        XmlFeedAdapter adapter = new XmlFeedAdapter(getApplicationContext(),
                R.layout.itemlista, feed);
        items.setAdapter(adapter);
        items.setTextFilterEnabled(true);
        items.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                XmlFeedAdapter adapter = (XmlFeedAdapter) parent.getAdapter();
                ItemFeed item = adapter.getItem(position);

                Intent episodeDetail = new Intent(getApplicationContext(), EpisodeDetailActivity.class);
                episodeDetail.putExtra("item_title", item.getTitle());
                episodeDetail.putExtra("item_description", item.getDescription());
                episodeDetail.putExtra("item_pubdate", item.getPubDate());
                episodeDetail.putExtra("item_link", item.getLink());
                episodeDetail.putExtra("item_dlink", item.getDownloadLink());
                startActivity(episodeDetail);
            }
        });
    }

    //TODO Opcional - pesquise outros meios de obter arquivos da internet
    private String getRssFeed(String feed) throws IOException {
        InputStream in = null;
        String rssFeed = "";
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
            int position = intent.getExtras().getInt("position");
            XmlFeedAdapter adapter = (XmlFeedAdapter) items.getAdapter();
            adapter.resetButtonState(position);
            print("Download finalizado!");
        }
    };

    private void print(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private class DownloadXmlTask extends AsyncTask<String, Void, List<ItemFeed>> {

        @Override
        protected List<ItemFeed> doInBackground(String... params) {
            List<ItemFeed> itemList = new ArrayList<>();
            try {
                itemList = XmlFeedParser.parse(getRssFeed(params[0]));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            }
            return itemList;
        }
        @Override
        protected void onPostExecute(List<ItemFeed> feed) {
            if (!feedSaved) {
                try {
                    // Avoid multithreading concurrency
                    // Wait the thread to finish
                    new SaveFeedList().execute(feed).get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                feedSaved = true;
            }
            setLayout(feed);
            print("Feed updated");
        }

    }

    private class SaveFeedList extends AsyncTask<List<ItemFeed>, Void, Void> {

        @Override
        protected Void doInBackground(List<ItemFeed>... items) {

            // Clear DB except downloaded files.
            String where = PodcastProviderContract.EPISODE_URI + " LIKE ?";
            String[] whereArgs = { "" };
            provider.delete(PodcastProviderContract.EPISODE_LIST_URI, where, whereArgs);

            // Refresh podcast list
            ContentValues values = new ContentValues();
            values.put(PodcastProviderContract.EPISODE_URI, "");
            for (ItemFeed item : items[0]) {
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
            print("Feed salvo");
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
            print("Feed restaurado");
        }

    }
}
