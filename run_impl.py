import os
import time
import signal
from subprocess import Popen, PIPE
from threading import Timer

def run(nodeName):
    impl_process = Popen([
        "/usr/lib/jvm/jdk-19/bin/java",
        "-jar",
        "target/Raft-1.0-SNAPSHOT-jar-with-dependencies.jar",
        nodeName
        ])
#         ], stderr=PIPE, stdout=PIPE)

    return impl_process

def timeout_process(p):
    my_timer = Timer(15, lambda p: p.kill(), [p])
    try:
        my_timer.start()
        stderr, stdout = p.communicate()
        print(stdout)
    finally:
        my_timer.cancel()


if __name__ == "__main__":
    p1 = run("node1")
    p2 = run("node2")
    # p3 = run("node3")

#     timeout_process(p1)
#     timeout_process(p2)

    # Wait all client are finished
    p1.wait()
    p2.wait()
#     p3.wait()

    p1.terminate()
    p2.terminate()
#     p3.terminate()

    # Kill server
#     os.kill(server_process.pid, signal.SIGINT)