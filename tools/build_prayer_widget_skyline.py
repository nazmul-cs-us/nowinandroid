from PIL import Image, ImageDraw
import math, random

W, H = 1536, 427
CREAM=(238,231,215); CREAM_D=(219,208,184); SAND=(226,214,190)
SHADE=(203,189,162); GREEN=(74,120,62); GREEN_D=(60,100,50)
GREY=(203,209,220); GREY_D=(178,186,202); GOLD=(226,181,62); DARK=(104,93,78)
def tint(c,t,to=(255,255,255)): return tuple(int(c[i]+(to[i]-c[i])*t) for i in range(3))

# A real onion profile: bulges below the middle, then draws to a point.
ONION = [(0.00,0.88),(0.08,0.99),(0.20,1.05),(0.32,1.02),(0.45,0.92),
         (0.57,0.77),(0.68,0.60),(0.78,0.43),(0.86,0.29),(0.93,0.16),(1.00,0.02)]

img = Image.new('RGBA',(W,H),(0,0,0,0)); d = ImageDraw.Draw(img)

def dome(cx, base, r, col, shade):
    h = r*1.95
    left  = [(cx-w*r, base-t*h) for t,w in ONION]
    right = [(cx+w*r, base-t*h) for t,w in reversed(ONION)]
    d.polygon(left+right, fill=col)
    # shading as a vertical sliver on the right, following the same profile
    band = [(cx+w*r*0.30, base-t*h) for t,w in ONION] + \
           [(cx+w*r, base-t*h) for t,w in reversed(ONION)]
    d.polygon(band, fill=shade)
    d.polygon([(cx-r*0.10, base-h), (cx+r*0.10, base-h), (cx, base-h-r*0.42)], fill=GOLD)
    d.ellipse([cx-r*0.10, base-h-r*0.20, cx+r*0.10, base-h], fill=GOLD)

TRUNK=(146,118,84); TRUNK_D=(122,96,66)
LEAF=(72,116,58); LEAF_D=(54,92,44); DATE=(190,132,52)

def frond(crown, ang, length, width, col, droop=0.55):
    """A tapered, drooping blade: sampled along a curve, offset either side."""
    pts_a=[]; pts_b=[]
    for i in range(13):
        u = i/12
        # straight out, then bending down under its own weight
        x = crown[0] + math.cos(math.radians(ang))*length*u
        y = crown[1] + math.sin(math.radians(ang))*length*u + droop*length*(u**2.1)
        wd = width*(1-u)**0.75
        # perpendicular to the local direction
        nx, ny = -math.sin(math.radians(ang)), math.cos(math.radians(ang))
        pts_a.append((x+nx*wd, y+ny*wd)); pts_b.append((x-nx*wd, y-ny*wd))
    d.polygon(pts_a+pts_b[::-1], fill=col)

def palm(cx, base, h, lean=1.0, dates=True, seed=0):
    rnd = random.Random(seed)
    segs=12; th=h*0.66; pl=[]; pr=[]
    for i in range(segs+1):
        t=i/segs; y=base-th*t
        off = math.sin(t*1.5)*h*0.055*lean
        wd  = h*0.030*(1-0.42*t)
        pl.append((cx+off-wd,y)); pr.append((cx+off+wd,y))
    d.polygon(pl+pr[::-1], fill=TRUNK)
    d.polygon([(a[0],a[1]) for a in pl] + [((a[0]+b[0])/2,a[1]) for a,b in zip(pl,pr)][::-1], fill=TRUNK_D)
    for i in range(1, segs, 2):                       # trunk rings
        y = base-th*(i/segs); off = math.sin((i/segs)*1.5)*h*0.055*lean
        d.line([(cx+off-h*0.026, y),(cx+off+h*0.026, y)], fill=TRUNK_D, width=max(1,int(h*0.008)))
    crown = (cx + math.sin(1.5)*h*0.055*lean, base-th)
    n = 9
    for k in range(n):
        ang = -168 + k*(156/(n-1)) + rnd.uniform(-5,5)
        L = h*0.40*rnd.uniform(0.82,1.06)
        col = LEAF if k%2==0 else LEAF_D
        frond(crown, ang, L, h*0.036, col)
    if dates:
        for k in range(7):
            a=rnd.uniform(-150,-30); r=h*0.07*rnd.uniform(0.4,1.0)
            x=crown[0]+math.cos(math.radians(a))*r; y=crown[1]+abs(math.sin(math.radians(a)))*r*0.7+h*0.02
            d.ellipse([x-h*0.012,y-h*0.012,x+h*0.012,y+h*0.012], fill=DATE)
    d.ellipse([crown[0]-h*0.03, crown[1]-h*0.03, crown[0]+h*0.03, crown[1]+h*0.03], fill=LEAF_D)


def arch_mask(size, openings):
    m = Image.new('L', size, 255); md = ImageDraw.Draw(m)
    for x0,y0,x1,y1 in openings:
        rr = (x1-x0)/2
        md.rectangle([x0, y0+rr, x1, y1], fill=0)
        md.pieslice([x0, y0, x1, y0+rr*2], 180, 360, fill=0)
    return m

