package org.cellocad.MIT.dnacompiler;


import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.Set;
import java.util.HashSet;

/***********************************************************************
 Synopsis    [ Template file for genetic algorithm assigning repressors to gates.]

 Some genetic approach, should be relatively good and fast.

 ***********************************************************************/

/**
 *
 */
public class BuildCircuitsGenetic extends BuildCircuits {

    private static final int _MAX_ATTEMPTS = 100;
    private static final int _POPULATION_SIZE = 20;
    private static final int _NUM_EPOCHS = 5;

    private static final Double _MUTATION_PROB = 0.75;
    private static final Double _CROSSOVER_PROB = 0.5;
    private static final Double _GENE_MUTATION_PROB = 0.3;

    private static final String _EVOLUTION_TYPE = "plus";

    private static final Double _INVALID_SCORE = 0.0;

    private Random generator;

    private class NoGatesAvailableException extends Exception {
        public NoGatesAvailableException(String msg) {
            super(msg);
        }
    }

    private class Assignment implements Comparable<Assignment> {
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
            evaluateSolution(solution);
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

                evaluateSolution(child);
                children.set(i, child);
            }

            if (_EVOLUTION_TYPE == "plus") {
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

        int bestCircuit = 0;
        for (int i = 1; i < _POPULATION_SIZE; i++) {
            if (population.get(bestCircuit).score < population.get(i).score) {
                bestCircuit = i;
            }
        }

        Assignment solution = population.get(bestCircuit);
        LogicCircuit lc = constructCircuitFromAssignment(solution);
        evaluateCircuit(lc);
        get_logic_circuits().add(lc);
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

    private LogicCircuit initEmptyCircuit() {
        LogicCircuit lc = get_unassigned_lc();
        LogicCircuitUtil.sortGatesByStage(lc);

        for(Gate g: lc.get_logic_gates()) {
            g.name = null;
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

    private LogicCircuit constructCircuitFromAssignment(Assignment solution) {
        LogicCircuit lc = initEmptyCircuit();

        for(int gi=0; gi<lc.get_logic_gates().size(); gi++) {
            String name = solution.gates.get(gi);
            Gate g = lc.get_logic_gates().get(gi);
            assignGate(g, name);
        }

        return lc;
    }


    private void evaluateSolution(Assignment solution) {
        LogicCircuit lc = constructCircuitFromAssignment(solution);
        evaluateCircuit(lc);
        solution.score = lc.get_scores().get_score();
    }


    private void evaluateCircuit(LogicCircuit lc) {
        // Set<String> usedGroups = new HashSet<>();

        // for (int i = 0; i < lc.get_logic_gates().size(); i++) {
        //     Gate g = lc.get_logic_gates().get(i);
        //
        //     if (usedGroups.contains(g.group)) {
        //         logger.info("grp used");
        //         solution.score = _INVALID_SCORE;
        //         return;
        //     }
        //     usedGroups.add(g.group);
        //
        //     if(get_options().is_toxicity()) {
        //         g.set_toxtable(get_gate_library().get_GATES_BY_NAME().get(g.name).get_toxtable());
        //         Toxicity.evaluateGateToxicity(g);
        //         if (Toxicity.mostToxicRow(g) < get_options().get_toxicity_threshold()) {
        //             logger.info("tox");
        //             solution.score = _INVALID_SCORE;
        //             return;
        //         }
        //     }
        //
        //     if(get_options().is_noise_margin()) {
        //         if (!g.get_scores().is_noise_margin_contract()) {
        //             logger.info("nm");
        //             solution.score = _INVALID_SCORE;
        //             return;
        //         }
        //     }
        // }
        //
        // if(!get_options().is_tpmodel()) {
        //     if(get_options().is_check_roadblocking()) {
        //         if(get_roadblock().numberRoadblocking(lc, get_gate_library()) > 0) {
        //             solution.score = _INVALID_SCORE;
        //             return;
        //         }
        //     }
        // }

        Evaluate.evaluateCircuit(lc, get_gate_library(), get_options());
        // Toxicity.evaluateCircuitToxicity(lc, get_gate_library());
    }


    private Assignment mutate(Assignment parent) {
        Assignment child = new Assignment();
        LogicCircuit lc = initEmptyCircuit();

        for (int i = 0; i < parent.gates.size(); i++) {
            Gate g = lc.get_logic_gates().get(i);
            String name = parent.gates.get(i);

            if (bernoulliSample(_GENE_MUTATION_PROB) || !checkGateForCircuit(g, name, lc)) {
                try {
                    name = sampleGateForCircuit(g, lc);
                } catch (NoGatesAvailableException e) {
                    // logger.info("mutation broken");
                    return randomSolution();
                }
            }

            child.gates.add(name);
            assignGate(g, name);
        }
        return child;
    }

    private Assignment mixOnCutoffPoint(Assignment parent1, Assignment parent2, int cutoff_point) {
        Assignment child = new Assignment();
        LogicCircuit lc = initEmptyCircuit();

        for (int i = 0; i < parent1.gates.size(); i++) {
            String gateName1 = parent1.gates.get(i);
            String gateName2 = parent2.gates.get(i);
            Gate g = lc.get_logic_gates().get(i);

            if (i >= cutoff_point) {
                if (checkGateForCircuit(g, gateName2, lc)) {
                    gateName1 = gateName2;
                } else {
                    try {
                        gateName1 = sampleGateForCircuit(g, lc);
                    } catch (NoGatesAvailableException e) {
                        // logger.info("child broken");
                        return randomSolution();
                    }
                }
            }

            assignGate(g, gateName1);
            child.gates.add(gateName1);
        }

        return child;
    }

    private ArrayList<Assignment> crossover(Assignment parent1, Assignment parent2) {
        int size = parent1.gates.size();
        int margin = size / 5;
        int cutoff_point = generator.nextInt(size - 2 * margin) + margin;

        ArrayList<Assignment> children = new ArrayList();
        children.add(mixOnCutoffPoint(parent1, parent2, cutoff_point));
        children.add(mixOnCutoffPoint(parent2, parent1, cutoff_point));
        return children;
    }

    private boolean checkGateForCircuit(Gate gc, String name, LogicCircuit lc) {
        //setting to null then refreshing clears the attributes assocated with the gate
        gc.name = null;
        Evaluate.refreshGateAttributes(gc, get_gate_library());

        Gate otherGate = get_gate_library().get_GATES_BY_NAME().get(name);

        if (currentlyAssignedGroup(lc, otherGate.group)) {
            return false;
        }

        assignGate(gc, name);

        if(get_options().is_toxicity()) {
            gc.set_toxtable(get_gate_library().get_GATES_BY_NAME().get(gc.name).get_toxtable());
            Toxicity.evaluateGateToxicity(gc);
            if (Toxicity.mostToxicRow(gc) < get_options().get_toxicity_threshold()) {
                return false;
            }
        }

        if(get_options().is_noise_margin()) {
            if (!gc.get_scores().is_noise_margin_contract()) {
                return false;
            }
        }

        if(!get_options().is_tpmodel()) {
            if(get_options().is_check_roadblocking()) {
                if(get_roadblock().numberRoadblocking(lc, get_gate_library()) > 0) {
                    return false;
                }
            }
        }

        return true;
    }


    private ArrayList<String> getAvailableGates(Gate gc, LogicCircuit lc) {
        ArrayList<Gate> gatesOfType = new ArrayList<Gate>(get_gate_library().get_GATES_BY_TYPE().get(gc.type).values());
        ArrayList<String> availableGateNames = new ArrayList();

        for (Gate otherGate : gatesOfType) {
            if (checkGateForCircuit(gc, otherGate.name, lc)) {
                availableGateNames.add(otherGate.name);
            }
        }

        return availableGateNames;
    }


    private String sampleGateForCircuit(Gate gc, LogicCircuit lc) throws NoGatesAvailableException {
        ArrayList<String> availableGates = getAvailableGates(gc, lc);

        if (availableGates.isEmpty()) {
            // logger.info("no gate available");
            throw new NoGatesAvailableException("no gate available");
        }

        int randomIdx = generator.nextInt(availableGates.size());
        return availableGates.get(randomIdx);
    }


    private Assignment randomSolution() {
        for (int attempt=0; attempt < _MAX_ATTEMPTS; attempt++) {
            Assignment solution = new Assignment();
            LogicCircuit lc = initEmptyCircuit();

            int found = 1;

            for(int gi=0; gi<lc.get_logic_gates().size(); gi++) {

                /**
                * Gate gc, 'current gate', is the gate that we want to assign now.
                */
                Gate gc = lc.get_logic_gates().get(gi);

                String sampledName;
                try {
                    sampledName = sampleGateForCircuit(gc, lc);
                } catch(NoGatesAvailableException e) {
                    found = 0;
                    break;
                }

                assignGate(gc, sampledName);
                solution.gates.add(sampledName);
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
