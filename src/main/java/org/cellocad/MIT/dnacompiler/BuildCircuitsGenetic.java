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
