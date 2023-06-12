import os
import time
import signal
from subprocess import Popen, PIPE
import run_impl
import trace_merger


print("# Clean up")

trace_files = [f for f in os.listdir(".") if f.endswith('.ndjson')]
print(f"Cleanup: {trace_files}")
for trace_file in trace_files:
    os.remove(trace_file)

print("# Start implementation.\n")

p1 = run_impl.run("node1")
p2 = run_impl.run("node2")
p3 = run_impl.run("node3")

# Wait all client are finished
p1.wait()
p2.wait()
p3.wait()

p1.terminate()
p2.terminate()
p3.terminate()

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