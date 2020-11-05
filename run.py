import os,subprocess,shlex,re
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages


algorithms = ["genetic","sim_annealing","hill_climbing"]
test_files = ["example1","and2","and3","or2","or3","xor2"]
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

#def get_elapsed_time(r):
#    p = re.compile("Total elapsed.*?(\d+).*?")
#    iterator = p.finditer(r)
#    for match in iterator:
#        return int(match.group(1))


# Add the scores of the 2 lists
def add_scores(l1,l2,run_nb):
    if run_nb == 1:
        return l2
    l3 = []
    for i in range(max(len(l1),len(l2))):
        if (i+1 > len(l1)):
            l3.append(run_nb*l2[i])
        elif (i+1 > len(l2)):
            l3.append(l1[i]/(run_nb-1)+l1[i])
        else:
            l3.append(l1[i]+l2[i])
    return l3


# Main
test_nb = 1
pp = PdfPages("Results.pdf")
for test_file in test_files:
    print(" ===== "+test_file+" =====")
    scores_avg = []
    diagram = plt.figure(test_nb)
    #histo = plt.figure(test_nb*2)
    #time_y = []
    for algorithm in algorithms:
        nb_fails = 0
        print(" == "+algorithm+" ==")
        scores_total = []
        #total_elapsed = 0
        for i in range(nb_runs):
            print("Run "+str(i+1))
            r = run_test(algorithm, test_file+".v")
            print(r)
            scores = get_scores(r)
            if (len(scores) == 0):
                print("No solution found !")
                nb_fails = nb_fails + 1
            #else:
                #elapsed = get_elapsed_time(r)
                #total_elapsed = total_elapsed + elapsed

            scores_total = add_scores(scores_total, scores, i+1)

        scores_avg = [i/nb_runs for i in scores_total]
        nb_scores = len(scores_avg)
        x = [i for i in range(1,nb_scores+1)]
        if (nb_runs == nb_fails):
            #time_y.append(0)
            x = [0]
            scores_avg = [0]
            print("No solution found at all !!!")
        else:
            #time_y.append(total_elapsed / (nb_runs - nb_fails))
            last = scores_avg[nb_scores-1]
            plt.text(nb_scores,last,last,horizontalalignment='right')

        plt.plot(x, scores_avg, label=algorithm+" algorithm ("+str(nb_fails)+" fails)")
    test_nb = test_nb+1

    plt.legend()
    plt.title('Average iterations score for "'+test_file+'" ('+str(nb_runs)+' runs)')
    plt.xlabel('Iterations')
    plt.ylabel('Best score')
    pp.savefig(diagram, dpi = 300, transparent = True)
    #plt.bar(algorithms, time_y)
    #plt.title('Average duration for "'+test_file+'" ('+str(nb_runs)+' runs)')
    #plt.xlabel('Iterations')
    #plt.ylabel('Time (in ms)')
    #pp.savefig(histo, dpi = 300, transparent = True)

pp.close()
