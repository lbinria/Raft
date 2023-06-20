import os
from subprocess import Popen, PIPE, TimeoutExpired
import ndjson
import clean

# Get paths
java_home = os.environ["JAVA_HOME"]
assert java_home != None, "JAVA_HOME variable is not set."
java_bin = os.path.join(java_home, "java")

def run(nodeName):
    p = Popen([
        java_bin,
        "-jar",
        "target/Raft-1.0-SNAPSHOT-jar-with-dependencies.jar",
        nodeName
        ])
    return p


def run_all(timeout=5.):
    # Load config
    with open("raft.ndjson.conf", 'r') as f:
        json_config = ndjson.load(f)

    servers = json_config[0]['Server']

    # Run all processes
    processes =  [run(node_name) for node_name in servers]
    try:
        for p in processes:
            p.wait(timeout)
    except TimeoutExpired:
        print("Timeout reach.\n")
        for p in processes:
            p.terminate()


if __name__ == "__main__":
    # Clean directory
    clean.clean()
    run_all()
