package com.artclave.sandvikdatamule.gui.log;

import java.util.ArrayList;

/**
 * Created by JuhaM on 2.3.2017.
 */
public class TransferLogControllerData {
    private ArrayList<TransferLogItem> sortedItems;

    public TransferLogControllerData(ArrayList<TransferLogItem> sorted) {
        sortedItems = new ArrayList<>(sorted);
    }

    public int getItemCount() {
        return sortedItems.size();
    }

    public TransferLogItem getItemByPos(int position) {
        if (position >= 0 && position < sortedItems.size()) {
            return sortedItems.get(position);
        }
        else {
            return null;
        }
    }

}
