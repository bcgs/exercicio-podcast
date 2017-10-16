package br.ufpe.cin.if710.podcast.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import br.ufpe.cin.if710.podcast.R;

public class EpisodeDetailActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_episode_detail);

        TextView item_title = findViewById(R.id.item_title);
        TextView item_description = findViewById(R.id.item_description);
        TextView item_pubDate = findViewById(R.id.item_pubDate);
        TextView item_link = findViewById(R.id.item_link);
        TextView item_dlink = findViewById(R.id.item_dlink);

        Intent intent = getIntent();
        item_title.setText(intent.getStringExtra("item_title"));
        item_description.setText(intent.getStringExtra("item_description"));
        item_pubDate.setText(intent.getStringExtra("item_pubdate"));
        item_link.setText(intent.getStringExtra("item_link"));
        item_dlink.setText(intent.getStringExtra("item_dlink"));
    }
}