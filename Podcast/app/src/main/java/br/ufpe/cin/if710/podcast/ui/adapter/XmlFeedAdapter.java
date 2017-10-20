package br.ufpe.cin.if710.podcast.ui.adapter;

import java.io.File;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import br.ufpe.cin.if710.podcast.R;
import br.ufpe.cin.if710.podcast.domain.ItemFeed;
import br.ufpe.cin.if710.podcast.service.DownloadService;
import br.ufpe.cin.if710.podcast.service.RssPlayerService;

public class XmlFeedAdapter extends ArrayAdapter<ItemFeed> {

    private int linkResource;
    private Intent playService;

    // btn_state[state]
    // state 0: Baixar | state 1: Baixando | state 2: Ouvir | state 3: Parar
    private int[] btn_state;

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
            case 0:
                holder.item_action.setText("Baixar");
                holder.item_action.setEnabled(true);
                break;
            case 1:
                holder.item_action.setText("Baixando");
                holder.item_action.setEnabled(false);
                break;
            case 2:
                holder.item_action.setText("Ouvir");
                holder.item_action.setEnabled(true);
                break;
            case 3:
                holder.item_action.setText("Parar");
                holder.item_action.setEnabled(true);
                break;
        }
        holder.item_action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (btn_state[position]) {
                    case 0:
                        // Next step
                        holder.item_action.setText("Baixando");
                        holder.item_action.setEnabled(false);
                        btn_state[position] = 1;

                        // Call download service
                        Intent downloadService = new Intent(getContext(), DownloadService.class);
                        downloadService.setData(Uri.parse(getItem(position).getDownloadLink()));
                        downloadService.putExtra("position", position);
                        getContext().startService(downloadService);
                        break;
                    case 2:
                        holder.item_action.setText("Parar");
                        holder.item_action.setEnabled(true);
                        btn_state[position] = 3;

                        // Get file uri in order to play
                        File path = Environment
                                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        Uri fileuri = Uri.parse(getItem(position).getDownloadLink());
                        File file = new File(path, fileuri.getLastPathSegment());

                        // Call player service
                        playService = new Intent(getContext(), RssPlayerService.class);
                        playService.putExtra("fileuri", Uri.fromFile(file).toString());
                        getContext().startService(playService);
                        break;
                    case 3:
                        holder.item_action.setText("Ouvir");
                        holder.item_action.setEnabled(true);
                        btn_state[position] = 2;

                        getContext().stopService(playService);
                        break;
                }
            }
        });
        return convertView;
    }

    public void resetButtonState(int position) {
        btn_state[position] = 2;
        this.notifyDataSetChanged();    // Notify adapter
    }
}