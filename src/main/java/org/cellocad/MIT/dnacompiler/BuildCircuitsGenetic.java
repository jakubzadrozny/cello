package org.cellocad.MIT.dnacompiler;


import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;

/***********************************************************************
 Synopsis    [ Template file for genetic algorithm assigning repressors to gates.]

 Some genetic approach, should be relatively good and fast.

 ***********************************************************************/


/**
 *
 */
public class BuildCircuitsGenetic extends BuildCircuits {


    /**
     * Constructor.  Search algorithm needs to know about:
     *
     * options:
     *   Search settings: some settings (don't know what yet),
     *   Score settings: noise margin, roadblocking, toxicity
     *
     * gate_library:
     *   Needs to know about the gate library in order to update response functions during assignment.
     *
     * roadblock:
     *   Needs to know which input and logic gate promoters are roadblocking
     *
     * @param options
     * @param gate_library
     * @param roadblock
     */
    public BuildCircuitsGenetic(Args options, GateLibrary gate_library, Roadblock roadblock) {
        super(options, gate_library, roadblock);
    }

    private boolean currentlyAssignedGroup(LogicCircuit lc, String group_name) {

        for (Gate g: lc.get_logic_gates()) {
            if (g.group.equals(group_name)) {
                return true;
            }
        }

        return false;
    }

    private void mutate(LogicCircuit lc, Gate g) {

        LinkedHashMap<String, ArrayList<Gate>> groups_of_type = get_gate_library().get_GATES_BY_GROUP().get(g.type);

        ArrayList<String> group_names = new ArrayList<String>(groups_of_type.keySet());

        Collections.shuffle(group_names);

        for (String group_name : group_names) {

            if (!currentlyAssignedGroup(lc, group_name)) {

                ArrayList<Gate> gates_of_group = new ArrayList<Gate>(groups_of_type.get(group_name));
                
                Collections.shuffle(gates_of_group);

                g.name = gates_of_group.get(0).name;
                g.group = group_name;

            }
        }
    }

    private LogicCircuit crossover(LogicCircuit lc1, LogicCircuit lc2) {

        Random generator = new Random();

        LogicCircuit child = new LogicCircuit(lc1);

        int cutoff_point = generator.nextInt(lc2.get_logic_gates().size());

        for (int i = cutoff_point; i < lc2.get_logic_gates().size(); i++) {

            Gate g_child = child.get_logic_gates().get(i);
            Gate g_parent = lc2.get_logic_gates().get(i);
            g_child.name = g_parent.name;
            g_child.group = g_parent.group;

        }

        for (int i = 0; i < child.get_logic_gates().size(); i++) {

            Gate g = child.get_logic_gates().get(i);

            String g_name = new String(g.name);
            String g_group = new String(g.group);
            g.name = "null";
            g.group = "null";

            if (currentlyAssignedGroup(child, g_group)) {
                mutate(child, g);
            }

            else {
                g.name = g_name;
                g.group = g_group;
            }
        }

        return child;

    }

    /**
     *  returns void, but sets the list of assigned circuits, which can be accessed through 'get_logic_circuits()'
     */
    @Override
    public void buildCircuits() {
        logger = Logger.getLogger(getThreadDependentLoggername());
        logger.info("Building circuits by a genetic algorithm");

        //Boom!
        System.exit(-1);
    }

    private Logger logger  = Logger.getLogger(getClass());
}
