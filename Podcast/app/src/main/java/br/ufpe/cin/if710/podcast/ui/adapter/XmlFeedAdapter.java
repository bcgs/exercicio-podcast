package br.ufpe.cin.if710.podcast.ui.adapter;

import java.io.File;
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import br.ufpe.cin.if710.podcast.R;
import br.ufpe.cin.if710.podcast.domain.ItemFeed;
import br.ufpe.cin.if710.podcast.service.DownloadService;
import br.ufpe.cin.if710.podcast.service.RssPlayerService;

public class XmlFeedAdapter extends ArrayAdapter<ItemFeed> {
    public static int currentEpisode = -1;

    public static final int DOWNLOAD = 0;
    public final int DOWNLOADING = 1;
    public static final int LISTEN = 2;
    public final int PAUSE = 3;

    private int[] btn_state;

    private int linkResource;
    public boolean isBound;

    public RssPlayerService rssPlayer;

    public XmlFeedAdapter(Context context, int resource, List<ItemFeed> objects) {
        super(context, resource, objects);
        linkResource = resource;
        btn_state = new int[objects.size()];
    }

    /**
     * public abstract View getView (int position, View convertView, ViewGroup parent)
     * <p>
     * Added in API level 1
     * Get a View that displays the data at the specified position in the data set. You can either create a View manually or inflate it from an XML layout file. When the View is inflated, the parent View (GridView, ListView...) will apply default layout parameters unless you use inflate(int, android.view.ViewGroup, boolean) to specify a root view and to prevent attachment to the root.
     * <p>
     * Parameters
     * position	The position of the item within the adapter's data set of the item whose view we want.
     * convertView	The old view to reuse, if possible. Note: You should check that this view is non-null and of an appropriate type before using. If it is not possible to convert this view to display the correct data, this method can create a new view. Heterogeneous lists can specify their number of view types, so that this View is always of the right type (see getViewTypeCount() and getItemViewType(int)).
     * parent	The parent that this view will eventually be attached to
     * Returns
     * A View corresponding to the data at the specified position.
     */


	/*
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.itemlista, parent, false);
		TextView textView = (TextView) rowView.findViewById(R.id.item_title);
		textView.setText(items.get(position).getTitle());
	    return rowView;
	}
	/**/

    //http://developer.android.com/training/improving-layouts/smooth-scrolling.html#ViewHolder
    static class ViewHolder {
        TextView item_title;
        TextView item_date;
        TextView item_action;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;
        if (convertView == null) {
            convertView = View.inflate(getContext(), linkResource, null);
            holder = new ViewHolder();
            holder.item_title = convertView.findViewById(R.id.item_title);
            holder.item_date = convertView.findViewById(R.id.item_date);
            holder.item_action = convertView.findViewById(R.id.item_action);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.item_title.setText(getItem(position).getTitle());
        holder.item_date.setText(getItem(position).getPubDate());
        switch (btn_state[position]) {
            case DOWNLOAD:
                holder.item_action.setText("Baixar");
                holder.item_action.setEnabled(true);
                break;
            case DOWNLOADING:
                holder.item_action.setText("Baixando");
                holder.item_action.setEnabled(false);
                break;
            case LISTEN:
                holder.item_action.setText("Ouvir");
                holder.item_action.setEnabled(true);
                break;
            case PAUSE:
                holder.item_action.setText("Pausar");
                holder.item_action.setEnabled(true);
                break;
        }
        holder.item_action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (btn_state[position]) {
                    case DOWNLOAD:
                        // Next step
                        holder.item_action.setText("Baixando");
                        holder.item_action.setEnabled(false);
                        btn_state[position] = DOWNLOADING;

                        // Call download service
                        Intent downloadService = new Intent(getContext(), DownloadService.class);
                        downloadService.setData(Uri.parse(getItem(position).getDownloadLink()));
                        downloadService.putExtra("position", position);
                        getContext().startService(downloadService);
                        break;
                    case LISTEN:
                        holder.item_action.setText("Pausar");
                        holder.item_action.setEnabled(true);
                        btn_state[position] = PAUSE;

                        File path = Environment.
                                getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

                        // If there is not bound connection...
                        if (!isBound) {
                            Intent playerIntent = new Intent(getContext(), RssPlayerService.class);
                            isBound = getContext().bindService(playerIntent, sConn, Context.BIND_AUTO_CREATE);

                            Uri fileuri = Uri.parse(getItem(position).getDownloadLink());
                            File file = new File(path, fileuri.getLastPathSegment());
                            playEpisode(file);
                        } else {
                            if (position == currentEpisode) {
                                rssPlayer.play();
                            } else {
                                // Stop last episode and set button to 'Ouvir'.
                                if (currentEpisode != -1) {
                                    rssPlayer.stop();
                                    setButtonToState(LISTEN, currentEpisode);
                                }
                                // Play new episode
                                Uri fileuri = Uri.parse(getItem(position).getDownloadLink());
                                File file = new File(path, fileuri.getLastPathSegment());
                                playEpisode(file);
                            }
                        }
                        // Update current episode status
                        currentEpisode = position;
                        break;
                    case PAUSE:
                        holder.item_action.setText("Ouvir");
                        holder.item_action.setEnabled(true);
                        btn_state[position] = LISTEN;

                        rssPlayer.pause();
                        break;
                }
            }
        });
        return convertView;
    }

    /**
     * Use this to set a state for a particular button.
     * @param state It can be DOWNLOAD, DOWNLOADING, LISTEN or PAUSE.
     * @param position Specific button to be changed.
     */
    public void setButtonToState(int state, int position) {
        btn_state[position] = state;
        this.notifyDataSetChanged();    // Notify adapter
    }

    /**
     * Set bind connection using this ServiceConnection.
     */
    public ServiceConnection sConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rssPlayer = ((RssPlayerService.RssBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            rssPlayer = null;
            isBound = false;
        }
    };

    /**
     * Start RssPlayer.
     * @param file File location in order to get its uri.
     */
    private void playEpisode(File file) {
        Intent playService = new Intent(getContext(), RssPlayerService.class);
        playService.putExtra("fileuri", Uri.fromFile(file).toString());
        getContext().startService(playService);
    }

}