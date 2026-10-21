#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec2 resolution;
uniform float time,phase,mode;
float hash(vec3 p){p=fract(p*.3183099+vec3(.1,.2,.3));p*=17.;return fract(p.x*p.y*p.z*(p.x+p.y+p.z));}
float noise(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);}
float fbm(vec3 p){float v=0.,a=.5;for(int i=0;i<4;i++){v+=a*noise(p);p=p*2.03+3.1;a*=.5;}return v;}
vec3 rotateY(vec3 p,float a){return vec3(cos(a)*p.x+sin(a)*p.z,p.y,-sin(a)*p.x+cos(a)*p.z);}
float craters(vec3 n){float v=0.;for(int i=0;i<38;i++){float f=float(i);vec3 c=normalize(vec3(hash(vec3(f,1,2)),hash(vec3(f,3,4)),hash(vec3(f,5,6)))*2.-1.);float r=.035+.105*hash(vec3(f,7,8));float d=length(n-c)/r;float rim=(d-.88)*9.;v+=.20*exp(-rim*rim)-.15*exp(-d*d*4.); }return v;}
vec4 body(vec2 q,bool sun){float r=length(q),aa=3./min(resolution.x,resolution.y);if(r>1.32)return vec4(0.);if(r>1.){if(!sun)return vec4(0.);float angle=atan(q.y,q.x);float waves=.85+.15*sin(angle*19.+time*.35)+.07*sin(angle*37.-time*.2);float glow=exp(-(r-1.)*18.)*.46*waves*(1.-smoothstep(1.2,1.32,r));return vec4(1.,.47,.08,glow);}vec3 n=vec3(q,sqrt(max(0.,1.-r*r)));vec3 color;if(sun){vec3 p=rotateY(n,time*.018);float flow=fbm(p*5.+vec3(time*.06,0.,0.));float grain=fbm(p*44.+flow*2.+vec3(0.,time*.16,0.));float fine=noise(p*145.+time*.04);float hot=smoothstep(.32,.76,grain);color=mix(vec3(.78,.16,.018),vec3(1.,.78,.26),hot);color+=vec3(.26,.12,.02)*fine;color*=.72+.28*n.z;}else{vec3 p=rotateY(n,.22+time*.006);float terrain=fbm(p*7.);float detail=fbm(p*70.);float crater=craters(p);float albedo=.38+.36*terrain+.16*detail+crater;float angle=phase*6.2831853;vec3 light=vec3(sin(angle),0.,-cos(angle));float diffuse=max(0.,dot(n,light));color=vec3(.88,.89,.87)*albedo*(.035+1.28*diffuse);color+=vec3(.006,.009,.015)*(1.-diffuse);color=pow(max(color,vec3(0.)),vec3(.65));}return vec4(color,1.-smoothstep(1.-aa,1.,r));}
void main(){float radius=min(resolution.y*.35,resolution.x*.36);vec2 center=resolution*.5;gl_FragColor=body((gl_FragCoord.xy-center)/radius,mode<1.5);}
