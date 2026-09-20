"""
Validates the FaceEmbedder pipeline end to end: does this model, with the preprocessing
the Kotlin uses, actually separate two people? Crop boxes were read off the images by eye
(fractions of width/height), standing in for ML Kit's detector.
"""
import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter

interp = Interpreter(model_path="facenet_512.tflite")
interp.allocate_tensors()
inp, out = interp.get_input_details()[0], interp.get_output_details()[0]
SIZE = int(inp["shape"][1])
print(f"model input {inp['shape']} {inp['dtype'].__name__} | output {out['shape']}\n")

# (cx, cy, side) as fractions — cx/side of width, cy of height
BOXES = {
    "obama":  (0.544, 0.205, 0.44),
    "obama2": (0.570, 0.341, 0.70),
    "biden":  (0.582, 0.160, 0.49),
}

def crop(name):
    img = Image.open(f"faces/{name}.jpg").convert("RGB")
    W, H = img.size
    fcx, fcy, fs = BOXES[name]
    cx, cy, side = fcx * W, fcy * H, fs * W
    box = (cx - side/2, cy - side/2, cx + side/2, cy + side/2)
    c = img.crop(tuple(int(v) for v in box)).resize((SIZE, SIZE), Image.BILINEAR)
    c.save(f"faces/{name}_crop.png")
    return np.asarray(c, dtype=np.float32)

def embed(a, mode):
    if mode == "standardize":                       # what FaceEmbedder.kt does
        std = max(a.std(), 1.0 / np.sqrt(a.size))
        a = (a - a.mean()) / std
    elif mode == "unit":
        a = a / 255.0
    elif mode == "symmetric":
        a = (a - 127.5) / 128.0
    interp.set_tensor(inp["index"], a[None, ...])
    interp.invoke()
    v = interp.get_tensor(out["index"])[0]
    return v / np.linalg.norm(v)

crops = {n: crop(n) for n in BOXES}
for mode in ("standardize", "unit", "symmetric"):
    o1, o2, b = (embed(crops[n], mode) for n in ("obama", "obama2", "biden"))
    same = float(o1 @ o2)
    d1, d2 = float(o1 @ b), float(o2 @ b)
    gap = same - max(d1, d2)
    flag = "  <-- COLLAPSED" if max(d1, d2) > 0.9 else ("  <-- good separation" if gap > 0.2 else "")
    print(f"{mode:>12}:  same={same:+.4f}   diff={d1:+.4f}/{d2:+.4f}   gap={gap:+.4f}{flag}")
