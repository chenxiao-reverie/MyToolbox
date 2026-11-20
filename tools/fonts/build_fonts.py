"""把 photo-diary 的 fontsource woff2 切片合并成 Android 可用的 TTF。

用法：
    # 先取字体源（约 55MB zip）
    curl -L -o pd.zip https://codeload.github.com/chenxiao-reverie/photo-diary/zip/refs/heads/main
    unzip -q pd.zip 'photo-diary-main/fonts/*'

    # 逐族构建（输出到 feature/diary/src/main/assets/fonts/）
    python3 tools/fonts/build_fonts.py photo-diary-main/fonts/LXGWWenKaiScreen \
        feature/diary/src/main/assets/fonts/LXGWWenKaiScreen.ttf "LXGW WenKai Screen"

依赖：pip install fonttools brotli
说明：按 GB2312（6763 字）+ ASCII + 全角标点子集化；丢弃 GSUB/GPOS 以便 merge。
"""

import os, sys, glob, time, tempfile
from fontTools.ttLib import TTFont
from fontTools import subset
from fontTools.merge import Merger

CHARS = set()
for cp in range(0x20, 0x7F): CHARS.add(chr(cp))
for cp in range(0x4E00, 0x9FA6):
    ch = chr(cp)
    try:
        ch.encode('gb2312'); CHARS.add(ch)
    except Exception:
        pass
for cp in list(range(0x3000, 0x3040)) + list(range(0xFF00, 0xFFF0)) + [0x2018,0x2019,0x201C,0x201D,0x2026,0x2014,0x00B7]:
    CHARS.add(chr(cp))
TEXT = "".join(CHARS)

def build(family_dir, out_path, family_name):
    files = sorted(glob.glob(os.path.join(family_dir, "files", "*.woff2")))
    tmpdir = tempfile.mkdtemp(prefix="sub_", dir="/tmp/fontwork2")
    paths = []
    for i, f in enumerate(files):
        try:
            font = TTFont(f)
            opts = subset.Options()
            opts.name_IDs = ['*']; opts.name_legacy = True; opts.name_languages = ['*']
            opts.notdef_outline = True; opts.recalc_bounds = True
            opts.drop_tables += ['DSIG','GSUB','GPOS','GDEF','FFTM','feat','morx']
            sub = subset.Subsetter(options=opts)
            sub.populate(text=TEXT)
            sub.subset(font)
            if len(font.getGlyphOrder()) > 1:
                font.flavor = None
                p = os.path.join(tmpdir, f"{i:03d}.ttf")
                font.save(p)
                paths.append(p)
        except Exception as e:
            print("  skip", os.path.basename(f), str(e)[:80])
    print(f"  {family_name}: {len(paths)} slices")
    merged = Merger().merge(paths)
    for rec in merged['name'].names:
        if rec.nameID in (1, 4, 6, 16, 17):
            try: rec.string = family_name
            except Exception: pass
    merged.flavor = None
    merged.save(out_path)
    print("  ->", out_path, round(os.path.getsize(out_path)/1024/1024, 2), "MB")

if __name__ == "__main__":
    t0=time.time()
    build(sys.argv[1], sys.argv[2], sys.argv[3])
    print("elapsed", round(time.time()-t0,1), "s")
