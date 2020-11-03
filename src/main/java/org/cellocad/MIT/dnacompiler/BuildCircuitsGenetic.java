package org.cellocad.MIT.dnacompiler;


import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Comparator;

/***********************************************************************
 Synopsis    [ Template file for genetic algorithm assigning repressors to gates.]

 Some genetic approach, should be relatively good and fast.

 ***********************************************************************/


/**
 *
 */
public class BuildCircuitsGenetic extends BuildCircuits {

    private static final int _MAX_ATTEMPTS = 10;
    private static final int _POPULATION_SIZE = 20;
    private static final int _NUM_EPOCHS = 20;

    private static final Double _MUTATION_PROB = 0.3;
    private static final Double _CROSSOVER_PROB = 0.9;
    private static final Double _GENE_MUTATION_PROB = 0.2;

    private static final String _MUTATION_TYPE = "plus";

    private Random generator;

    class Assignment implements Comparable<Assignment> {
        public ArrayList<String> gates;
        public Double score;

        public Assignment() {
            gates = new ArrayList();
        }

        public int compareTo(Assignment other) {
            Double diff = score - other.score;
            if (diff > 0.0) {
                return 1;
            } else if (diff < 0.0) {
                return -1;
            } else {
                return 0;
            }
        }
    }

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
        generator = new Random();
    }

    /**
     *  returns void, but sets the list of assigned circuits, which can be accessed through 'get_logic_circuits()'
     */
    @Override
    public void buildCircuits() {
        logger = Logger.getLogger(getThreadDependentLoggername());
        logger.info("Building circuits by a genetic algorithm");

        set_logic_circuits( new ArrayList<LogicCircuit>() );

        ArrayList<Assignment> population = new ArrayList();

        for (int i = 0; i < _POPULATION_SIZE; i++) {
            // We may have to catch an exception here
            Assignment solution = randomSolution();
            LogicCircuit lc = buildAndScoreCircuit(solution);

            solution.score = lc.get_scores().get_score();
            population.add(solution);
        }

        for (int epoch = 0; epoch < _NUM_EPOCHS; epoch++) {
            logger.info("Epoch " + String.valueOf(epoch + 1));

            ArrayList<Double> fitness = new ArrayList();
            Double min_score = Collections.min(population).score;
            for (Assignment ind : population) {
                fitness.add(ind.score - min_score);
            }

            ArrayList<Assignment> children = new ArrayList();

            for (int i = 0; i < _POPULATION_SIZE / 2; i++) {
                int parentAIdx = weightedDiscreteSample(fitness);
                int parentBIdx = weightedDiscreteSample(fitness);

                Assignment parentA = population.get(parentAIdx);
                Assignment parentB = population.get(parentBIdx);

                if (bernoulliSample(_CROSSOVER_PROB)) {
                    children.addAll(crossover(parentA, parentB));
                } else {
                    children.add(parentA);
                    children.add(parentB);
                }
            }

            // Just to avoid problems with odd population size
            // copy a random parent to the children population
            if (_POPULATION_SIZE % 2 == 1) {
              int parentIdx = weightedDiscreteSample(fitness);
              children.add(population.get(parentIdx));
            }

            for (int i = 0; i < _POPULATION_SIZE; i++) {
                Assignment child = children.get(i);

                if (bernoulliSample(_MUTATION_PROB)) {
                    child = mutate(child);
                }

                LogicCircuit lc = buildAndScoreCircuit(child);
                child.score = lc.get_scores().get_score();
                children.set(i, child);
            }

            if (_MUTATION_TYPE == "plus") {
                population.addAll(children);
                Collections.sort(population, Collections.reverseOrder());
                population.subList(_POPULATION_SIZE, population.size()).clear();
            } else {
                population = children;
            }

            double best_score = Collections.max(population).score;
            double worst_score = Collections.min(population).score;
            logger.info("Best score: " + String.valueOf(best_score));
            logger.info("Worst score: " + String.valueOf(worst_score));
            logger.info("  iteration " + String.format("%4s", epoch) + ": score = " + String.format("%6.2f", best_score));
        }

        for (int i = 0; i < _POPULATION_SIZE; i++) {
            Assignment solution = population.get(i);
            LogicCircuit lc = buildAndScoreCircuit(solution);
            get_logic_circuits().add(lc);
        }
    }

    private boolean bernoulliSample(Double p) {
        return generator.nextDouble() < p;
    }

    private int weightedDiscreteSample(ArrayList<Double> weights) {
        Double weights_sum = 0.0;
        for (Double w : weights) {
            weights_sum += w;
        }

        ArrayList<Double> CMF = new ArrayList();
        for (Double w : weights) {
            double prob;
            if (weights_sum > 0.0) {
                prob = w / weights_sum;
            } else {
                prob = 1.0 / weights.size();
            }

            if (CMF.isEmpty()) {
                CMF.add(prob);
            } else {
                CMF.add(CMF.get(CMF.size() - 1) + prob);
            }
        }

        Double r = generator.nextDouble();
        int k = Collections.binarySearch(CMF, r);
        // This can break if we randomly draw a float from the list, but...
        // The probability of this happening is almost 0
        return -k - 1;
    }

    private LogicCircuit buildAndScoreCircuit(Assignment solution) {
        LogicCircuit lc = get_unassigned_lc();
        LogicCircuitUtil.sortGatesByStage(lc);

        for(Gate g: lc.get_output_gates()) {
            Evaluate.refreshGateAttributes(g, get_gate_library());
        }

        for(int gi=0; gi<lc.get_logic_gates().size(); gi++) {
            String gate_name = solution.gates.get(gi);
            Gate g = lc.get_logic_gates().get(gi);
            g.name = gate_name;
        }

        Evaluate.evaluateCircuit(lc, get_gate_library(), get_options());
        Toxicity.evaluateCircuitToxicity(lc, get_gate_library());

        return lc;
    }

    private LogicCircuit initEmptyCircuit() {
        LogicCircuit lc = get_unassigned_lc();
        LogicCircuitUtil.sortGatesByStage(lc);

        for(Gate g: lc.get_output_gates()) {
            Evaluate.refreshGateAttributes(g, get_gate_library());
        }

        return lc;
    }

    private void assignGate(Gate gc, String name) {
        gc.name = name;
        Evaluate.refreshGateAttributes(gc, get_gate_library());
        gc.set_unvisited(true);

        Evaluate.simulateRPU(gc, get_gate_library(), get_options());
        Evaluate.evaluateGate(gc, get_options());
    }

    private Assignment mutate(Assignment parent) {
        int broken = 0;
        Assignment child = new Assignment();
        LogicCircuit lc = initEmptyCircuit();

        for (int i = 0; i < parent.gates.size(); i++) {
            Gate g = lc.get_logic_gates().get(i);
            String name = parent.gates.get(i);
            ArrayList<Gate> available_gates = getAvailableGates(g, lc);

            if (!available_gates.contains(name) || bernoulliSample(_GENE_MUTATION_PROB)) {
                if (available_gates.size() == 0) {
                    broken = 1;
                    break;
                } else {
                    int gate_idx = generator.nextInt(available_gates.size());
                    name = available_gates.get(gate_idx).name;
                }
            }

            child.gates.add(name);
            assignGate(g, name);
        }

        if (broken > 0) {
            logger.info("mutation broken");
            return parent;
        } else {
            return child;
        }
    }

    private ArrayList<Assignment> crossover(Assignment parent1, Assignment parent2) {
        Assignment child1 = new Assignment();
        Assignment child2 = new Assignment();

        LogicCircuit lc1 = initEmptyCircuit();
        LogicCircuit lc2 = initEmptyCircuit();

        int cutoff_point = generator.nextInt(parent1.gates.size());

        int broken1 = 0;
        int broken2 = 0;

        for (int i = 0; i < parent1.gates.size(); i++) {
            String gate_name1 = parent1.gates.get(i);
            Gate g1 = lc1.get_logic_gates().get(i);

            String gate_name2 = parent2.gates.get(i);
            Gate g2 = lc2.get_logic_gates().get(i);

            if (i >= cutoff_point) {
                String tmp = gate_name1;

                ArrayList<Gate> available_gates1 = getAvailableGates(g1, lc1);
                if (available_gates1.size() == 0) {
                    broken1 = 1;
                } else {
                    if (available_gates1.contains(gate_name2)) {
                        gate_name1 = gate_name2;
                    } else {
                        int gate_idx = generator.nextInt(available_gates1.size());
                        gate_name1 = available_gates1.get(gate_idx).name;
                    }
                }

                ArrayList<Gate> available_gates2 = getAvailableGates(g2, lc2);
                if (available_gates2.size() == 0) {
                    broken2 = 1;
                } else {
                    if (available_gates2.contains(tmp)) {
                        gate_name2 = tmp;
                    } else {
                        int gate_idx = generator.nextInt(available_gates2.size());
                        gate_name2 = available_gates2.get(gate_idx).name;
                    }
                }
            }

            assignGate(g1, gate_name1);
            assignGate(g2, gate_name2);

            child1.gates.add(gate_name1);
            child2.gates.add(gate_name2);
        }

        ArrayList<Assignment> children = new ArrayList();
        if (broken1 > 0) {
            logger.info("child 1 broken");
            children.add(parent1);
        } else {
            children.add(child1);
        }

        if (broken2 > 0) {
            logger.info("child 2 broken");
            children.add(parent2);
        } else {
            children.add(child2);
        }

        return children;
    }

    private ArrayList<Gate> getAvailableGates(Gate gc, LogicCircuit lc) {
        ArrayList<Gate> gates_of_type = new ArrayList<Gate>(get_gate_library().get_GATES_BY_TYPE().get(gc.type).values());
        ArrayList<Gate> available_gates = new ArrayList();

        for (Gate libgate : gates_of_type) {

            //setting to null then refreshing clears the attributes assocated with the gate
            gc.name = null;
            Evaluate.refreshGateAttributes(gc, get_gate_library());

            if (currentlyAssignedGroup(lc, libgate.group)) {
                continue;
            }

            assignGate(gc, libgate.name);

            if(get_options().is_toxicity()) {
                gc.set_toxtable(get_gate_library().get_GATES_BY_NAME().get(gc.name).get_toxtable());
                Toxicity.evaluateGateToxicity(gc);
                if (Toxicity.mostToxicRow(gc) < get_options().get_toxicity_threshold()) {
                    continue;
                }
            }

            if(get_options().is_noise_margin()) {
                if (!gc.get_scores().is_noise_margin_contract()) {
                    continue;
                }
            }

            if(!get_options().is_tpmodel()) {
                if(get_options().is_check_roadblocking()) {
                    if(get_roadblock().numberRoadblocking(lc, get_gate_library()) > 0) {
                        continue;
                    }
                }
            }

            available_gates.add(libgate);
        }

        return available_gates;
    }


    private Assignment randomSolution() {
        for (int attempt=0; attempt < _MAX_ATTEMPTS; attempt++) {
            Assignment solution = new Assignment();

            LogicCircuit lc = get_unassigned_lc();
            LogicCircuitUtil.sortGatesByStage(lc);

            for(Gate g: lc.get_output_gates()) {
                Evaluate.refreshGateAttributes(g, get_gate_library());
            }

            int found = 1;

            for(int gi=0; gi<lc.get_logic_gates().size(); gi++) {

                /**
                * Gate gc, 'current gate', is the gate that we want to assign now.
                */
                Gate gc = lc.get_logic_gates().get(gi);

                ArrayList<Gate> available_gates = getAvailableGates(gc, lc);

                if (available_gates.isEmpty()) {
                    logger.info("no gate available");
                    found = 0;
                    break;
                }

                int gate_idx = generator.nextInt(available_gates.size());
                Gate selected_gate = available_gates.get(gate_idx);

                assignGate(gc, selected_gate.name);
                solution.gates.add(selected_gate.name);
            }

            if (found == 0) {
                continue;
            }

            return solution;
        }

        // This should be some other exception, not RuntimeException
        throw new RuntimeException("Random solution not found.");
    }

    /**
     * If group name already exists in the current circuit assignment, return true;
     * This prevents the assignment of genetic gates belonging to the same group (such as RBS variants or crosstalkers)
     *
     * @param lc
     * @param group_name
     * @return
     */
    private boolean currentlyAssignedGroup(LogicCircuit lc, String group_name) {

        for(Gate g: lc.get_logic_gates()) {

            if(g.group.equals(group_name)) {
                return true;
            }
        }

        return false;
    }

    private Logger logger  = Logger.getLogger(getClass());
}
