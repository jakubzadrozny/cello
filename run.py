import os,subprocess,shlex,re
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages


algorithms = ["genetic","sim_annealing","hill_climbing"]
test_files = ["demo_verilog.v","example1.v"]
nb_runs = 10



def get_match(regex, s):
    p = re.compile(regex)
    r = p.match(s)
    return r.group(1)

def run_test(algorithm, test_file):
    os.chdir("examples")
    command_line = 'mvn -f ../pom.xml -DskipTests=true -PCelloMain -Dexec.args="-verilog '+test_file+' -assignment_algorithm '+algorithm+'"'
    args = shlex.split(command_line)
    r = subprocess.Popen(args, stdout=subprocess.PIPE)
    os.chdir("..")
    out,err = r.communicate()
    return out.decode("utf-8")



# Get a list of scores from a result, 1 score per algorithm iteration
def get_scores(r):
    last_iter = 0
    last_score = 0
    scores = []
    p = re.compile("iteration\s*(\d+): score =\s*(\d+),.*?\n")
    iterator = p.finditer(r)

    for match in iterator:
        iteration,score = match.group(1,2)

        # For duplicated lines
        if (int(iteration) == last_iter):
            continue

        for i in range(last_iter+1,int(iteration)-1):
            scores.append(last_score)

        scores.append(int(score))
        last_iter,last_score = int(iteration),int(score)

    return scores


# Add the scores of the 2 lists
def add_scores(l1,l2,run_nb):
    if run_nb == 1:
        return l2
    max_len = max(len(l1),len(l2))
    l3 = []
    for i in range(max_len):
        if (i+1 > len(l1)):
            l3.append(run_nb*l2[i])
        elif (i+1 > len(l2)):
            l3.append(l1[i]/run_nb+l1[i])
        else:
            l3.append(l1[i]+l2[i])
    return l3


# Main
test_nb = 1
pp = PdfPages("Results.pdf")
for test_file in test_files:
    scores_avg = []
    diagram = plt.figure(test_nb)
    for algorithm in algorithms:
        print(" == "+algorithm+" ==")
        scores_total = []
        for i in range(nb_runs):
            print("Run "+str(i+1))
            r = run_test(algorithm, test_file)
            scores = get_scores(r)
            scores_total = add_scores(scores_total, scores, i+1)

        scores_avg = [i/nb_runs for i in scores_total]
        nb_scores = len(scores_avg)
        x = [i for i in range(1,nb_scores+1)]
        if (len(scores_avg) == 0):
            print("No solution found !")
            continue

        last = scores_avg[nb_scores-1]
        plt.text(nb_scores,last,last,horizontalalignment='right')
        plt.plot(x, scores_avg, label=algorithm+" algorithm")
    test_nb = test_nb+1

    plt.legend()
    plt.title('Comparison for "'+test_file+'" ('+str(nb_runs)+' runs)')
    plt.xlabel('Iterations')
    plt.ylabel('Best score')
    pp.savefig(diagram, dpi = 300, transparent = True)

pp.close()
