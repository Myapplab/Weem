#!/usr/bin/env python3
"""Rawstats Kotlin kaynağından iskelet (imza) üretir: gövdeler TODO, private üyeler atılır.
Kullanım: stubber.py SRC.kt --only Name1,Name2 [--task A3.5] [--types types.json] > out.kt
Türü çıkarılamayan üyeler `UNKNOWN_TYPE` ile işaretlenir (elle doldurulur: types.json 'Sınıf.üye' -> 'Tip')."""
import re,sys,json,argparse
ap=argparse.ArgumentParser()
ap.add_argument('src');ap.add_argument('--only',default='');ap.add_argument('--task',default='A3.5')
ap.add_argument('--types',default='');ap.add_argument('--keep-kdoc',action='store_true')
a=ap.parse_args()
TYPES=json.load(open(a.types)) if a.types else {}
text=open(a.src,encoding='utf-8').read()
# --- maske: dize ve yorum içeriklerini boşlukla değiştir (aynı uzunluk)
def mask(s):
    out=list(s);i=0;n=len(s)
    while i<n:
        c=s[i]
        if s.startswith('//',i):
            j=s.find('\n',i); j=n if j<0 else j
            for k in range(i,j): out[k]=' '
            i=j
        elif s.startswith('/*',i):
            depth=1;j=i+2
            while j<n and depth:
                if s.startswith('/*',j): depth+=1;j+=2
                elif s.startswith('*/',j): depth-=1;j+=2
                else: j+=1
            for k in range(i,j):
                if s[k]!='\n': out[k]=' '
            i=j
        elif s.startswith('"""',i):
            j=s.find('"""',i+3); j=n if j<0 else j+3
            for k in range(i+1,j-1):
                if s[k]!='\n': out[k]='x'
            i=j
        elif c=='"':
            j=i+1
            while j<n and s[j]!='"':
                if s[j]=='\\': j+=1
                j+=1
            # şablon ifadeleri ${...} içindeki " işaretleri nadir; yok sayılır
            for k in range(i+1,min(j,n)): out[k]='x'
            i=j+1
        elif c=="'":
            j=i+1
            while j<n and s[j]!="'":
                if s[j]=='\\': j+=1
                j+=1
            for k in range(i+1,min(j,n)): out[k]='x'
            i=j+1
        else: i+=1
    return ''.join(out)
M=mask(text)
lines=text.split('\n');mlines=M.split('\n')
def indent(l): return len(l)-len(l.lstrip(' '))
def depth_end(ms,start):
    """start: '{' indeksi; eşleşen '}' indeksi"""
    d=0
    for i in range(start,len(ms)):
        if ms[i]=='{': d+=1
        elif ms[i]=='}':
            d-=1
            if d==0: return i
    return len(ms)-1
MODS=r'(?:(?:public|internal|private|protected|open|abstract|final|override|const|lateinit|inline|operator|infix|tailrec|suspend|data|enum|sealed|annotation|companion|external|expect|actual|vararg)\s+)*'
def split_members(blines,mblines,base):
    """satır listesini üyelere böl (indent==base ve '}'/')' ile başlamayan satır = yeni üye)"""
    mem=[];cur=[]
    for l,m in zip(blines,mblines):
        st=l.strip()!='' and indent(l)==base and not l.lstrip().startswith(('}',')',']'))
        if st:
            if cur and all(x[1].strip()=='' or x[1].lstrip().startswith(('//','/*','*','@')) for x in cur) :
                cur.append((l,m));continue
            if cur: mem.append(cur)
            cur=[]
        cur.append((l,m))
    if cur: mem.append(cur)
    return mem
def kdoc_and_code(mem):
    """(kdoc satırları, kod satırları (orijinal), kod satırları (maske))"""
    i=0
    while i<len(mem) and (mem[i][1].strip()=='' or mem[i][0].lstrip().startswith(('/**','/*','*','//'))): i+=1
    rest=mem[i:]
    while rest and rest[-1][1].strip()=='' and rest[-1][0].strip()=='': rest=rest[:-1]
    return mem[:i],rest
