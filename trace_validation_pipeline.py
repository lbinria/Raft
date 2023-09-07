import subprocess
import run_impl
import trace_merger
import tla_trace_validation
import clean
import argparse

parser = argparse.ArgumentParser(
    prog='Trace validation pipeline',
    description='This program aims to execute a pipeline that validate implementation of KeyValueStore against a formal spec.')

parser.add_argument('-c', '--compile', type=bool, action=argparse.BooleanOptionalAction)

args = parser.parse_args()

print("# Clean up.\n")

# Clean directory
clean.clean()

if args.compile:
    print("# Compile.\n")
    subprocess.run(["mvn", "package"])

print("# Run.\n")

run_impl.run_all(20.)

print("# Merge trace with config.\n")

trace_tla = trace_merger.run(["."], config="raft.ndjson.conf", sort=True)
# Write to file
with open("trace-tla.ndjson", "w") as f:
    f.write(trace_tla)

print("# Start TLA+ trace spec.\n")

tla_trace_validation.run_tla("spec/raftTrace.tla","trace-tla.ndjson")

print("End pipeline.")