import os
ROOT=os.environ.get('WEEM_ROOT','/home/user/Weem')
import re,os,sys
from tlib import *
from cfg import *
OUT=ROOT+'/logic/capture/src/test/kotlin/com/kuryelog/logic/capture/'
PKG='com.kuryelog.logic.capture'
def post(name,txt,cap=True):
    if name=='SahaTekrarTest':
        txt=re.sub(r'\bFrame\b','SahaFrame',txt)
        txt=txt.replace('SahaTekrarTest::parse','SahaFrames::parse')
        imps=''.join(f'import com.kuryelog.logic.capture.SahaFrames.{x}\n' for x in ['WOLT','frames','expected','parse','isIdleFrame','isSplitFrame','cardEarning','changeAmount'])
        txt=txt.replace('\n\n/**',  '\n'+imps+'\n/**',1) if False else re.sub(r'(import org.junit.Test\n)',r'\1'+imps,txt,1)
    if name=='ParserEconomicsTest' and cap:
        txt=txt.replace('OverlayText','OwnLines')
    if name=='OcrPaceD6Test':
        txt=txt.replace('SahaTekrarTest.frames','SahaFrames.frames').replace('SahaTekrarTest::parse','SahaFrames::parse')
    return txt
if __name__=='__main__':
    os.makedirs(OUT,exist_ok=True)
    for f,keep in CAP.items():
        p=parse(S+f+'.kt')
        names=[name_of(m) for m in p['members'] if name_of(m)]
        k=names if keep is None else keep
        tmp=OUT+f+'.kt'
        ORD=r'\b(DeliveryCore|OrderTracker|RecordJson|OnlineSessions|CashPayment|Readiness|OrderHistory|BoostLog|PlatformHistory|TripPhase|CoreEvent|RecordPages|PocketCash|CashPending|VenueArrival|DeliveryReminder|BackupFiles|Store|ManualSessionPolicy)\b'
        dn=set()
        for m in p['members']:
            if name_of(m) is None and re.search(ORD,'\n'.join(m)): dn|=set(helper_names([m]))
        changed=True
        while changed:
            changed=False
            for m in p['members']:
                if name_of(m) is None:
                    hn=helper_names([m])
                    if hn and not set(hn)<=dn and any(re.search(r'\b'+re.escape(d)+r'\b','\n'.join(m)) for d in dn):
                        dn|=set(hn); changed=True
        tests=write(p,tmp,PKG,[],keep=set(k),dropprefix=('data class Frame','companion object'),dropnames=dn)
        if dn: print('  atılan yardımcılar:',sorted(dn))
        t=open(tmp,encoding='utf-8').read(); open(tmp,'w',encoding='utf-8').write(post(f,t))
        print(f,len(tests))
