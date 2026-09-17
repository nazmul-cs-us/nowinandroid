from PIL import Image, ImageDraw

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

ARC_H=70; BASE=H-ARC_H+5
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

arcade(0,W,H,ARC_H,CREAM,CREAM_D)
img.save('/tmp/skyline_new.png')
bg=Image.new('RGB',(W,H),(120,160,205)); bg.paste(img,(0,0),img)
bg.resize((1152,320)).save('skyline_preview.png'); print('v2 drawn')
