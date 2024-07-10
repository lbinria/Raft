# Raft

An implementation of Raft consensus specification.

# Prerequisites

- Java >= 17
- Apache maven >= 3.6
- Python >= 3.9
- TLA+ >= 1.8.0 (The Clarke release)

### Install the trace validation tools (and TLA+)

See README at https://github.com/lbinria/trace_validation_tools

### Install python librairies

The `ndjson` Python library is needed in order to perform the
validation; it can be installed with:

`pip install ndjson`

We suppose that `python` and `pip` are the commands for Python and
its package installer, if otherwise you should change the above line
and some of the following accordingly.

# Build the Java program

Change the version of the dependency `org.lbee.instrumentation` in the
file [pom.xml](pom.xml) according to the one you use (in .m2 or on the
github maven registry) and run

`mvn package`

### Perform trace validation pipeline

To run the complete trace validation pipeline, the script [trace_validation_pipeline.py](trace_validation_pipeline.py) can be used with the following options:

- `-c`, `--compile`: (optional) Compile the implementation of Raft. If not specified, the script will not perform the compilation step.
- `--config <file>`: (optional) Specify the configuration file. Defaults to `conf.ndjson` if not provided.
- `--spec <file>`: (optional) Specify the TLA+ specification file. Defaults to `spec/raftTrace.tla` if not provided.

#### Example usage

Run the trace validation pipeline with compilation:
```bash
python trace_validation_pipeline.py -c
```

Run the trace validation pipeline with a custom TLA+ specification file:

```bash
python trace_validation_pipeline.py --spec spec_abstract/raftTrace.tla
```

It consists of the following steps:
- clean old trace files
- compile implementation of Raft
- run implementation of Raft
- [merge trace files / config into one trace file (when different processes produce different trace files)]
- Run TLC on the resulting trace file

### Perform trace validation on a trace file

Alternatively, we can run the implementation with the command

`mvn exec:java`

or

`python run_impl.py`

and then perform the trace validation on the obtained trace file
`trace.ndjson` by using the command:

`python tla_trace_validation.py spec/raftTrace.tla --trace trace.ndjson`

# Directory structure

- `spec/**`: contains Raft specification and trace specification
- `src/**`: contains Raft implementation