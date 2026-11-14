"""Wayfinder fall-risk dataset builder.

Merges three free sources into one YOLO dataset with the labels Wayfinder speaks:
  1. ROD-Dataset (Abtinzandi/Obstacle-Detection-Dataset-YOLO, MIT): 24k sidewalk photos, stairs, poles, bins...
  2. Unified Obstacles (Dderedor/obstacles_on_the_road, MIT): curbs, cracks, potholes, open manholes
  3. COCO 2017 (CC BY 4.0): indoor/floor things: bags, balls, skateboards, chairs, pets

usage: python prep_data.py OUT_DIR [--coco N]   (N = max COCO images, default 8000, 0 = skip)
"""
import concurrent.futures as cf, io, os, random, shutil, sys, urllib.request, zipfile
from pathlib import Path

# ---- the classes the app will announce (index = class id in the trained model)
NAMES = ['stairs', 'curb', 'hole', 'manhole', 'pole', 'trash can', 'cone', 'plant pot', 'chair',
         'bench', 'fence', 'bike rack', 'fire hydrant', 'electrical box', 'bag', 'ball', 'skateboard',
         'person', 'animal', 'bicycle', 'vehicle', 'tree', 'crosswalk']
N = {n: i for i, n in enumerate(NAMES)}

ROD = {  # ROD class id -> our name (None = drop the box)
    0: 'bicycle', 1: None, 2: 'vehicle', 3: 'person', 4: 'stairs', 5: None, 6: 'pole', 7: None,
    8: 'vehicle', 9: 'trash can', 10: 'animal', 11: 'manhole', 12: 'tree', 13: 'fence', 14: 'crosswalk',
    15: 'vehicle', 16: 'vehicle', 17: 'bench', 18: 'cone', 19: 'fire hydrant', 20: 'cone',
    21: 'plant pot', 22: 'electrical box', 23: 'chair', 24: 'bike rack'}
UOD = {0: 'animal', 1: 'hole', 2: 'trash can', 3: 'hole', 4: 'curb', 5: 'fence', 6: 'hole', 7: 'pole'}
COCO = {0: 'person', 1: 'bicycle', 2: 'vehicle', 3: 'vehicle', 5: 'vehicle', 7: 'vehicle',
        10: 'fire hydrant', 13: 'bench', 15: 'animal', 16: 'animal', 24: 'bag', 26: 'bag', 28: 'bag',
        32: 'ball', 36: 'skateboard', 56: 'chair', 58: 'plant pot'}
COCO_WANT = {24, 26, 28, 32, 36}   # a COCO photo is only taken if it shows one of these floor things

def remap(txt, table):
    out = []
    for line in txt.splitlines():
        p = line.split()
        if len(p) < 5:
            continue
        name = table.get(int(float(p[0])))
        if name is None:
            continue
        xywh = p[1:5]   # polygons -> keep only boxes
        out.append(f"{N[name]} {' '.join(xywh)}")
    return out

def put(out, split, stem, img_src, lines):
    (out / split / 'images').mkdir(parents=True, exist_ok=True)
    (out / split / 'labels').mkdir(parents=True, exist_ok=True)
    ext = Path(img_src).suffix.lower() if isinstance(img_src, (str, Path)) else '.jpg'
    dst = out / split / 'images' / (stem + ext)
    if isinstance(img_src, bytes):
        dst.write_bytes(img_src)
    else:
        shutil.copyfile(img_src, dst)
    (out / split / 'labels' / (stem + '.txt')).write_text('\n'.join(lines) + ('\n' if lines else ''))

def hf(repo, root):
    """git-lfs clone: one batched download instead of ~50,000 separate file requests (much faster)"""
    import subprocess
    dst = root / repo.split('/')[1]
    if not (dst / '.git').exists():
        env = dict(os.environ, GIT_LFS_SKIP_SMUDGE='1')
        subprocess.run(['git', 'clone', '-q', '--depth', '1', f'https://huggingface.co/datasets/{repo}', str(dst)], check=True, env=env)
    subprocess.run(['git', '-C', str(dst), 'config', 'lfs.concurrenttransfers', '64'], check=True)
    subprocess.run(['git', '-C', str(dst), 'lfs', 'pull'], check=True)
    return dst

def add_yolo_set(src, table, prefix, out, splitmap):
    n = 0
    for s_in, s_out in splitmap.items():
        for img in sorted(f for f in (src / s_in / 'images').rglob('*') if f.is_file()):
            rel = img.relative_to(src / s_in / 'images')   # some sets use numbered subfolders
            lab = (src / s_in / 'labels' / rel).with_suffix('.txt')
            lines = remap(lab.read_text(), table) if lab.exists() else []
            put(out, s_out, f'{prefix}_{img.stem}', img, lines)
            n += 1
    print(prefix, n, 'images', flush=True)

def add_coco(out, raw, limit):
    zp = raw / 'coco2017labels.zip'
    if not zp.exists():
        urllib.request.urlretrieve('https://github.com/ultralytics/assets/releases/download/v0.0.0/coco2017labels.zip', zp)
    z = zipfile.ZipFile(zp)
    picks = []
    for name in z.namelist():
        if name.startswith('coco/labels/train2017/') and name.endswith('.txt'):
            txt = z.read(name).decode()
            ids = {int(l.split()[0]) for l in txt.splitlines() if l.strip()}
            if ids & COCO_WANT:
                picks.append((Path(name).stem, txt))
    random.Random(0).shuffle(picks)
    picks = picks[:limit]
    print('coco picked', len(picks), flush=True)

    def fetch(item):
        stem, txt = item
        for _ in range(3):
            try:
                return stem, txt, urllib.request.urlopen(f'http://images.cocodataset.org/train2017/{stem}.jpg', timeout=30).read()
            except Exception:
                pass
        return stem, txt, None
    got = 0
    with cf.ThreadPoolExecutor(32) as ex:
        for i, (stem, txt, data) in enumerate(ex.map(fetch, picks)):
            if data is None:
                continue
            split = 'valid' if i % 10 == 0 else 'train'
            put(out, split, f'coco_{stem}', data, remap(txt, COCO))
            got += 1
    print('coco', got, 'images', flush=True)

def main():
    out = Path(sys.argv[1]); raw = out.parent / (out.name + '_raw')
    limit = int(sys.argv[sys.argv.index('--coco') + 1]) if '--coco' in sys.argv else 8000
    raw.mkdir(parents=True, exist_ok=True)
    if out.exists():
        shutil.rmtree(out)
    add_yolo_set(hf('Abtinzandi/Obstacle-Detection-Dataset-YOLO', raw), ROD, 'rod', out,
                 {'train': 'train', 'valid': 'valid', 'test': 'test'})
    add_yolo_set(hf('Dderedor/obstacles_on_the_road', raw) / 'unified_obstacles', UOD, 'uod', out,
                 {'train': 'train', 'valid': 'valid'})
    if limit:
        add_coco(out, raw, limit)
    yaml = [f'path: {out.resolve()}', 'train: train/images', 'val: valid/images', 'test: test/images',
            f'nc: {len(NAMES)}', 'names:'] + [f'  {i}: {n}' for i, n in enumerate(NAMES)]
    (out / 'data.yaml').write_text('\n'.join(yaml) + '\n')
    # box counts so we can see the balance
    cnt = [0] * len(NAMES)
    for lab in out.glob('*/labels/*.txt'):
        for l in lab.read_text().splitlines():
            cnt[int(l.split()[0])] += 1
    for i, n in enumerate(NAMES):
        print(f'{n:15s} {cnt[i]}')

if __name__ == '__main__':
    main()
