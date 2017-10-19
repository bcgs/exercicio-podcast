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
        return db != null;
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
        if (uriMatchesEpisodes(uri)) {
            return db.getReadableDatabase().query(
                    PodcastProviderContract.EPISODE_TABLE,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    sortOrder
            );
        }
        else throw new IllegalArgumentException("Uri invalida!");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (uriMatchesEpisodes(uri)) {
            Long id = db.getWritableDatabase().insert(
                    PodcastProviderContract.EPISODE_TABLE,
                    null,
                    values
            );
            return Uri.withAppendedPath(PodcastProviderContract.EPISODE_LIST_URI, Long.toString(id));
        }
        else throw new IllegalArgumentException("Uri invalida!");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (uriMatchesEpisodes(uri)) {
            return db.getWritableDatabase().delete(
                    PodcastProviderContract.EPISODE_TABLE,
                    selection,
                    selectionArgs
            );
        }
        else throw new IllegalArgumentException("Uri invalida!");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        if (uriMatchesEpisodes(uri)) {
            return db.getWritableDatabase().update(
                    PodcastProviderContract.EPISODE_TABLE,
                    values,
                    selection,
                    selectionArgs
            );
        }
        else throw new IllegalArgumentException("Uri invalida!");
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