def arcade(x0, x1, base, h, col, shade, pitch=46):
    # Openings are a dark interior, not a hole: punched through, the card's sky showed
    # between the pillars and the colonnade read as a fence rather than a building.
    d.rectangle([x0, base-h, x1, base], fill=col)
    d.rectangle([x0, base-h, x1, base-h*0.84], fill=shade)
    inner = (150,138,118)
    x = x0+pitch*0.22
    while x < x1-pitch*0.5:
        ow = pitch*0.56; rr = ow/2
        top = base-h*0.70
        d.rectangle([x, top+rr, x+ow, base], fill=inner)
        d.pieslice([x, top, x+ow, top+rr*2], 180, 360, fill=inner)
        x += pitch

def body(x0,y0,x1,y1,col,shade,arches=0):
    d.rectangle([x0,y0,x1,y1], fill=col)
    d.rectangle([x0,y0,x1,y0+(y1-y0)*0.13], fill=shade)
    if arches:
        for i in range(arches):
            cx = x0+(i+0.5)*(x1-x0)/arches; ww=(x1-x0)/arches*0.30
            top=y1-(y1-y0)*0.66; rr=ww/2
            d.rectangle([cx-rr, top+rr, cx+rr, y1-(y1-y0)*0.10], fill=DARK)
            d.pieslice([cx-rr, top, cx+rr, top+rr*2], 180, 360, fill=DARK)

def minaret(cx, base, h, w, col, shade, cap=GREEN, capd=GREEN_D):
    d.polygon([(cx-w*0.62,base),(cx+w*0.62,base),(cx+w*0.62,base-h*0.08),(cx-w*0.62,base-h*0.08)], fill=shade)
    top=base-h*0.70
    d.polygon([(cx-w*0.46,base-h*0.08),(cx+w*0.46,base-h*0.08),(cx+w*0.30,top),(cx-w*0.30,top)], fill=col)
    d.polygon([(cx+w*0.10,base-h*0.08),(cx+w*0.46,base-h*0.08),(cx+w*0.30,top),(cx+w*0.08,top)], fill=shade)
    for f in (0.26,0.46,0.63):
        bw=w*(0.60-f*0.20); y=base-h*f
        d.rectangle([cx-bw,y-h*0.018,cx+bw,y], fill=CREAM_D)
    for f in (0.34,0.54):
        d.rectangle([cx-w*0.08,base-h*(f+0.06),cx+w*0.08,base-h*f], fill=DARK)
    d.rectangle([cx-w*0.34, top-h*0.03, cx+w*0.34, top], fill=CREAM_D)
    dome(cx, top-h*0.03, w*0.30, cap, capd)

BASE = H - 4
far_c, far_s = tint(CREAM,0.40), tint(CREAM_D,0.40)
for cx,hh,ww in ((170,112,13),(1352,130,14),(1470,92,11)):
    minaret(cx,BASE,hh,ww,far_c,far_s,tint(GREEN,0.40),tint(GREEN_D,0.40))
for cx,r in ((292,26),(1256,23)):
    body(cx-r*2.0,BASE-r*0.8,cx+r*2.0,BASE,far_c,far_s)
    dome(cx,BASE-r*0.8,r,tint(GREY,0.40),tint(GREY_D,0.40))

CX=int(W*0.42)
body(CX-296,BASE-88,CX+296,BASE,CREAM,SHADE,arches=7)
for sx in (-186,186): dome(CX+sx,BASE-88,40,GREY,GREY_D)
body(CX-104,BASE-134,CX+104,BASE-84,SAND,SHADE)
dome(CX,BASE-134,78,GREEN,GREEN_D)
rr=44
d.rectangle([CX-rr,BASE-58,CX+rr,BASE], fill=DARK)
d.pieslice([CX-rr,BASE-58-rr,CX+rr,BASE-58+rr],180,360, fill=DARK)
for mx,mh in ((CX-336,268),(CX+336,292)): minaret(mx,BASE,mh,24,CREAM,SHADE)

# A plinth just wider than the mosque, instead of a colonnade running the whole card:
# the arcade made every tree look like it was growing out of the building.
body(CX-330, BASE-16, CX+330, BASE, CREAM_D, SHADE)

# Date palms on the ground either side, the nearer ones taller.
for i,(cx,hh,ln) in enumerate(((92,196,1.0),(232,150,-0.8),(1146,176,0.7),
                               (1290,214,-1.0),(1432,148,0.9))):
    palm(cx, BASE, hh, ln, seed=i)
# Trim the transparent sky above the tallest finial. A quarter of the canvas was empty,
# and since the card sizes the art by its *aspect*, that padding was shrinking the drawing
# at every widget height — worst on the short stacked layout, where it cost a third of the
# available width.
bbox = img.getbbox()
img = img.crop((0, bbox[1], W, H))
img.save('/tmp/skyline_new.png')
bg=Image.new('RGB',img.size,(120,160,205)); bg.paste(img,(0,0),img)
bg.resize((1152,320)).save('skyline_preview.png'); print('v2 drawn')
