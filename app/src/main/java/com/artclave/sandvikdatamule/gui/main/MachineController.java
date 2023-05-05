package com.artclave.sandvikdatamule.gui.main;

import android.app.Activity;

import com.artclave.sandvikdatamule.storage.FileStorage;
import com.artclave.sandvikdatamule.storage.IFileStorageListener;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by JuhaM on 31.10.2016.
 */
public class MachineController implements IFileStorageListener {

    // maps machine serial numbers to MachineState objects
    private final HashMap<String, MachineState> machineStates;

    // stores machine serial numbers or IP addresses
    private final LinkedList<String> machineIds;

    private final HashMap<String, String> ipToSerial;

    private DataTransferListViewAdapter listViewAdapter;
    private Activity activity;

    public MachineController() {
        machineStates = new HashMap<String, MachineState>();
        machineIds = new LinkedList<String>();
        ipToSerial = new HashMap<String, String>();
        listViewAdapter = null;

        FileStorage.instance().addListener(this);
        FileStorage.instance().getNextOutboxFiles();
    }

    @Override
    public void onMachineMetaDataChanged(String machineSerial) {
        boolean refresh = false;
        MachineState m = null;

        synchronized (machineIds) {
            m = getMachinePriv(machineSerial);
            if (m == null) {
                m = new MachineState();
                m.setName(machineSerial);
                machineStates.put(machineSerial, m);
                machineIds.addFirst(machineSerial);
                refresh = true;
            }
        }

        int pending = FileStorage.instance().getPendingReportCount(machineSerial);
        if (pending != m.getReceivedFileCount()) {
            m.setReceivedFileCount(pending);
            refresh = true;
        }
        if (refresh) {
            refreshUI();
        }
    }

    public MachineState getMachine(String serial) {
        synchronized (machineIds) {
            return machineStates.get(serial);
        }
    }

    private MachineState getMachinePriv(String serial) {
        return machineStates.get(serial);
    }

    public String getMachineSerial(String ip) {
        synchronized (machineIds) {
            return ipToSerial.get(ip);
        }
    }

    private String getMachineSerialPriv(String ip) {
        return ipToSerial.get(ip);
    }

    public void setListViewAdapter(Activity activity, DataTransferListViewAdapter listViewAdapter) {
        this.activity = activity;
        this.listViewAdapter = listViewAdapter;
        synchronized (machineIds) {
            this.listViewAdapter.setData(new MachineControllerData(machineStates, machineIds));
        }
    }

    public boolean refreshUI() {
        boolean ret = false;
        if (activity != null && listViewAdapter != null) {
            try {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // update listview data set.
                        synchronized (machineIds) {
                            listViewAdapter.setData(new MachineControllerData(machineStates, machineIds));
                        }
                        listViewAdapter.notifyDataSetChanged();
                    }
                });
                ret = true;
            } catch (Throwable t) {
            }
        }
        return ret;
    }

    public MachineState addMachineIP(String ipAddress) {
        synchronized (machineIds) {
            MachineState machine = null;
            String serial = getMachineSerialPriv(ipAddress);
            if (serial == null) {
                machine = new MachineState();
                machine.setName(ipAddress);

                if (machineStates.put(ipAddress, machine) == null) {
                    machineIds.addFirst(ipAddress);
                }
            } else {
                machine = getMachinePriv(serial);
                raiseMachine(serial);
            }
            return machine;
        }
    }

    public MachineState associateMachineSerial(String ipAddress, String serial) {
        MachineState machine = null;
        synchronized (machineIds) {
            if (machineStates.containsKey(serial)) {
                machineStates.remove(ipAddress);
                machineIds.remove(ipAddress);

                // if this IP was associated to another machine, change it.
                // set connection state of overrided machine to Disconnected.
                String overrideSerial = ipToSerial.put(ipAddress, serial);
                MachineState overrideMachine = getMachinePriv(overrideSerial);
                if (overrideMachine != null) {
                    overrideMachine.setConnectionState(MachineState.ConnectionState.Disconnected);
                }

                raiseMachine(serial);
                machine = getMachinePriv(serial);
            } else if (machineStates.containsKey(ipAddress)) {
                machine = machineStates.remove(ipAddress);
                machineIds.remove(ipAddress);
                machine.setName(serial);
                machineStates.put(serial, machine);
                machineIds.addFirst(serial);
                ipToSerial.put(ipAddress, serial);
            }
            // that means new machine has IP that used to belong to another known machine.
            else {
                machine = new MachineState();
                machine.setName(serial);

                machineStates.put(serial, machine);
                machineIds.addFirst(serial);

                // set connection state of overrided machine to Disconnected.
                String overrideSerial = ipToSerial.put(ipAddress, serial);
                MachineState overrideMachine = getMachinePriv(overrideSerial);
                if (overrideMachine != null) {
                    overrideMachine.setConnectionState(MachineState.ConnectionState.Disconnected);
                }

            }
        }
        return machine;
    }


    private void raiseMachine(String id) {
        if (machineIds.remove(id)) {
            machineIds.addFirst(id);
        }
    }
}
