#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Render the generated icon/logo SVGs to a PNG contact sheet so the design can be
eyeballed without a browser. Implements just enough SVG: <defs> linearGradient,
<path> (M L H V C S Q T A Z, abs+rel), <g transform=translate/scale>, fill solid|
gradient, and round strokes. Nonzero-winding fill (matches Android VectorDrawable).

Run: python3 vendor/ruos/branding/preview.py   ->  branding/preview.png
"""
import os, re, math
from PIL import Image, ImageDraw
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
SS = 4  # supersample

NUM = re.compile(r'[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?')
CMD = re.compile(r'([MmLlHhVvCcSsQqTtAaZz])')

def tokenize(d):
    out, i = [], 0
    parts = CMD.split(d)
    for p in parts:
        p = p.strip()
        if not p: continue
        if p in "MmLlHhVvCcSsQqTtAaZz": out.append(p)
        else: out.extend(float(x) for x in NUM.findall(p))
    return out

def flatten(d, steps=24):
    """Return list of subpaths; each is a list of (x,y)."""
    toks = tokenize(d); i = 0
    subs, cur = [], []
    x = y = 0.0; sx = sy = 0.0; cmd = None; prev_ctrl = None
    def emit(px, py):
        cur.append((px, py))
    while i < len(toks):
        t = toks[i]
        if isinstance(t, str): cmd = t; i += 1
        # implicit repeat keeps cmd
        if cmd in "Mm":
            nx, ny = toks[i], toks[i+1]; i += 2
            if cmd == 'm': nx, ny = x+nx, y+ny
            if cur: subs.append(cur); cur = []
            x, y = nx, ny; sx, sy = x, y; emit(x, y); cmd = 'L' if cmd=='M' else 'l'
        elif cmd in "Ll":
            nx, ny = toks[i], toks[i+1]; i += 2
            if cmd == 'l': nx, ny = x+nx, y+ny
            x, y = nx, ny; emit(x, y)
        elif cmd in "Hh":
            nx = toks[i]; i += 1
            x = x+nx if cmd=='h' else nx; emit(x, y)
        elif cmd in "Vv":
            ny = toks[i]; i += 1
            y = y+ny if cmd=='v' else ny; emit(x, y)
        elif cmd in "Cc":
            x1,y1,x2,y2,nx,ny = toks[i:i+6]; i += 6
            if cmd=='c': x1,y1,x2,y2,nx,ny = x+x1,y+y1,x+x2,y+y2,x+nx,y+ny
            for k in range(1,steps+1):
                u=k/steps; mu=1-u
                bx=mu**3*x+3*mu**2*u*x1+3*mu*u**2*x2+u**3*nx
                by=mu**3*y+3*mu**2*u*y1+3*mu*u**2*y2+u**3*ny
                emit(bx,by)
            prev_ctrl=(x2,y2); x,y=nx,ny
        elif cmd in "Ss":
            x2,y2,nx,ny = toks[i:i+4]; i += 4
            if cmd=='s': x2,y2,nx,ny = x+x2,y+y2,x+nx,y+ny
            x1,y1 = (2*x-prev_ctrl[0],2*y-prev_ctrl[1]) if prev_ctrl else (x,y)
            for k in range(1,steps+1):
                u=k/steps; mu=1-u
                bx=mu**3*x+3*mu**2*u*x1+3*mu*u**2*x2+u**3*nx
                by=mu**3*y+3*mu**2*u*y1+3*mu*u**2*y2+u**3*ny
                emit(bx,by)
            prev_ctrl=(x2,y2); x,y=nx,ny
        elif cmd in "Qq":
            x1,y1,nx,ny = toks[i:i+4]; i += 4
            if cmd=='q': x1,y1,nx,ny = x+x1,y+y1,x+nx,y+ny
            for k in range(1,steps+1):
                u=k/steps; mu=1-u
                bx=mu**2*x+2*mu*u*x1+u**2*nx
                by=mu**2*y+2*mu*u*y1+u**2*ny
                emit(bx,by)
            prev_ctrl=(x1,y1); x,y=nx,ny
        elif cmd in "Tt":
            nx,ny = toks[i:i+2]; i += 2
            if cmd=='t': nx,ny=x+nx,y+ny
            x1,y1=(2*x-prev_ctrl[0],2*y-prev_ctrl[1]) if prev_ctrl else (x,y)
            for k in range(1,steps+1):
                u=k/steps; mu=1-u
                bx=mu**2*x+2*mu*u*x1+u**2*nx
                by=mu**2*y+2*mu*u*y1+u**2*ny
                emit(bx,by)
            prev_ctrl=(x1,y1); x,y=nx,ny
        elif cmd in "Aa":
            rx,ry,rot,laf,sf,nx,ny = toks[i:i+7]; i += 7
            if cmd=='a': nx,ny=x+nx,y+ny
            pts=arc_to(x,y,rx,ry,rot,laf,sf,nx,ny,steps)
            for p in pts: emit(*p)
            x,y=nx,ny
        elif cmd in "Zz":
            emit(sx,sy)
            if cur: subs.append(cur); cur=[]
        else:
            i += 1
    if cur: subs.append(cur)
    return subs

def arc_to(x1,y1,rx,ry,phi,laf,sf,x2,y2,steps):
    if rx==0 or ry==0: return [(x2,y2)]
    phi=math.radians(phi)
    dx,dy=(x1-x2)/2,(y1-y2)/2
    x1p= math.cos(phi)*dx+math.sin(phi)*dy
    y1p=-math.sin(phi)*dx+math.cos(phi)*dy
    rx,ry=abs(rx),abs(ry)
    lam=x1p**2/rx**2+y1p**2/ry**2
    if lam>1: s=math.sqrt(lam); rx*=s; ry*=s
    sign=-1 if laf==sf else 1
    num=rx**2*ry**2-rx**2*y1p**2-ry**2*x1p**2
    den=rx**2*y1p**2+ry**2*x1p**2
    co=sign*math.sqrt(max(0,num/den)) if den else 0
    cxp=co*rx*y1p/ry; cyp=-co*ry*x1p/rx
    cx=math.cos(phi)*cxp-math.sin(phi)*cyp+(x1+x2)/2
    cy=math.sin(phi)*cxp+math.cos(phi)*cyp+(y1+y2)/2
    def ang(ux,uy,vx,vy):
        d=math.sqrt((ux*ux+uy*uy)*(vx*vx+vy*vy))
        c=max(-1,min(1,(ux*vx+uy*vy)/d)); a=math.acos(c)
        if ux*vy-uy*vx<0: a=-a
        return a
    th1=ang(1,0,(x1p-cxp)/rx,(y1p-cyp)/ry)
    dth=ang((x1p-cxp)/rx,(y1p-cyp)/ry,(-x1p-cxp)/rx,(-y1p-cyp)/ry)
    if not sf and dth>0: dth-=2*math.pi
    if sf and dth<0: dth+=2*math.pi
    pts=[]
    for k in range(1,steps+1):
        th=th1+dth*k/steps
        px=math.cos(phi)*rx*math.cos(th)-math.sin(phi)*ry*math.sin(th)+cx
        py=math.sin(phi)*rx*math.cos(th)+math.cos(phi)*ry*math.sin(th)+cy
        pts.append((px,py))
    return pts

def fill_mask(subs, size):
    """Nonzero-winding scanline fill -> 'L' mask at `size` px."""
    mask=Image.new("L",(size,size),0)
    px=mask.load()
    edges=[]
    for sub in subs:
        n=len(sub)
        for k in range(n):
            x0,y0=sub[k]; x1,y1=sub[(k+1)%n]
            if y0==y1: continue
            edges.append((x0,y0,x1,y1))
    for y in range(size):
        yc=y+0.5; xs=[]
        for (x0,y0,x1,y1) in edges:
            if (y0<=yc<y1) or (y1<=yc<y0):
                t=(yc-y0)/(y1-y0); xint=x0+t*(x1-x0)
                xs.append((xint,1 if y1>y0 else -1))
        xs.sort()
        w=0
        for j in range(len(xs)-1):
            w+=xs[j][1]
            if w!=0:
                a=int(math.ceil(xs[j][0]-0.5)); b=int(math.floor(xs[j+1][0]-0.5))
                for xx in range(max(0,a),min(size-1,b)+1): px[xx,y]=255
    return mask

def grad_image(g, size):
    x1,y1,x2,y2,stops=g
    img=Image.new("RGB",(size,size))
    pix=img.load()
    dx,dy=x2-x1,y2-y1; L=dx*dx+dy*dy
    def lerp(c1,c2,t): return tuple(int(c1[i]+(c2[i]-c1[i])*t) for i in range(3))
    def hx(c): c=c.lstrip('#'); return (int(c[0:2],16),int(c[2:4],16),int(c[4:6],16))
    sc=[(o,hx(c)) for o,c in stops]
    for yy in range(size):
        for xx in range(size):
            t=0 if L==0 else ((xx/SS-x1)*dx+(yy/SS-y1)*dy)/L
            t=max(0,min(1,t))
            # piecewise
            col=sc[0][1]
            for k in range(len(sc)-1):
                o0,c0=sc[k]; o1,c1=sc[k+1]
                if t<=o1:
                    tt=0 if o1==o0 else (t-o0)/(o1-o0); col=lerp(c0,c1,max(0,min(1,tt))); break
                col=c1
            pix[xx,yy]=col
    return img

def render_svg(path, out_px=216):
    tree=ET.parse(path); root=tree.getroot()
    ns='{http://www.w3.org/2000/svg}'
    vb=root.get('viewBox','0 0 108 108').split()
    vw=float(vb[2]); scale=out_px/vw*SS; size=int(out_px*SS)
    canvas=Image.new("RGBA",(size,size),(0,0,0,0))
    grads={}
    defs=root.find(ns+'defs')
    if defs is not None:
        for lg in defs.findall(ns+'linearGradient'):
            stops=[(float(s.get('offset')),s.get('stop-color')) for s in lg.findall(ns+'stop')]
            grads[lg.get('id')]=(float(lg.get('x1')),float(lg.get('y1')),
                                 float(lg.get('x2')),float(lg.get('y2')),stops)
    def draw_path(el, tf):
        d=el.get('d');
        if not d: return
        subs=flatten(d)
        def ap(p):
            x,y=p
            if tf:
                tx,ty,s=tf; x,y=tx+x*s, ty+y*s
            return (x*scale, y*scale)
        subs=[[ap(p) for p in s] for s in subs]
        fillv=el.get('fill','#000000'); stroke=el.get('stroke')
        if stroke and stroke!='none':
            w=float(el.get('stroke-width',1))*scale
            dr=ImageDraw.Draw(canvas)
            for s in subs:
                for k in range(len(s)-1):
                    dr.line([s[k],s[k+1]],fill=stroke,width=max(1,int(w)))
                for p in s:
                    r=w/2
                    dr.ellipse([p[0]-r,p[1]-r,p[0]+r,p[1]+r],fill=stroke)
            return
        if fillv=='none': return
        m=fill_mask(subs,size)
        if fillv.startswith('url('):
            gid=fillv[5:-1]; gi=grad_image(grads[gid],size).convert("RGBA")
            canvas.paste(gi,(0,0),m)
        else:
            solid=Image.new("RGBA",(size,size),hexrgba(fillv))
            canvas.paste(solid,(0,0),m)
    def hexrgba(c):
        c=c.lstrip('#'); return (int(c[0:2],16),int(c[2:4],16),int(c[4:6],16),255)
    for el in root:
        tag=el.tag.replace(ns,'')
        if tag=='path': draw_path(el,None)
        elif tag=='g':
            tr=el.get('transform','')
            mt=re.search(r'translate\(([-\d.]+),([-\d.]+)\)\s*scale\(([-\d.]+)\)',tr)
            tf=(float(mt.group(1)),float(mt.group(2)),float(mt.group(3))) if mt else None
            for ch in el:
                if ch.tag==ns+'path': draw_path(ch,tf)
    return canvas.resize((out_px,out_px),Image.LANCZOS)

def main():
    svgs=sorted(__import__('glob').glob(os.path.join(HERE,'icons','svg','*.svg')))
    cell=216; pad=24; cols=4
    rows=(len(svgs)+cols-1)//cols
    W=cols*cell+(cols+1)*pad; H=rows*cell+(rows+1)*pad+80
    sheet=Image.new("RGB",(W,H),(20,20,22))
    for idx,sp in enumerate(svgs):
        r,c=divmod(idx,cols)
        ic=render_svg(sp,cell)
        x=pad+c*(cell+pad); y=pad+r*(cell+pad)
        sheet.paste(ic,(x,y),ic)
    # logo strip
    wm=render_svg(os.path.join(HERE,'logo','ruos_symbol.svg'),120)
    sheet.paste(wm,(pad,H-120-pad),wm)
    out=os.path.join(HERE,'preview.png'); sheet.save(out)
    print("wrote",out, sheet.size)

if __name__=='__main__': main()
