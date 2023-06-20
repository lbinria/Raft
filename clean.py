import os

def clean():
    trace_files = [f for f in os.listdir(".") if f.endswith('.ndjson')]
    print(f"Cleanup: {trace_files}")
    for trace_file in trace_files:
        os.remove(trace_file)

if __name__ == "__main__":
    clean()