def find_top(ms,pos=0,ch=None):
    pass
def split_header_body(code_o,code_m):
    """tek üye için başlık ve gövde: ({ ... } ya da = ... ya da get() ...)"""
    o='\n'.join(x[0] for x in code_o);m='\n'.join(x[1] for x in code_m)
    return o,m
def header_end(m):
    """başlığın bittiği yer: derinlik 0'da ilk '{' ya da (parantez dışında) ilk ' = ' ya da satır sonu"""
    d=0;i=0
    n=len(m)
    while i<n:
        c=m[i]
        if c in '([': d+=1
        elif c in ')]': d-=1
        elif c=='<' and False: pass
        elif d==0 and c=='{': return i,'{'
        elif d==0 and c=='=' and m[i-1:i+2] not in ('==','!=','<=','>=') and m[i:i+2]!='==' and not (i+1<n and m[i+1]=='=') and m[i-1] not in '!<>=':
            return i,'='
        i+=1
    return n,''
def ret_type(hdr):
    """fun başlığından dönüş türü ('): T') ya da None"""
    d=0;pos=None
    k=hdr.find('(')
    if k<0: return None
    d=0
    for i in range(k,len(hdr)):
        if hdr[i]=='(': d+=1
        elif hdr[i]==')':
            d-=1
            if d==0: pos=i;break
    if pos is None: return None
    rest=hdr[pos+1:].strip()
    if rest.startswith(':'):
        return rest[1:].strip()
    return None
def esc(t): return t
SIMPLE_INIT=re.compile(r'^(?:null|true|false|-?\d[\d_.eEL]*[fFL]?|"[^"\\]*"|emptyList\(\)|emptyMap\(\)|emptySet\(\)|(?:mutable|linked|hash|array)?(?:List|Map|Set|ListOf|MapOf|SetOf)Of?(?:<[^()]*>)?\(\)|(?:HashMap|LinkedHashMap|ArrayList|HashSet|LinkedHashSet|StringBuilder)(?:<[^()]*>)?\(\)|(?:mutableListOf|linkedMapOf|linkedSetOf|mutableMapOf|mutableSetOf|hashMapOf|hashSetOf)(?:<[^()]*>)?\(\))$')
def infer_literal_type(init):
    init=init.strip()
    if init in('true','false'): return 'Boolean'
    if re.fullmatch(r'-?\d+',init): return 'Int'
    if re.fullmatch(r'-?\d[\d_]*L',init): return 'Long'
    if re.fullmatch(r'-?\d+\.\d+',init): return 'Double'
    if re.fullmatch(r'"[^"\\]*"',init): return 'String'
    return None
def split_semis(code):
    """tek satırlık `var a: X; var b: Y` gruplarını ayır (derinlik 0'da ';' + yeni bildirim)"""
    if len(code)!=1: return None
    o,m=code[0]
    cuts=[];d=0
    for i,ch in enumerate(m):
        if ch in '({[': d+=1
        elif ch in ')}]': d-=1
        elif ch==';' and d==0 and re.match(r'\s*(?:@\w+\s+)*(?:\w+\s+)*?(?:val|var|fun)\s',m[i+1:]): cuts.append(i)
    if not cuts: return None
    parts=[];prev=0
    for c in cuts+[len(m)]:
        parts.append((o[prev:c].lstrip(';').rstrip(),m[prev:c].lstrip(';').rstrip()));prev=c+1
    return parts
