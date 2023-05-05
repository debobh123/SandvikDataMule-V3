package com.artclave.sandvikdatamule.gui.main;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by JuhaM on 2.3.2017.
 */
public class MachineControllerData {
    private HashMap<String, MachineState> machineStates;
    private LinkedList<String> machineIds;

    public MachineControllerData(HashMap<String, MachineState> machineStates, LinkedList<String> machineIds) {
        this.machineStates = new HashMap<>(machineStates);
        this.machineIds = new LinkedList<>(machineIds);
    }

    public int getCount() {
        return machineIds.size();
    }

    public MachineState getMachineByPos(int position) {
        MachineState state = machineStates.get(machineIds.get(position));
        return state;
    }
}
