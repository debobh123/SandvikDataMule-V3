package com.artclave.sandvikdatamule.gui.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.artclave.sandvikdatamule.R;

/**
 * Created by JuhaM on 28.10.2016.
 */
public class DataTransferListViewAdapter extends BaseAdapter {


    MachineControllerData data;

    private static LayoutInflater inflater = null;

    public DataTransferListViewAdapter(Context context) {
        inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void setData(MachineControllerData data) {
        this.data = data;
    }

    @Override
    public int getCount() {
        return data.getCount();
    }

    @Override
    public Object getItem(int position) {
        return data.getMachineByPos(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View vi = convertView;
        if (vi == null)
            vi = inflater.inflate(R.layout.datatransfer_listitem, null);

        MachineState state = data.getMachineByPos(position);
        if (state == null) return null;

        // machine is hidden from list if no files and no connection.
        if (state.getConnectionState() == MachineState.ConnectionState.Disconnected &&
                state.getOutgoingFileCount() == 0 &&
                state.getReceivedFileCount() == 0)
        {
            vi.setVisibility(View.GONE);
            return vi;
        }

        vi.setVisibility(View.VISIBLE);
        TextView machineName = (TextView) vi.findViewById(R.id.dataTransferList_machineLabel);
        machineName.setText(state.getName());

        TextView pendingReportsCount = (TextView) vi.findViewById(R.id.dataTransferList_receivedFilesCount);
        pendingReportsCount.setText(String.valueOf(state.getReceivedFileCount()));

        LinearLayout receivedFilesRow = (LinearLayout) vi.findViewById(R.id.dataTransferList_receivedFilesRow);
        LinearLayout outgoingFilesRow = (LinearLayout) vi.findViewById(R.id.dataTransferList_outgoingFilesRow);

        // hide received files text if no received files.
        if (state.getReceivedFileCount() > 0)
        {
            receivedFilesRow.setVisibility(View.VISIBLE);
        }
        else
        {
            receivedFilesRow.setVisibility(View.GONE);
        }
        // hide outgoing files text if no outgoing files.
        if (state.getOutgoingFileCount() > 0)
        {
            outgoingFilesRow.setVisibility(View.VISIBLE);
        }
        else
        {
            outgoingFilesRow.setVisibility(View.GONE);
        }


        TextView sendingFilesLabel = (TextView) vi.findViewById(R.id.dataTransferList_sendingFilesLabel);
        TextView receivingFilesLabel = (TextView) vi.findViewById(R.id.dataTransferList_receivingFilesLabel);
        ImageView connStateIcon = (ImageView) vi.findViewById(R.id.dataTransferList_machineConnStateIcon);

        switch (state.getConnectionState())
        {
            case Disconnected:
                sendingFilesLabel.setVisibility(View.GONE);
                receivingFilesLabel.setVisibility(View.GONE);
                connStateIcon.setImageResource(R.drawable.wirelessconnectionstatus_1_100x100);
                connStateIcon.setVisibility(View.VISIBLE);
                break;
            case Idle:
                sendingFilesLabel.setVisibility(View.GONE);
                receivingFilesLabel.setVisibility(View.GONE);
                connStateIcon.setImageResource(R.drawable.wirelessconnectionstatus_5_100x100);
                connStateIcon.setVisibility(View.VISIBLE);
                break;
            case Receiving:
                sendingFilesLabel.setVisibility(View.GONE);
                receivingFilesLabel.setVisibility(View.VISIBLE);
                connStateIcon.setImageResource(R.drawable.sendreceive_100x100);
                connStateIcon.setVisibility(View.VISIBLE);
                break;
            case Sending:
                sendingFilesLabel.setVisibility(View.VISIBLE);
                receivingFilesLabel.setVisibility(View.GONE);
                connStateIcon.setImageResource(R.drawable.sendreceive_100x100);
                connStateIcon.setVisibility(View.VISIBLE);
                break;

        }

        return vi;
    }
}
