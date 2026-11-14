"""Builds Wayfinder_FallRisk_Training.ipynb (prep_data.py is embedded so the notebook stands alone)."""
import json
from pathlib import Path
HERE = Path(__file__).parent
prep = (HERE / 'prep_data.py').read_text()

def md(s): return {'cell_type': 'markdown', 'metadata': {}, 'source': s.strip('\n')}
def code(s): return {'cell_type': 'code', 'metadata': {}, 'execution_count': None, 'outputs': [], 'source': s.strip('\n')}

cells = [
md("""
# Wayfinder: fall-risk detector training

Teaches a small, fast AI (YOLO11n) to spot **stairs, curbs, holes, cracks and things on the floor** for the Wayfinder app.

**How to run:** menu **Runtime → Run all**. Allow Google Drive when asked.
Everything is saved to Google Drive in the **Wayfinder** folder, so if Colab disconnects, just **Run all** again and it picks up where it stopped.
"""),
md("## 1. Check the graphics card and connect Google Drive"),
code("""
import subprocess, os
gpu = subprocess.run(['nvidia-smi', '--query-gpu=name,memory.total', '--format=csv,noheader'], capture_output=True, text=True).stdout.strip()
assert gpu, 'NO GRAPHICS CARD. Menu: Runtime -> Change runtime type -> T4 GPU, then Run all again.'
print('Graphics card:', gpu)
from google.colab import drive
drive.mount('/content/drive')
SAVE = '/content/drive/MyDrive/Wayfinder'
os.makedirs(SAVE, exist_ok=True)
!pip -q install ultralytics
!which git-lfs >/dev/null || apt-get -qq install -y git-lfs
!git lfs install --skip-repo
"""),
md("## 2. The dataset builder (merges 3 free photo sets into one set of fall-risk labels)"),
code('%%writefile /content/prep_data.py\n' + prep),
md("## 3. Build the photo set (about 20 minutes the first time; later runs reuse the copy saved in Drive)"),
code("""
import os, shutil
DS = '/content/ds'
ZIP = f'{SAVE}/fallrisk_dataset.zip'
if os.path.exists(f'{DS}/data.yaml'):
    print('dataset already here')
elif os.path.exists(ZIP):
    print('unzipping saved dataset from Drive...')
    !unzip -q -o "{ZIP}" -d /content
else:
    !python /content/prep_data.py {DS} --coco 8000
    print('saving a copy to Drive so the next run is quick...')
    shutil.make_archive('/content/fallrisk_dataset', 'zip', '/content', 'ds')
    shutil.copy('/content/fallrisk_dataset.zip', ZIP)
# data.yaml must point at this machine's folder
y = open(f'{DS}/data.yaml').read().split('\\n'); y[0] = f'path: {DS}'
open(f'{DS}/data.yaml', 'w').write('\\n'.join(y))
print(open(f'{DS}/data.yaml').read())
"""),
md("## 4. Train (several hours; checkpoints are saved to Drive after every round)"),
code("""
from ultralytics import YOLO
RUN = f'{SAVE}/runs/fallrisk'
last, done = f'{RUN}/weights/last.pt', f'{RUN}/TRAINING_DONE.txt'
if os.path.exists(done):
    print('Training already finished, skipping to the results.')
elif os.path.exists(last):
    print('Colab stopped last time. Resuming from', last)
    YOLO(last).train(resume=True)
else:
    model = YOLO('yolo11n.pt')   # starts from an AI that already knows everyday objects
    model.train(data=f'{DS}/data.yaml', epochs=40, imgsz=640, batch=32, workers=2, patience=10,
                project=f'{SAVE}/runs', name='fallrisk', exist_ok=True, plots=True)
open(done, 'w').write('finished\n')
"""),
md("## 5. How well did it learn? (per item; higher is better, 1.0 = perfect)"),
code("""
best = f'{RUN}/weights/best.pt'
m = YOLO(best)
r = m.val(data=f'{DS}/data.yaml', split='test', imgsz=640, batch=32, plots=False)
print(f"\\nOverall mAP50: {r.box.map50:.2f}")
for ci, ap in zip(r.box.ap_class_index, r.box.ap50):
    print(f'{m.names[int(ci)]:15s} {ap:.2f}')
"""),
md("## 6. Make the phone version (TensorFlow Lite) and save it to Drive"),
code("""
m = YOLO(best)
f32 = m.export(format='tflite', imgsz=640)          # also writes a float16 copy next to it
import glob
outdir = os.path.dirname(f32)
for f in glob.glob(f'{outdir}/*.tflite'):
    shutil.copy(f, SAVE); print('saved', os.path.basename(f))
open(f'{SAVE}/labels.txt', 'w').write('\\n'.join(m.names[i] for i in range(len(m.names))) + '\\n')
print('Phone files are in Google Drive -> Wayfinder')
"""),
md("## 7. Try it on your own photo (optional)\nRun this cell, click **Choose Files**, and pick a photo of stairs, a curb or a cluttered floor."),
code("""
from google.colab import files
from IPython.display import Image, display
up = files.upload()
for name in up:
    res = YOLO(best).predict(name, conf=0.35, save=True, project='/content/try', name='out', exist_ok=True)
    for b in res[0].boxes:
        print(f'{res[0].names[int(b.cls)]:15s} {float(b.conf):.2f}')
    display(Image(f'/content/try/out/{name}', width=640))
"""),
]
nb = {'nbformat': 4, 'nbformat_minor': 5, 'cells': cells,
      'metadata': {'accelerator': 'GPU', 'colab': {'provenance': [], 'gpuType': 'T4'},
                   'kernelspec': {'name': 'python3', 'display_name': 'Python 3'}}}
for c in nb['cells']:
    c['source'] = c['source'].splitlines(keepends=True)
(Path.home() / 'wayfinder' / 'training' / 'Wayfinder_FallRisk_Training.ipynb').write_text(json.dumps(nb, indent=1))
print('written')
