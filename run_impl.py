import os
from subprocess import Popen, PIPE, TimeoutExpired
import ndjson
import clean

JAR_NAME = "Raft-1.2-jar-with-dependencies.jar"
CONFIG_FILE = "raft.ndjson.conf"
TIMEOUT = 5.0

def run(node_name):
    args = [
        "java",
        "-cp",
        f"target/{JAR_NAME}",
        "org.lbee.protocol.Main",
        node_name
    ]
    return Popen(args)

#
def run_all(timeout=TIMEOUT):
    # Load config
    with open(CONFIG_FILE) as f:
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