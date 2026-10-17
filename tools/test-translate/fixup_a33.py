# A3.3: stub düzeltmeleri (regen_stubs.sh sonrası). YolGoNav.State/Route → WEEM RouteStatus/RouteStatus.Route (ORDERS.md 4.9, 15.2).
import os,re
ROOT=os.environ.get('WEEM_ROOT','/home/user/Weem')
O=ROOT+'/logic/orders/src/main/kotlin/com/kuryelog/logic/orders/'
p=O+'CustomerCard.kt'; s=open(p,encoding='utf-8').read()
s=s.replace('fun routeFor(state: YolGoNav.State?, since: Long?, now: Long): YolGoNav.Route?','fun routeFor(state: RouteStatus?, since: Long?, now: Long): RouteStatus.Route?')
s=s.replace('YolGoNav.Route','RouteStatus.Route')
open(p,'w',encoding='utf-8').write(s)
