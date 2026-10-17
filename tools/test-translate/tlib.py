import re
import os
S=os.environ.get('RS_TEST','/home/user/kurye_nav/app/src/test/java/com/kuryelog/app/')
def parse(path):
    lines=open(path,encoding='utf-8').read().split('\n')
    ci=next(i for i,l in enumerate(lines) if re.match(r'^(?:class|object) \w+',l))
    hs=ci
    while hs>0 and (lines[hs-1].startswith((' *','/**','//','@'))): hs-=1
    imports=[l for l in lines[:hs] if l.startswith('import ')]
    pre=lines[hs:ci]
    end=max(i for i,l in enumerate(lines) if l=='}')
    body=lines[ci+1:end]
    members=[];cur=[]
    def iscom(c): return bool(c) and all(x.strip()=='' or x.lstrip().startswith(('//','/*','*')) for x in c)
    for l in body:
        ind=len(l)-len(l.lstrip(' '))
        start=l.strip()!='' and ind==4 and not l.lstrip().startswith(('}',')',']'))
        if start and not iscom(cur) and cur:
            members.append(cur);cur=[]
        cur.append(l)
    if cur: members.append(cur)
    return dict(lines=lines,ci=ci,imports=imports,pre=pre,members=members,decl=lines[ci])
def name_of(m):
    t='\n'.join(m)
    mm=re.search(r'@Test(?:\([^)]*\))?\s+(?:@Ignore(?:\([^)]*\))?\s+)?fun\s+(?:`([^`]+)`|(\w+))',t)
    if mm: return mm.group(1) or mm.group(2)
    mm=re.search(r'@Ignore(?:\([^)]*\))?\s+@Test\s+fun\s+(\w+)',t)
    return mm.group(1) if mm else None
def helper_names(members):
    out=[]
    for m in members:
        if name_of(m): continue
        first=next((x for x in m if x.strip() and not x.lstrip().startswith(('/','*','@'))),'')
        # "private val a = ...; private val b = ..." satırındaki tüm bildirimler
        for x in re.finditer(r'(?:fun|val|var|class)\s+(?:<[^>]*>\s*)?(\w+)',first):
            out.append(x.group(1))
    return out
def write(p,out,pkg,imps,keep=None,drop=(),dropnames=(),extra_members=(),dropprefix=()):
    tests=[]
    res=[]
    for m in p['members']:
        n=name_of(m)
        if n is None:
            first=next((x for x in m if x.strip() and not x.lstrip().startswith(('/','*','@'))),'')
            if first.strip().startswith(tuple(dropprefix)): continue
            hn=[x for x in helper_names([m])]
            if any(h in dropnames for h in hn): continue
            res.append(m);continue
        if keep is not None and n not in keep: continue
        if n in drop: continue
        res.append(m);tests.append(n)
    hdr=['package '+pkg,'']+sorted(set(p['imports']+['import '+i for i in imps]))
    txt='\n'.join(hdr)+'\n\n'+'\n'.join(p['pre'])+('\n' if p['pre'] else '')+p['decl']+'\n'+'\n'.join('\n'.join(m) for m in res)+'\n'+'\n'.join(extra_members)+'}\n'
    open(out,'w',encoding='utf-8').write(txt)
    return tests
