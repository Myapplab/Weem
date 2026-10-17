import os
ROOT=os.environ.get('WEEM_ROOT','/home/user/Weem')
import re
O=ROOT+'/logic/orders/src/main/kotlin/com/kuryelog/logic/orders/'
def sub(path,pairs):
    s=open(O+path,encoding='utf-8').read()
    for a,b in pairs:
        if a not in s: print('YOK:',path,a[:60]); continue
        s=s.replace(a,b,1)
    open(O+path,'w',encoding='utf-8').write(s)
sub('RecordJson.kt',[
 ('fun details(d: DeliveryDetails): UNKNOWN_TYPE /* RecordJson.details */','fun details(d: DeliveryDetails): JSONObject'),
 ('fun details(j: JSONObject): UNKNOWN_TYPE /* RecordJson.details */','fun details(j: JSONObject): DeliveryDetails'),
 ('fun observation(o: CourierObservation): UNKNOWN_TYPE /* RecordJson.observation */','fun observation(o: CourierObservation): JSONObject'),
 ('fun observation(j: JSONObject): UNKNOWN_TYPE /* RecordJson.observation */','fun observation(j: JSONObject): CourierObservation'),
 ('fun offer(o: OfferState): UNKNOWN_TYPE /* RecordJson.offer */','fun offer(o: OfferState): JSONObject'),
 ('fun offer(j: JSONObject): UNKNOWN_TYPE /* RecordJson.offer */','fun offer(j: JSONObject): OfferState'),
])
s=open(O+'DeliveryCore.kt',encoding='utf-8').read()
s=re.sub(r'    internal fun distance\(a: String, b: String\): Int = TODO\("A3.5"\)\n','',s)
s=re.sub(r'    fun sameName\(a: String\?, b: String\?\): Boolean = TODO\("A3.5"\)\n','',s)
s=re.sub(r'    fun looksLikeAddress\(s: String\): Boolean = TODO\("A3.5"\)\n','',s)
open(O+'DeliveryCore.kt','w',encoding='utf-8').write(s)
s=open(O+'OverlayText.kt',encoding='utf-8').read()
s=re.sub(r'    fun isOwn\(line: String\): Boolean = TODO\("A3.5"\)\n','',s)
s=re.sub(r'    fun withoutOwn\(texts: List<String>\): List<String> = TODO\("A3.5"\)\n','',s)
open(O+'OverlayText.kt','w',encoding='utf-8').write(s)
