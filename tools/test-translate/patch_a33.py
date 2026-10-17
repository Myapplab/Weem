import os,re
ROOT=os.environ.get('WEEM_ROOT','/home/user/Weem')
D=ROOT+'/logic/orders/src/test/kotlin/com/kuryelog/logic/orders/'
def edit(f,pairs,regex=()):
    p=D+f;s=open(p,encoding='utf-8').read()
    for a,b in pairs:
        if a not in s: print('YOK',f,repr(a[:70])); continue
        s=s.replace(a,b)
    for a,b in regex:
        s2=re.sub(a,b,s,flags=re.S)
        if s2==s: print('REGEX YOK',f,a[:60])
        s=s2
    open(p,'w',encoding='utf-8').write(s)

AE='AnalysisExport (A4.3, `:core`)'
# --- anonim dışa aktarma ifadeleri A4.3'e (AnalysisExport çekirdekte; ORDERS.md 15.2, 15.3)
edit('CashOrderK31K32Test.kt',[
 ('        assertEquals(AnalysisExport.Kind.KEEP_ALL, AnalysisExport.kindOf("2026-10-08/" + CashTips.FILE))\n',
  '        // WEEM: `AnalysisExport.kindOf(...) == KEEP_ALL` ifadesi `AnalysisExportTest`\'te (A4.3, `:core`).\n')])
edit('PlatformHistoryK36Test.kt',[
 ('        assertEquals(AnalysisExport.Kind.KEEP_ALL, AnalysisExport.kindOf("2026-10-08/" + PlatformHistory.FILE))\n'
  '        val exported = AnalysisExport().text("2026-10-08/" + PlatformHistory.FILE, rows.joinToString("\\n", postfix = "\\n"))!!\n'
  '        assertTrue(exported.contains("\\"missed_by_kuryeplus\\":true"))\n',
  '        // WEEM: `AnalysisExport.kindOf(...) == KEEP_ALL` ve `missed_by_kuryeplus` alanı ifadeleri `AnalysisExportTest`\'te (A4.3, `:core`).\n')])
edit('OfferNetLineTest.kt',[
 ('OverlayText.isOwn(l)); assertTrue(l, AnalysisExport.isPanelLine(l)) }\n        for (l in e5) assertFalse(l, AnalysisExport.isPanelLine(l))\n',
  'OwnLines.isOwn(l)) }\n        // WEEM: `AnalysisExport.isPanelLine` ve `AnalysisExport(...).screen(...)` ifadeleri (dışa aktarma net satırını atar) `AnalysisExportTest`\'te (A4.3, `:core`); burada ayrıştırıcının tanıyıcısı.\n'),
])
# OfferNetLineTest: kalan AnalysisExport.screen bloğu (regex yerine) çıkarılır
_p=D+'OfferNetLineTest.kt'; _s=open(_p,encoding='utf-8').read()
_a=_s.index('        val row = AnalysisExport('); _e='        assertFalse(row.toString(), row.toString().contains("net 3"))\n'; _b=_s.index(_e)+len(_e)
_s=_s[:_a]+'        for (l in e5) assertFalse(l, OwnLines.isOwn(l))\n'+_s[_b:]
_s=_s.replace('for (l in lines) { assertTrue(l, OwnLines.isOwn(l)) }','for (l in lines) assertTrue(l, OwnLines.isOwn(l))')
open(_p,'w',encoding='utf-8').write(_s)
edit(XXTheNetLineWithTheParsersRecogniser','theNetLineIsOneOfTheParsersOwnLines')])
edit('OverlayBadgeK35Test.kt',[('OverlayText::isOwn','OwnLines::isOwn'),('OverlayText.isOwn','OwnLines.isOwn')])

# --- CustomerCardE16: YolGo yükü -> RouteStatus; metin "(YolGo)" -> "(WEEM)" (ORDERS.md 16)
edit('CustomerCardE16Test.kt',[
 ('): YolGoNav.State = YolGoNav.parse(','): RouteStatus = RouteStatus.fromNavFeed('),
 ('YolGoNav.Route(','RouteStatus.Route('),
 ('(YolGo)','(WEEM)'),('(YolGo, ','(WEEM, '),
 ('// Without YolGo only the straight line counts.','// Without a route only the straight line counts.'),
 (" * route, or the last 2 turns. Route distance and turns come from YolGo's navigation payload (E8, `nav/v1/current`); the old\n"," * route, or the last 2 turns. Route distance and turns come from the navigation status (WEEM: `RouteStatus`, plan 4.4; Rawstats: YolGo's `nav/v1/current`, E8); the old\n"),
 (" * YolGo PR #25 is not on the phone yet: tested with made-up payloads (uydurma koordinatlar).\n"," * Tested with made-up payloads (uydurma koordinatlar); WEEM reads the same payload text through `RouteStatus.fromNavFeed`.\n"),
])
edit('BoostTempE3Test.kt',[("(YolGo'dan hava gelmedi)","(hava bilgisi gelmedi)"),("(YolGo-dan hava gəlmədi)","(hava məlumatı gəlmədi)")])
edit('PlatformTotalsF85Test.kt',[('Rawstats:','WEEM:'),("Rawstats's","WEEM's")])
edit('PlatformTotalsF84Test.kt',[("Rawstats's","WEEM's")])