def stub_member(cls,mem,base,out,ind):
    kd,code=kdoc_and_code(mem)
    if not code: return
    sp=split_semis(code)
    if sp:
        for p in sp:
            lead=' '*base
            stub_member(cls,[(lead+p[0].strip(),lead+p[1].strip())],base,out,ind)
        return
    co=[x[0] for x in code];cm=[x[1] for x in code]
    first=cm[0].strip()
    # ek açıklamalar (@Foo) ayrı satırda olabilir
    while first.startswith('@') and len(co)>1 and re.match(r'^@\w+(?:\([^)]*\))?$',first):
        co=co[1:];cm=cm[1:];first=cm[0].strip()
    o='\n'.join(co);m='\n'.join(cm)
    if re.match(r'^(?:@\w+(?:\([^)]*\))?\s+)*(?:\w+\s+)*?private\s',first+' ') and not first.startswith('private set'): return
    if first.startswith('init') or first.startswith('@Volatile') and False: return
    if re.match(r'^(?:\w+\s+)*init\s*\{',first): return
    kdtxt=''
    if a.keep_kdoc and kd:
        k1=[x[0] for x in kd if x[0].strip()]
        kdtxt='\n'.join(k1)+'\n'
    pad=' '*ind
    # sınıf benzeri
    mm=re.match(r'^((?:@\w+\s+)*)'+MODS+r'(class|object|interface)\b',first) or re.match(r'^(?:\w+\s+)*companion\s+object',first)
    if mm:
        he,kind=header_end(m)
        hdr_o=o[:he].rstrip()
        if kind=='{':
            end=depth_end(m,he)
            inner_o=o[he+1:end];inner_m=m[he+1:end]
            if re.search(r'\benum\s+class\b',hdr_o):
                # girişler (;'a kadar) aynen, geri kalan üyeler iskelet
                semi=-1;d=0
                for i,ch in enumerate(inner_m):
                    if ch in '({[': d+=1
                    elif ch in ')}]': d-=1
                    elif ch==';' and d==0: semi=i;break
                if semi<0 and not re.search(r'\b(?:fun|val|var)\b',inner_m):
                    out.append(pad+hdr_o.strip()+' {'+inner_o.rstrip()+'\n'+pad+'}' if '\n' in inner_o else pad+hdr_o.strip()+' {'+inner_o+'}')
                else:
                    ent=inner_o[:semi] if semi>=0 else inner_o
                    out.append(pad+hdr_o.strip()+' {')
                    out.append(pad+'    '+' '.join(ent.split())+';')
                    if semi>=0: stub_body(cls_name(hdr_o,cls),inner_o[semi+1:],inner_m[semi+1:],out,ind+4)
                    out.append(pad+'}')
                return
            out.append(pad+hdr_o.strip()+' {')
            stub_body(cls_name(hdr_o,cls),inner_o,inner_m,out,ind+4)
            out.append(pad+'}')
        else:
            out.append(pad+hdr_o.strip())
        return
    # fonksiyon
    if re.match(r'^(?:\w+\s+)*fun\b',first):
        he,kind=header_end(m)
        hdr=o[:he].strip();hm=m[:he]
        # ' = ' sonrası: ifade gövde; '{' : blok
        rt=ret_type(hm)
        override='override ' in hdr.split('fun')[0]
        name=re.search(r'fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)\s*\(',hm)
        nm=name.group(1) if name else '?'
        key=f'{cls}.{nm}'
        if rt is None and kind=='=':
            t=TYPES.get(key)
            if t: hdr=hdr+': '+t
            else: hdr=hdr+': UNKNOWN_TYPE /* '+key+' */'
            out.append(pad+hdr+f' = TODO("{a.task}")')
        elif rt is None:  # blok gövde, Unit
            out.append(pad+hdr+f': Unit = TODO("{a.task}")')
        else:
            out.append(pad+hdr+f' = TODO("{a.task}")')
        return
    # özellik
    pm=re.match(r'^((?:\w+\s+)*)(val|var)\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w+)',first)
    if pm:
        isvar=pm.group(2)=='var';name=pm.group(3)
        he,kind=header_end(m)
        hdr=o[:he].rstrip()
        hm=m[:he]
        if kind=='' and 'get()' in m:
            kind='get'
        # get() = ... biçimi
        gi=m.find('get()')
        explicit=re.search(r'\b(?:val|var)\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?\w+\s*:\s*(.+?)\s*$',hm.split('get()')[0].split(' = ')[0].strip().split('\n')[0]) 
        mods=pm.group(1)
        if 'const ' in mods:
            out.append(pad+o.strip().split('\n')[0] if kind=='=' else pad+o.strip()); return
        # tür
        typ=None
        hdr_clean=hm.split('get()')[0].split(' by ')[0].strip()
        e=re.search(r'\b(?:val|var)\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?\w+\s*:\s*([^=]+?)\s*$',hdr_clean)
        if e: typ=e.group(1).strip()
        init=None
        if kind=='=' and gi<0:
            init=o[he+1:].strip()
        key=f'{cls}.{name}'
        if typ is None:
            if init is not None:
                typ=infer_literal_type(init)
            typ=TYPES.get(key,typ)
        simple0 = init is not None and SIMPLE_INIT.match(init.split('\n')[0].strip()) and '\n' not in init.strip()
        if typ is None and not simple0:
            typ='UNKNOWN_TYPE /* '+key+' */'
        decl=re.sub(r'(\b(?:val|var)\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?\w+)\s*(?::\s*[^=]+)?$',lambda x:x.group(1),hdr_clean)
        decl=decl.strip()
        if typ: decl=decl+': '+typ
        # Kısıtlı: 'by lazy' vb.
        simple = init is not None and SIMPLE_INIT.match(init.split('\n')[0].strip()) and '\n' not in init.strip()
        if simple:
            out.append(pad+decl+' = '+init.strip())
        elif isvar:
            out.append(pad+decl)
            out.append(pad+f'    get() = TODO("{a.task}")')
            out.append(pad+f'    set(_) = TODO("{a.task}")')
        else:
            out.append(pad+decl+f' get() = TODO("{a.task}")')
        return
    out.append(pad+'/* ATLANDI: '+first[:70].replace('*/','')+' */')
