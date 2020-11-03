import os,subprocess,shlex,re
import matplotlib.pyplot as plt


algorithms = ["genetic","sim_annealing","hill_climbing"]
nb_runs = 10



def get_match(regex, s):
    p = re.compile(regex)
    r = p.match(s)
    return r.group(1)

def run_test(algorithm):
    os.chdir("demo")
    command_line = 'mvn -f ../pom.xml -DskipTests=true -PCelloMain -Dexec.args="-verilog demo_verilog.v -assignment_algorithm '+algorithm+'"'
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
for algorithm in algorithms:
    print(" == "+algorithm+" ==")
    scores_total = []
    for i in range(nb_runs):
        print("Run "+str(i+1))
        r = run_test(algorithm)
        scores = get_scores(r)
        scores_total = add_scores(scores_total, scores, i+1)

    scores_avg = [i/nb_runs for i in scores_total]
    nb_scores = len(scores_avg)
    x = [i for i in range(1,nb_scores+1)]
    plt.text(nb_scores,scores_avg[nb_scores-1],scores_avg[nb_scores-1]+5,horizontalalignment='right')
    plt.plot(x, scores_avg, label=algorithm+" algorithm")


plt.legend()
plt.title('Assignment algorithms comparison ('+str(nb_runs)+' runs)')
plt.xlabel('Iterations')
plt.ylabel('Best score')
plt.show()
