package br.ufpe.cin.if710.podcast.ui;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
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

import br.ufpe.cin.if710.podcast.R;
import br.ufpe.cin.if710.podcast.db.PodcastDBHelper;
import br.ufpe.cin.if710.podcast.domain.ItemFeed;
import br.ufpe.cin.if710.podcast.domain.XmlFeedParser;
import br.ufpe.cin.if710.podcast.ui.adapter.XmlFeedAdapter;

public class MainActivity extends Activity {

    //ao fazer envio da resolucao, use este link no seu codigo!
    private final String RSS_FEED = "http://leopoldomt.com/if710/fronteirasdaciencia.xml";
    //TODO teste com outros links de podcast

    private PodcastDBHelper db;
    private ListView items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = PodcastDBHelper.getInstance(this);
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
    protected void onStop() {
        super.onStop();
        XmlFeedAdapter adapter = (XmlFeedAdapter) items.getAdapter();
        adapter.clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private boolean hasInternetConnection() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private class DownloadXmlTask extends AsyncTask<String, Void, List<ItemFeed>> {
        @Override
        protected void onPreExecute() {
            Toast.makeText(getApplicationContext(), "iniciando...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected List<ItemFeed> doInBackground(String... params) {
            List<ItemFeed> itemList = new ArrayList<>();
            try {
                itemList = XmlFeedParser.parse(getRssFeed(params[0]));
                // Refresh DB
                db.getWritableDatabase().delete(PodcastDBHelper.DATABASE_TABLE, null, null);
                new SaveFeedList().execute(itemList);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            }
            return itemList;
        }

        @Override
        protected void onPostExecute(List<ItemFeed> feed) {
            Toast.makeText(getApplicationContext(), "terminando...", Toast.LENGTH_SHORT).show();

            //Adapter Personalizado
            XmlFeedAdapter adapter = new XmlFeedAdapter(getApplicationContext(), R.layout.itemlista, feed);

            //atualizar o list view
            items.setAdapter(adapter);
            items.setTextFilterEnabled(true);
            items.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    XmlFeedAdapter adapter = (XmlFeedAdapter) parent.getAdapter();
                    ItemFeed item = adapter.getItem(position);

                    Intent intent = new Intent(getApplicationContext(), EpisodeDetailActivity.class);
                    intent.putExtra("item_title", item.getTitle());
                    intent.putExtra("item_description", item.getDescription());
                    intent.putExtra("item_pubdate", item.getPubDate());
                    intent.putExtra("item_link", item.getLink());
                    intent.putExtra("item_dlink", item.getDownloadLink());

                    startActivity(intent);
                }
            });
        }
    }

    private class SaveFeedList extends AsyncTask<List<ItemFeed>, Void, Void> {
        @Override
        protected Void doInBackground(List<ItemFeed>... items) {
            ContentValues values = new ContentValues();
            values.put(PodcastDBHelper.EPISODE_FILE_URI, "");
            for (ItemFeed item : items[0]) {
                values.put(PodcastDBHelper.EPISODE_TITLE, item.getTitle());
                values.put(PodcastDBHelper.EPISODE_DESC, item.getDescription());
                values.put(PodcastDBHelper.EPISODE_DATE, item.getPubDate());
                values.put(PodcastDBHelper.EPISODE_LINK, item.getLink());
                values.put(PodcastDBHelper.EPISODE_DOWNLOAD_LINK, item.getDownloadLink());
                db.getWritableDatabase().insert(
                        PodcastDBHelper.DATABASE_TABLE,
                        null,
                        values
                );
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Toast.makeText(getApplicationContext(), "Feed saved", Toast.LENGTH_SHORT).show();
        }
    }

    private class RestoreFeedList extends AsyncTask<Void, Void, Cursor> {
        @Override
        protected Cursor doInBackground(Void... voids) {
            Cursor cursor = db.getReadableDatabase().query(
                    PodcastDBHelper.DATABASE_TABLE,
                    PodcastDBHelper.columns,
                    null,
                    null,
                    null,
                    null,
                    PodcastDBHelper._ID
            );
            return cursor;
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            List<ItemFeed> list = new ArrayList<>();
            ItemFeed item;
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                item = new ItemFeed(
                        cursor.getString(cursor.getColumnIndex(PodcastDBHelper.EPISODE_TITLE)),
                        cursor.getString(cursor.getColumnIndex(PodcastDBHelper.EPISODE_DESC)),
                        cursor.getString(cursor.getColumnIndex(PodcastDBHelper.EPISODE_DATE)),
                        cursor.getString(cursor.getColumnIndex(PodcastDBHelper.EPISODE_LINK)),
                        cursor.getString(cursor.getColumnIndex(PodcastDBHelper.EPISODE_DOWNLOAD_LINK))
                );
                list.add(item);
            }
            XmlFeedAdapter adapter = new XmlFeedAdapter(getApplicationContext(), R.layout.itemlista, list);
            items.setAdapter(adapter);
            cursor.close();
            Toast.makeText(getApplicationContext(), "Feed restored", Toast.LENGTH_SHORT).show();
        }
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
}
