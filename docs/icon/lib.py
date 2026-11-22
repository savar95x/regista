import subprocess
from PIL import Image
BG="#1C1C1E"; C="#EEE7DC"; A="#E2957A"; W=8.2
def render(inner, name, s, px=300):
    v=f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="19 19 70 70" width="{px}" height="{px}">
<defs><clipPath id="c"><rect x="19" y="19" width="70" height="70" rx="24"/></clipPath></defs><g clip-path="url(#c)"><rect width="108" height="108" fill="{BG}"/>
<g transform="translate(54 54) scale({s}) translate(-54 -54)">{inner}</g></g></svg>'''
    open(name+".svg","w").write(v); subprocess.run(["rsvg-convert",name+".svg","-o",name+".png"],check=True)
    im=Image.open(name+".png").convert('RGBA'); p=im.load(); k=px/180
    pts=[(x,y) for y in range(px) for x in range(px) if p[x,y][3]>0 and max(p[x,y][:3])>120]
    xs=[a for a,_ in pts]; ys=[b for _,b in pts]
    return name+".png", f"{(max(xs)-min(xs)+1)/k:.0f}x{(max(ys)-min(ys)+1)/k:.0f} · {len(pts)/k/k:.0f}", pts
def label(f, t, m):
    o=f.replace(".png","_l.png")
    subprocess.run(["convert",f,"-background","#2a332a","-gravity","south","-splice","0x70","-fill","#ddd","-pointsize","22","-annotate","+0+36",t,"-fill","#999","-pointsize","17","-annotate","+0+10",m,o],check=True); return o
def fit(inner, target=79):
    lo,hi=0.4,1.2
    for _ in range(12):
        s=(lo+hi)/2; _,m,_p=render(inner,"tmp_fit",s,px=180)
        w,h=map(int,m.split(" ")[0].split("x"))
        if max(w,h)>target: hi=s
        else: lo=s
    return lo
def optical(inner, s, amount=0.5):
    _f,_m,pts=render(inner,"tmp_opt",s,px=360)
    xs=[a for a,_ in pts]; ys=[b for _,b in pts]
    bx=(min(xs)+max(xs))/2; by=(min(ys)+max(ys))/2; mx=sum(xs)/len(xs); my=sum(ys)/len(ys)
    k=360/70*s
    return f'<g transform="translate({(180-(bx+(mx-bx)*amount))/k:.3f} {(180-(by+(my-by)*amount))/k:.3f})">{inner}</g>'
aria=f'<g stroke-linecap="round" stroke-width="8.2"><path d="M36.9,46.6L36.9,61.4" stroke="{C}"/><path d="M48.3,37.5L48.3,70.5" stroke="{C}"/><path d="M59.7,42.05L59.7,65.95" stroke="{A}"/><path d="M71.1,50.1L71.1,57.9" stroke="{C}"/></g>'
hia=f'<path d="M72,54A18,18 0,1 1,54 36" fill="none" stroke="{C}" stroke-width="8.2" stroke-linecap="round"/><circle cx="66.73" cy="41.27" r="4.1" fill="{A}"/>'
minu=open('minutia.svg').read().split('<rect width="108" height="108" fill="#1C1C1E"/>')[1].split('</g></svg>')[0]
FAMILY=[("Aria",aria,0.75),("Minutia",minu,0.75),("Hiatus",hia,0.7)]
def sheet(variants, out):
    big=[];small=[]
    for n,(inner,s) in variants.items():
        f,m,_=render(inner,"x_"+str(len(big)),s); big.append(label(f,n,m)); small.append(render(inner,"xs_"+str(len(small)),s,px=120)[0]); print(n,round(s,3),m)
    fam=[label(*render(i,"f_"+n,s)[:2][:1], n, render(i,"f_"+n,s)[1]) for n,i,s in FAMILY]
    subprocess.run(["convert",*fam,"-background","#2a332a","-splice","16x0","+append","s0.png"],check=True)
    subprocess.run(["convert",*big,"-background","#2a332a","-splice","16x0","+append","s1.png"],check=True)
    sm=[render(i,"fs_"+n,s,px=120)[0] for n,i,s in FAMILY]+small
    subprocess.run(["convert",*sm,"-background","#2a332a","-splice","24x0","+append","-splice","0x24","s2.png"],check=True)
    subprocess.run(["convert","s0.png","s1.png","s2.png","-background","#2a332a","-splice","0x12","-append",out],check=True)

import math
def stroke(d,col=C,cap="round",w=W): return f'<path d="{d}" fill="none" stroke="{col}" stroke-width="{w}" stroke-linecap="{cap}" stroke-linejoin="round"/>'
def snail(r=14, cx=46, cy=58, tip_x=73, tip_drop=6.5, end_deg=-25, pea=4.6, w=W):
    top=cy-r; ex=cx+r*math.cos(math.radians(end_deg)); ey=cy-r*math.sin(math.radians(end_deg))
    d=f"M{tip_x},{top+tip_drop} L{tip_x},{top} L{cx},{top} A{r},{r} 0 1 0 {ex:.2f},{ey:.2f}"
    return f'<path d="{d}" fill="none" stroke="{C}" stroke-width="{w}" stroke-linejoin="miter" stroke-linecap="round"/><circle cx="{cx}" cy="{cy}" r="{pea}" fill="{A}"/>'
def arcd(r,a0,a1,cx=54,cy=54,sweep=1):
    x0=cx+r*math.cos(math.radians(a0)); y0=cy+r*math.sin(math.radians(a0)); x1=cx+r*math.cos(math.radians(a1)); y1=cy+r*math.sin(math.radians(a1))
    return f"M{x0:.2f},{y0:.2f} A{r},{r} 0 0 {sweep} {x1:.2f},{y1:.2f}"
def rot(a): return lambda x,y:(54+x*math.cos(math.radians(a))-y*math.sin(math.radians(a)), 54+x*math.sin(math.radians(a))+y*math.cos(math.radians(a)))