def cls_name(hdr,default):
    m=re.search(r'(?:class|object|interface)\s+(\w+)',hdr)
    return m.group(1) if m else ('Companion' if 'companion' in hdr else default)
def stub_body(cls,body_o,body_m,out,ind):
    bo=body_o.split('\n');bm=body_m.split('\n')
    # ilk satır '{' sonrası kalan; boş olabilir
    if bm and bm[0].strip()=='' : bo=bo[1:];bm=bm[1:]
    if not bm: return
    base=min((indent(x) for x in bm if x.strip()!=''),default=4)
    mem=split_members(bo,bm,base)
    for mm in mem: stub_member(cls,mm,base,out,ind)
def top_decls():
    """üst düzey bildirimleri bul: (ad, orijinal metin, maske metin)"""
    mem=split_members(lines,mlines,0)
    res=[]
    for mm in mem:
        kd,code=kdoc_and_code(mm)
        if not code: continue
        m='\n'.join(x[1] for x in code)
        mt=re.match(r'^(?:@\w+\s+)*(?:\w+\s+)*(class|object|interface|fun|val|var|const)\s+(?:<[^>]*>\s*)?(\w+)',m.strip().split('\n')[0] if False else m.lstrip())
        res.append((mt.group(2) if mt else None,mm))
    return res
only=set(filter(None,a.only.split(',')))
print('// İskelet (%s): imzalar Rawstats\'tan (%s @ 6388c04); testlerin derlenmesi için. Gövdeler %s\'te.'%('A3.2',a.src.split('/')[-1],a.task))
for name,mm in top_decls():
    if name is None or (only and name not in only): continue
    kd,code=kdoc_and_code(mm)
    out=[];stub_member('',mm,0,out,0)
    if out: print('\n'.join(out))
    print()
