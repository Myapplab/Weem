import os
ROOT=os.environ.get('WEEM_ROOT','/home/user/Weem')
import re,os,sys
from tlib import *
from cfg import *
from gen_capture import post
OUT=ROOT+'/logic/orders/src/test/kotlin/com/kuryelog/logic/orders/'
PKG='com.kuryelog.logic.orders'
IMPS=['com.kuryelog.logic.capture.*','com.kuryelog.logic.core.*']
ECON={
 'CoreLifecycleV2Test':['waitWhileTheOtherAppIsBusyIsFlagged','bannerPopupHourAndDemandStayInsideOneSpell','aLongUnreadableStretchStillEndsTheSpell','ocrOfferAtTheSameInstantAsAnUnreadableFrameIsTheEvent','offlineScreenEndsTheWaitAsWentOffline','vehicleChangeStillSplits','midnightSpellIsFiledUnderItsStartDay','segmentsSurviveProcessDeath'],
 'CustomerStageK34RecordTest':['legsRowIsNeverADelivery','analysisExportKeepsTheFlagAndDropsTheCustomer','analysisExportKeepsTheOrderEvent','feedV2ShowsOneOrderAtTheCustomer'],
 'VenueArrivalD9Test':['legsAveragesAndRecordsKeepTheGpsArrival'],
 'RestaurantWaitE2Test':['cardAndListShowIt'],
 'ReadinessE15Test':['restaurantNotificationUsesTheCounterAndFollowsIt'],
 'TripStagesE8Test':['activeOrderCarriesEveryStageWithItsRealTime','attachingKeepsTheClosedCourierStateSchema'],
}
WHOLE=['CustomerStageK34Test','CustomerStageK34ClosedCardTest','CustomerStageK34LateDeliveryTest','CustomerStageK34RoutingTest','DeliveryCountD3Test','VenueNearM18Test','CustomerNearM19Test','ArrivalDistanceM20Test','StuckCustomerM22Test','WoltSameCustomerR1Test','CustomerMatchK21Test','DeliveryDeadlineK29Test','RecordJsonTest']
def orders_post(name,txt):
    txt=post(name,txt,False)
    txt=txt.replace('CustomerMatch.sameName','NameMatch.sameName')
    if name=='SahaTekrarTest':
        pass
    return txt
if __name__=='__main__':
    os.makedirs(OUT,exist_ok=True)
    files=ORDERS_FILES+['CoreLifecycleV2Test','CustomerStageK34RecordTest','VenueArrivalD9Test','RestaurantWaitE2Test','ReadinessE15Test','TripStagesE8Test']+WHOLE
    total=0
    for f in files:
        p=parse(S+f+'.kt')
        names=[name_of(m) for m in p['members'] if name_of(m)]
        excl=set(CAP.get(f) or []) | set(NOT_ORDERS.get(f,[])) | set(ECON.get(f,[]))
        keep=[n for n in names if n not in excl]
        tmp=OUT+f+'.kt'
        tests=write(p,tmp,PKG,IMPS,keep=set(keep),dropprefix=('data class Frame','companion object'))
        t=open(tmp,encoding='utf-8').read(); open(tmp,'w',encoding='utf-8').write(orders_post(f,t))
        print(f,len(tests)); total+=len(tests)
    # YolGoNavE8Test: 10 uyarlanır test (RouteStatus.Target / NavBinding); yük okuma A2.5'te, pathsMatchYolGoProvider çevrilmez
    f='YolGoNavE8Test'; p=parse(S+f+'.kt')
    drop={'payloadIsReadWithItsTargetAndRoute','anythingElseIsNotAYolGoPayload','pathsMatchYolGoProvider'}
    names=[name_of(m) for m in p['members'] if name_of(m)]
    tmp=OUT+f+'.kt'
    tests=write(p,tmp,PKG,IMPS,keep=set(n for n in names if n not in drop),dropnames={'payload','targetJson','routeJson'})
    t=open(tmp,encoding='utf-8').read()
    t=t.replace('YolGoNav.TARGET_MAX_AGE_MS','RouteStatus.TARGET_MAX_AGE_MS').replace('YolGoNav.TRAIL_MAX_GAP_MS','NavBinding.TRAIL_MAX_GAP_MS').replace('YolGoNav.pickupOf','NavBinding.pickupOf').replace('YolGoNav.preferred','NavBinding.preferred')
    t=t.replace('YolGoNav.Target(lat0 + north / 111_195.0, lon0, kind, origin, at, pkg)','RouteStatus.Target(lat0 + north / 111_195.0, lon0, NavKind.of(kind)!!, NavOrigin.of(origin)!!, at, pkg)')
    open(tmp,'w',encoding='utf-8').write(t); print(f,len(tests)); total+=len(tests)
    print('toplam',total)
