import subprocess
import run_impl
import trace_merger
import tla_trace_validation
import argparse
import ndjson
import os


def read_json(filename):
    with open(filename) as f:
        return ndjson.load(f)


# Get files to be merged from the config file.
# Should be adapted to the specific format of the config file.
def get_files(config):
    files = []
    for line in config:
        if "Server" in line:
            files += [server + ".ndjson" for server in line["Server"]]
    return files


parser = argparse.ArgumentParser("")
parser.add_argument('-c', '--compile', type=bool, action=argparse.BooleanOptionalAction)
parser.add_argument('--config', type=str, required=False, default="conf.ndjson", help="Config file")
parser.add_argument('--spec', type=str, required=False, default="spec/raftTrace.tla", help="TLA+ specification file")
args = parser.parse_args()

config = read_json(args.config)
files = get_files(config)

# Clean up
print("# Clean up")
trace_files = files + ["trace.ndjson"]
print(f"Cleanup: {files}")
for trace_file in trace_files:
    if os.path.isfile(trace_file):
        os.remove(trace_file)

# Compile
if args.compile:
    print("# Package.\n")
    subprocess.run(["mvn", "package"])

# Run
print("# Start implementation.\n")
run_impl.run_all(20.)

# Merge traces
print("# Merge traces.\n")
trace_merger.run(files, sort=True, remove_meta=True, out="trace.ndjson", config="conf.ndjson")

# Validate trace
print("# Start TLA+ trace spec.\n")
tla_trace_validation.run_tla(args.spec, "trace.ndjson", "conf.ndjson")
