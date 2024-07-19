from subprocess import Popen, TimeoutExpired, run as subprocess_run
import ndjson
import clean

# Run all nodes
def run_all():
    mvn_command = ["./mvnw", "clean", "package"]
    result = subprocess_run(mvn_command)
    if result.returncode != 0:
        print("Failed to run mvnw clean package")
        return

if __name__ == "__main__":
    # Clean directory
    clean.clean()  # except conf.ndjson
    run_all()
