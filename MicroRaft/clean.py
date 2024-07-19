import os
import glob

def clean():
    directory = "microraft/"
    # Rechercher les fichiers correspondant au modèle "node<number>.ndjson"
    trace_files = glob.glob(os.path.join(directory, "node*.ndjson"))
    # Ajouter "trace.ndjson" à la liste des fichiers à supprimer
    trace_files.append(os.path.join(directory, "trace.ndjson"))

    print(f"Cleanup: {trace_files}")
    for trace_file in trace_files:
        if os.path.isfile(trace_file):
            os.remove(trace_file)
            print(f"Removed: {trace_file}")
        else:
            print(f"File not found: {trace_file}")



if __name__ == "__main__":
    clean()