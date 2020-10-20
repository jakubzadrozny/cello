package org.cellocad.MIT.dnacompiler;


import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Collections;
import java.util.LinkedHashMap;

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
  private static final int _NUM_EPOCHS = 100;

  private static final Double _MUTATION_PROB = 0.3;
  private static final Double _CROSSOVER_PROB = 0.9;

  private Random generator;

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

      set_logic_circuits( new ArrayList<LogicCircuit>() );

      ArrayList<ArrayList<String>> population = new ArrayList();
      ArrayList<Double> scores = new ArrayList();
      for (int i = 0; i < _POPULATION_SIZE; i++) {
        ArrayList<String> solution = randomSolution();

        // Fallback in case we didn't find solution -- very unlikely case
        // if (solution.isEmpty()) {
        //   solution = reallyRandomSolution();
        // }

        LogicCircuit lc = buildAndScoreCircuit(solution);

        population.add(solution);
        scores.add(lc.get_scores().get_score());
      }

      for (int epoch = 0; epoch < _NUM_EPOCHS; epoch++) {
        ArrayList<Double> fitness = new ArrayList();
        Double min_score = Collections.min(scores);
        for (Double s : scores) {
          fitness.add(s - min_score);
        }

        ArrayList<ArrayList<String>> children = new ArrayList();
        ArrayList<Double> children_scores = new ArrayList();

        for (int i = 0; i < _POPULATION_SIZE / 2; i++) {
          int parentAIdx = weightedDiscreteSample(fitness);
          int parentBIdx = weightedDiscreteSample(fitness);
          logger.info("Drawn idx: " + String.valueOf(parentAIdx));

          ArrayList<String> parentA = population.get(parentAIdx);
          ArrayList<String> parentB = population.get(parentBIdx);

          // if (bernoulliSample(_CROSSOVER_PROB)) {
            // crossover -> 2 children
            // insert
          // } else {
          children.add(parentA);
          children.add(parentB);
          // }
        }

        // Just to avoid problems with odd population size
        // copy a random parent to the children population
        if (_POPULATION_SIZE % 2 == 1) {
          int parentIdx = weightedDiscreteSample(fitness);
          children.add(population.get(parentIdx));
        }

        for (int i = 0; i < _POPULATION_SIZE; i++) {
          ArrayList<String> child = children.get(i);

          // if (bernoulliSample(_MUTATION_PROB)) {
          //   // mutate
          //   children.set(i, child);
          // }

          LogicCircuit lc = buildAndScoreCircuit(child);
          children_scores.add(lc.get_scores().get_score());
        }

        population.addAll(children  );
        scores.addAll(children_scores);
        ArrayList<ArrayList<String>> new_population = new ArrayList();

        population = children;
        scores = children_scores;
      }

      for (int i = 0; i < _POPULATION_SIZE; i++) {
        ArrayList<String> solution = population.get(i);
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

      ArrayList<Double> probs = new ArrayList();
      if (weights_sum > 0.0) {
        for (Double w : weights) {
          Double p = w / weights_sum;
          probs.add(p);
        }
      } else {
        int size = weights.size();
        for (Double w : weights) {
          probs.add(1.0 / size);
        }
      }

      Double probs_sum = 0.0;
      ArrayList<Double> CMF = new ArrayList();
      for (Double p : probs) {
        probs_sum += p;
        CMF.add(probs_sum);
      }

      Double r = generator.nextDouble();
      int k = Collections.binarySearch(CMF, r);
      // This can break if we randomly draw a float from the list, but...
      // The probability of this happening is almost 0
      return -k - 1;
    }

    private LogicCircuit buildAndScoreCircuit(ArrayList<String> solution) {
      LogicCircuit lc = get_unassigned_lc();
      LogicCircuitUtil.sortGatesByStage(lc);

      for(Gate g: lc.get_output_gates()) {
          Evaluate.refreshGateAttributes(g, get_gate_library());
      }

      for(int gi=0; gi<lc.get_logic_gates().size(); gi++) {
          String gate_name = solution.get(gi);
          Gate g = lc.get_logic_gates().get(gi);
          g.name = gate_name;
      }

      Evaluate.evaluateCircuit(lc, get_gate_library(), get_options());
      Toxicity.evaluateCircuitToxicity(lc, get_gate_library());

      return lc;
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

        gc.name = libgate.name;
        Evaluate.refreshGateAttributes(gc, get_gate_library());
        gc.set_unvisited(true);

        Evaluate.simulateRPU(gc, get_gate_library(), get_options());
        Evaluate.evaluateGate(gc, get_options());

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


    private ArrayList<String> randomSolution() {
      ArrayList<String> solution = new ArrayList();

      for (int attempt=0; attempt < _MAX_ATTEMPTS; attempt++) {
        solution = new ArrayList();

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

          gc.name = null;
          Evaluate.refreshGateAttributes(gc, get_gate_library());

          gc.name = selected_gate.name;
          solution.add(selected_gate.name);
          Evaluate.refreshGateAttributes(gc, get_gate_library());
          gc.set_unvisited(true);

          Evaluate.simulateRPU(gc, get_gate_library(), get_options());
          Evaluate.evaluateGate(gc, get_options());
        }

        if (found == 0) {
          continue;
        }

        logger.info("Solution found at attempt " + String.valueOf(attempt + 1));
        return solution;
      }

      return new ArrayList();
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
