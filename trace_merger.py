import os
import ndjson
import argparse
from functools import reduce
from itertools import groupby

# Read ndjson file
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

# Merge trace files
def run(files, sort=False, remove_meta=False, out="trace.ndjson", config="conf.ndjson"):
    # Get all trace files - either the files themselves or all ndjson files in the directories pointed by files
    all_paths = reduce(lambda a, b: a + b, ([f] if os.path.isfile(f) else [os.path.join(f, filename) for filename in os.listdir(f) if filename.endswith('.ndjson')] for f in files))
    # Open trace files and concatenate events
    merged_trace = reduce(lambda a, b: a + b, (read_json(path) for path in all_paths), [])
    # Sort by clock
    if sort:
        merged_trace = list(sorted(merged_trace, key=lambda x: x['clock']))
    # Remove meta data: clock and sender
    if remove_meta:
        merged_trace = [{k:v for k, v in t.items() if k != "clock" and k != "logger"} for t in merged_trace]
    # Add config to the beginning of the trace
    if config:
        merged_trace = [read_json(config)[0]] + merged_trace
    # Dump in the target file
    with open(out, 'w') as f:
        ndjson.dump(merged_trace, f)

if __name__ == "__main__":
    # Read program args
    parser = argparse.ArgumentParser(description="")
    parser.add_argument('files', type=str, nargs="*", help="Trace files to merge")
    parser.add_argument('--config', type=str, required=False, default="conf.ndjson", help="Config file")
    parser.add_argument('--sort', type=bool, required=False, default=True, help="Sort by clock")
    parser.add_argument('--remove_meta', type=bool, required=False, default=True, help="Remove clock and sender data")
    parser.add_argument('--out', type=str, required=False, default="trace.ndjson", help="Output file")
    args = parser.parse_args()
    # Get files
    if len(args.files) > 0:
        files = args.files
    else:
        config = read_json(args.config)
        files = get_files(config)
    print(f"Traces merged: {files}")

    # Run
    run(files, sort=args.sort, remove_meta=args.remove_meta, out=args.out, config=args.config)