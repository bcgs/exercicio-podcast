package br.ufpe.cin.if710.podcast.db;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public class PodcastProvider extends ContentProvider {
    private PodcastDBHelper db;

    @Override
    public boolean onCreate() {
        db = PodcastDBHelper.getInstance(getContext());
        return true;
    }

    private boolean uriMatchesEpisodes(Uri uri) {
        return uri.equals(PodcastProviderContract.EPISODE_LIST_URI);
    }

    private boolean uriMatchesSpecificEpisode(Uri uri) {
        return uri.getLastPathSegment().matches("\\d+");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Cursor cursor;
        if (uriMatchesEpisodes(uri)) {
            cursor = db.getReadableDatabase().query(
                    PodcastProviderContract.EPISODE_TABLE,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    sortOrder
            );
            return cursor;
        }
        else throw new IllegalArgumentException("Uri invalida!");
    }

    // A PRINCIPIO SO SERA FORNECIDO QUERY E MIME TYPE.

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO: Implement this to handle requests to insert a new row.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO: Implement this to handle requests to delete one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO: Implement this to handle requests to update one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getType(Uri uri) {
        if (uriMatchesEpisodes(uri)) {
            return PodcastProviderContract.CONTENT_DIR_TYPE;
        }
        else if (uriMatchesSpecificEpisode(uri)) {
            return PodcastProviderContract.CONTENT_ITEM_TYPE;
        }
        else throw new IllegalArgumentException("Uri invalida!");
    }
}
