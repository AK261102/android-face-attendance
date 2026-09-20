# tools

## `validate_embeddings.py`

Checks that `facenet_512.tflite`, with the preprocessing `FaceEmbedder.kt` applies,
actually separates two different people — the failure this guards against is a
preprocessing mismatch, where the model still returns 512 valid-looking floats but they
collapse so that everyone matches everyone.

```bash
pip install ai-edge-litert pillow numpy
# place obama.jpg, obama2.jpg, biden.jpg in ./faces (see BOXES for the crop used)
python tools/validate_embeddings.py
```

Measured result that set `FaceMatcher.MATCH_THRESHOLD`:

```
 standardize:  same=+0.7418   diff=+0.0210/-0.0009   gap=+0.7208
        unit:  same=+0.7869   diff=+0.0750/+0.1245   gap=+0.6624
   symmetric:  same=+0.7966   diff=-0.0118/+0.1194   gap=+0.6771
```

`standardize` is the mode the app uses. Same person 0.74, different people ~0.0 — so the
0.60 threshold sits far above the different-person cluster with room below genuine matches.
