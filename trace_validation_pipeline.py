from subprocess import Popen, PIPE
import run_impl
import trace_merger
import clean

print("# Clean up.\n")

# Clean directory
clean.clean()

print("# Run.\n")

run_impl.run_all(30.)

print("# Merge trace with config.\n")

trace_tla = trace_merger.run(["."], config="raft.ndjson.conf", sort=True)
# Write to file
with open("trace-tla.ndjson", "w") as f:
    f.write(trace_tla)

print("# Start TLA+ trace spec.\n")


tla_trace_validation_process = Popen([
    "python",
    "tla_trace_validation.py",
    "spec/raftTrace.tla",
    "trace-tla.ndjson"])

tla_trace_validation_process.wait()

print("End pipeline.")