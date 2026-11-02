# سازنده آیکون: یک طرح، خروجی وکتور اندروید و SVG
import math, sys, os
res = sys.argv[1]; docs = sys.argv[2]
CX, CY, R = 49.0, 59.0, 17.0          # دایره تیک
ARCS = [(25.0, 1.0), (32.5, 0.62)]    # کمان‌های سیگنال (شعاع، شفافیت)
A0, A1 = -80, -12                     # زاویه کمان‌ها (درجه)
PRIMARY = "#4338CA"
G = ["#7C6CF7", "#4F46E5", "#3226A8"]  # گرادیان پس‌زمینه

def pt(r, a):
    a = math.radians(a); return CX + r*math.cos(a), CY + r*math.sin(a)
def arc(r):
    x0,y0 = pt(r,A0); x1,y1 = pt(r,A1)
    return f"M{x0:.2f},{y0:.2f} A{r:.1f},{r:.1f} 0 0,1 {x1:.2f},{y1:.2f}"
circle = f"M{CX-R:.1f},{CY:.1f} a{R:.1f},{R:.1f} 0 1,0 {2*R:.1f},0 a{R:.1f},{R:.1f} 0 1,0 {-2*R:.1f},0 z"
check = f"M{CX-8.5:.1f},{CY+0.5:.1f} L{CX-2.5:.1f},{CY+6.5:.1f} L{CX+9:.1f},{CY-5.5:.1f}"
# برای بررسی منطقه امن (دایره ۳۳ از مرکز)
far = max(math.dist((54,54), pt(r,a)) for r,_ in ARCS for a in range(A0, A1+1))
print("max distance from center:", round(far+2,1), "(safe <= 33)")

HDR = '''<?xml version="1.0" encoding="utf-8"?>
<!-- آیکون برنامه (ساخته‌شده با docs/icon/make_icon.py): تیک حضور با امواج بلوتوث -->
'''
def vec(body, extra=""):
    return HDR + f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"{extra}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}</vector>
'''
def stroke(d, color, width, alpha=1.0):
    a = f'\n        android:strokeAlpha="{alpha}"' if alpha != 1 else ""
    return f'''    <path
        android:pathData="{d}"
        android:strokeColor="{color}"
        android:strokeWidth="{width}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"{a} />
'''
def fill(d, color, alpha=1.0):
    a = f'\n        android:fillAlpha="{alpha}"' if alpha != 1 else ""
    return f'''    <path
        android:pathData="{d}"
        android:fillColor="{color}"{a} />
'''
arcs = "".join(stroke(arc(r), "#FFFFFF", 4.6, al) for r, al in ARCS)
# سایه نرم زیر دایره
shadow = fill(f"M{CX-R:.1f},{CY+1.6:.1f} a{R:.1f},{R:.1f} 0 1,0 {2*R:.1f},0 a{R:.1f},{R:.1f} 0 1,0 {-2*R:.1f},0 z", "#1E1B4B", 0.25)
fg = vec(arcs + shadow + fill(circle, "#FFFFFF") + stroke(check, PRIMARY, 4.8))
bg = vec(f'''    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0" android:startY="0"
                android:endX="108" android:endY="108">
                <item android:offset="0" android:color="{G[0]}" />
                <item android:offset="0.5" android:color="{G[1]}" />
                <item android:offset="1" android:color="{G[2]}" />
            </gradient>
        </aapt:attr>
    </path>
''' + fill("M-20,40 a60,60 0 0,1 100,-60 l-100,0 z", "#FFFFFF", 0.07), '\n    xmlns:aapt="http://schemas.android.com/aapt"')
# آیکون تک‌رنگ (Themed icon اندروید ۱۳): دایره توخالی و تیک
mono = vec(arcs.replace('android:strokeAlpha="0.62"', 'android:strokeAlpha="0.7"') + stroke(circle, "#FFFFFF", 4.2) + stroke(check, "#FFFFFF", 4.8))

open(os.path.join(res, "drawable/ic_launcher_foreground.xml"), "w", encoding="utf-8", newline="\n").write(fg)
open(os.path.join(res, "drawable/ic_launcher_background.xml"), "w", encoding="utf-8", newline="\n").write(bg)
open(os.path.join(res, "drawable/ic_launcher_monochrome.xml"), "w", encoding="utf-8", newline="\n").write(mono)

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{G[0]}"/><stop offset="0.5" stop-color="{G[1]}"/><stop offset="1" stop-color="{G[2]}"/>
    </linearGradient>
    <clipPath id="c"><rect width="108" height="108" rx="24"/></clipPath>
  </defs>
  <g clip-path="url(#c)">
    <rect width="108" height="108" fill="url(#g)"/>
    <path d="M-20,40 a60,60 0 0,1 100,-60 l-100,0 z" fill="#fff" fill-opacity="0.07"/>
  </g>
  <g fill="none" stroke="#fff" stroke-width="4.6" stroke-linecap="round">
    {"".join(f'<path d="{arc(r)}" stroke-opacity="{al}"/>' for r, al in ARCS)}
  </g>
  <circle cx="{CX}" cy="{CY+1.6}" r="{R}" fill="#1E1B4B" fill-opacity="0.25"/>
  <circle cx="{CX}" cy="{CY}" r="{R}" fill="#fff"/>
  <path d="{check}" fill="none" stroke="{PRIMARY}" stroke-width="4.8" stroke-linecap="round" stroke-linejoin="round"/>
</svg>
'''
os.makedirs(docs, exist_ok=True)
open(os.path.join(docs, "icon.svg"), "w", encoding="utf-8", newline="\n").write(svg)
