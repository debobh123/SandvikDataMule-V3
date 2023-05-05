package com.artclave.sandvikdatamule.gui.log;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.artclave.sandvikdatamule.R;

import java.text.SimpleDateFormat;

/**
 * Created by JuhaM on 20.1.2017.
 */
public class TransferLogListView extends BaseAdapter {
    private static LayoutInflater inflater = null;

    TransferLogControllerData data;

    public TransferLogListView(Context context) {
        inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void setData(TransferLogControllerData data) {
        this.data = data;
    }

    @Override
    public int getCount() {
        return data.getItemCount();
    }

    @Override
    public Object getItem(int position) {
        return data.getItemByPos(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View vi = convertView;
        if (vi == null)
            vi = inflater.inflate(R.layout.logview_listitem, null);

        TransferLogItem item = data.getItemByPos(position);
        TextView filename = (TextView) vi.findViewById(R.id.logViewList_filenameLabel);
        if (item != null) {
            TextView machineSerial = (TextView) vi.findViewById(R.id.logViewList_machineIdLabel);
            TextView date = (TextView) vi.findViewById(R.id.logViewList_dateLabel);
            ImageView fileIcon = (ImageView) vi.findViewById(R.id.logViewList_fileStateIcon);

            switch (item.getState())
            {
                case Invalid:
                    break;
                case FtpDownload:
                case CloudUpload:
                    fileIcon.setImageResource(R.drawable.sendreceive_100x100);
                    break;
                case Received:
                    fileIcon.setImageResource(R.drawable.incoming_black_100x100);
                    break;
                case Uploaded:
                    fileIcon.setImageResource(R.drawable.outgoing_black_100x100);
                    break;
                case Failed:
                    fileIcon.setImageResource(R.drawable.keyboardfeedback_100x100);
                    break;
            }

            filename.setText(item.getFilename());
            machineSerial.setText(item.getMachineSerial());
            // format date to user's default locale.
            SimpleDateFormat dateFormat = new SimpleDateFormat();
            date.setText(dateFormat.format(item.getTimestamp()));
        }
        return vi;
    }

}
