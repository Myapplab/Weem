import os
ROOT=os.environ.get('WEEM_ROOT','/home/user/Weem')
import os
from tlib import *
from cfg import *
OUT=ROOT+'/logic/orders/src/test/kotlin/com/kuryelog/logic/orders/'
OUT_CAP=ROOT+'/logic/capture/src/test/kotlin/com/kuryelog/logic/capture/'
PKG='com.kuryelog.logic.orders'; PKG_CAP='com.kuryelog.logic.capture'
IMPS=['com.kuryelog.logic.capture.*','com.kuryelog.logic.core.*']
if __name__=='__main__':
    total=0; per={}
    for f,drop in A33.items():
        if drop is None: continue
        p=parse(S+f+'.kt')
        names=[name_of(m) for m in p['members'] if name_of(m)]
        keep=[n for n in names if n not in drop]
        tests=write(p,OUT+f+'.kt',PKG,IMPS,keep=set(keep),dropprefix=('data class Frame','companion object'))
        t=open(OUT+f+'.kt',encoding='utf-8').read().replace('CustomerMatch.sameName','NameMatch.sameName')
        open(OUT+f+'.kt','w',encoding='utf-8').write(t)
        per[f]=len(tests); total+=len(tests)
    for f in A33_CAPTURE:
        p=parse(S+f+'.kt')
        names=[name_of(m) for m in p['members'] if name_of(m)]
        tests=write(p,OUT_CAP+f+'.kt',PKG_CAP,[],keep=set(names))
        per[f+' (capture)']=len(tests)
    for k,v in per.items(): print(k,v)
    print('toplam',sum(per.values()))
