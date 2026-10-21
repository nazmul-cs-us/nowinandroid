#include <metal_stdlib>
using namespace metal;

struct CelestialUniforms { float4 viewport; float4 parameters; };
struct CelestialVertex { float4 position [[position]]; };
vertex CelestialVertex celestialVertex(uint id [[vertex_id]]) {
    const float2 positions[3] = { float2(-1,-1), float2(3,-1), float2(-1,3) };
    CelestialVertex out; out.position = float4(positions[id],0,1); return out;
}
float celestialHash(float3 p){p=fract(p*.3183099f+float3(.1f,.2f,.3f));p*=17.f;return fract(p.x*p.y*p.z*(p.x+p.y+p.z));}
float celestialNoise(float3 p){float3 i=floor(p),f=fract(p);f=f*f*(3.f-2.f*f);return mix(mix(mix(celestialHash(i),celestialHash(i+float3(1,0,0)),f.x),mix(celestialHash(i+float3(0,1,0)),celestialHash(i+float3(1,1,0)),f.x),f.y),mix(mix(celestialHash(i+float3(0,0,1)),celestialHash(i+float3(1,0,1)),f.x),mix(celestialHash(i+float3(0,1,1)),celestialHash(i+float3(1,1,1)),f.x),f.y),f.z);}
float celestialFbm(float3 p){float v=0.f,a=.5f;for(int i=0;i<4;i++){v+=a*celestialNoise(p);p=p*2.03f+3.1f;a*=.5f;}return v;}
float3 celestialRotateY(float3 p,float a){return float3(cos(a)*p.x+sin(a)*p.z,p.y,-sin(a)*p.x+cos(a)*p.z);}
float celestialCraters(float3 n){float v=0.f;for(int i=0;i<38;i++){float f=float(i);float3 c=normalize(float3(celestialHash(float3(f,1,2)),celestialHash(float3(f,3,4)),celestialHash(float3(f,5,6)))*2.f-1.f);float r=.035f+.105f*celestialHash(float3(f,7,8));float d=length(n-c)/r;float rim=(d-.88f)*9.f;v+=.20f*exp(-rim*rim)-.15f*exp(-d*d*4.f);}return v;}
float4 celestialBody(float2 q,bool sun,float2 resolution,float time,float phase){float r=length(q),aa=3.f/min(resolution.x,resolution.y);if(r>1.32f)return float4(0.f);if(r>1.f){if(!sun)return float4(0.f);float angle=atan2(q.y,q.x);float waves=.85f+.15f*sin(angle*19.f+time*.35f)+.07f*sin(angle*37.f-time*.2f);float glow=exp(-(r-1.f)*18.f)*.46f*waves*(1.f-smoothstep(1.2f,1.32f,r));return float4(1.f,.47f,.08f,glow);}float3 n=float3(q,sqrt(max(0.f,1.f-r*r)));float3 color;if(sun){float3 p=celestialRotateY(n,time*.018f);float flow=celestialFbm(p*5.f+float3(time*.06f,0.f,0.f));float grain=celestialFbm(p*44.f+flow*2.f+float3(0.f,time*.16f,0.f));float fine=celestialNoise(p*145.f+time*.04f);float hot=smoothstep(.32f,.76f,grain);color=mix(float3(.78f,.16f,.018f),float3(1.f,.78f,.26f),hot);color+=float3(.26f,.12f,.02f)*fine;color*=.72f+.28f*n.z;}else{float3 p=celestialRotateY(n,.22f+time*.006f);float terrain=celestialFbm(p*7.f);float detail=celestialFbm(p*70.f);float crater=celestialCraters(p);float albedo=.38f+.36f*terrain+.16f*detail+crater;float angle=phase*6.2831853f;float3 light=float3(sin(angle),0.f,-cos(angle));float diffuse=max(0.f,dot(n,light));color=float3(.88f,.89f,.87f)*albedo*(.035f+1.28f*diffuse);color+=float3(.006f,.009f,.015f)*(1.f-diffuse);color=pow(max(color,float3(0.f)),float3(.65f));}return float4(color,1.f-smoothstep(1.f-aa,1.f,r));}
fragment float4 celestialFragment(CelestialVertex in [[stage_in]], constant CelestialUniforms &u [[buffer(0)]]) {
    float2 resolution=u.viewport.xy;
    float2 pixel=float2(in.position.x,resolution.y-in.position.y);
    float radius=min(resolution.y*.35f,resolution.x*.36f);
    float4 result=celestialBody((pixel-resolution*.5f)/radius,u.parameters.x<1.5f,resolution,u.viewport.z,u.viewport.w);
    return float4(result.rgb*result.a,result.a);
}
