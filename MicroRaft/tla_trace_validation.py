import os
import argparse
from subprocess import Popen
import glob
import json

# Path to TLA installation
tla_dir = "/home/terrytmp/stage/toolbox"
tla_jar = os.path.join(tla_dir, "tla2tools.jar")
community_modules_jar = os.path.join(tla_dir, "CommunityModules-deps.jar")
tla_cp = f"{tla_jar}:{community_modules_jar}"

# Run TLC
def run_tla(trace_spec, trace="trace.ndjson", config="conf.ndjson", dfs=False):
    os.environ["TRACE_PATH"] = trace
    os.environ["CONFIG_PATH"] = config
    if dfs:
        tla_trace_validation_process = Popen([
            "java",
            "-XX:+UseParallelGC",
            "-Dtlc2.tool.queue.IStateQueue=StateDeque",
            "-cp",
            tla_cp,
            "tlc2.TLC",
            "-note",
            trace_spec])
    else:
        tla_trace_validation_process = Popen([
            "java",
            "-XX:+UseParallelGC",
            "-cp",
            tla_cp,
            "tlc2.TLC",
            "-note",
            trace_spec])
    tla_trace_validation_process.wait()
    tla_trace_validation_process.terminate()

def gather_and_overwrite_config():
    node_files = glob.glob("microraft/node*.ndjson")
    nodes = [os.path.splitext(os.path.basename(file))[0] for file in node_files]

    cluster_info = []
    for i, node in enumerate(nodes):
        cluster_info.append({
            "name": node,
            "seed": 4 + i * 2800,
            "host": "LOCALHOST",
            "port": 1200 + i
        })

    config_content = {
        "Value": ["v_1", "v_2", "v_3", "v_4", "v_5"],
        "Server": nodes,
        "MaxTerm": 5,
        "MaxEntries": 5,
        "ClusterInfo": cluster_info
    }

    with open("conf.ndjson", "w") as config_file:
        json.dump(config_content, config_file, indent=None)

if __name__ == "__main__":
    # Read program args
    parser = argparse.ArgumentParser(description="")
    parser.add_argument('spec', type=str, help="Specification file")
    parser.add_argument('--trace', type=str, required=False, default="trace.ndjson", help="Trace file")
    parser.add_argument('--config', type=str, required=False, default="conf.ndjson", help="Config file")
    parser.add_argument('-dfs', '--dfs', type=bool, action=argparse.BooleanOptionalAction, help="breadth-first search")
    args = parser.parse_args()

    gather_and_overwrite_config()
    # Run
    run_tla(args.spec, args.trace, args.config, args.dfs)