# Test bölme yapılandırması (CAPTURE.md 8 / ORDERS.md 15.1)
CAP = {
 'BoltBubbleR2Test': ['bubbleOnOneLineBeforeThePanel','bubbleOnTwoLinesAfterThePanel','arrivedScreenBubble','otherMinutesAreNoBubble'],
 'OcrPaceD6Test': ['framesTheRulesUse','aQuietWindowKeepsItsGap','aNotificationMakesItsAppHotForTenSeconds','aCardWithoutItsFeeStaysHotUntilTheFeeOrTwentySeconds','aFailedScreenshotWaitsTheNormalGap','anIdenticalScreenshotReusesItsLinesAndEveryPixelCounts','ocrInputIsAtMost960Wide','sahaCardsWithoutAFeeFirstAreHot','simulatedFeeDelayOldAndNewPace'],
 'ParserEconomicsTest': ['boltCardSplitsApproachAndDeliveryMinutes','boltLegsNeedBothValues','woltOfferKeepsItsStopsAndHasNoBoltLegs','woltYourStatsEarningsAreReadButStayAPage','boltAcceptanceRateIsAStatNotABonus','boltPerformancePageFields','statsAreOnlyReadOnPerformancePages','scheduledOfflineQuickLinkIsNoPenalty','earningsScheduleForecastIsNotALiveMultiplier','namesAndDishesAreNotMultipliers','boltHomeMultipliersStay','futureBoostIsNotTheActiveBonus','activeBoostStaysTheBonus','boostStartTimes','thousandsSeparatorIsNeverMisread','azerbaijaniDeclineLabelIsADecline','cashToCollectIsNotTheFee','ownBadgeLinesAreRemovedForEverySource','ocrVariantsOfPanelLinesAreRecognised','courierAppLinesAreNeverPanelLines'],
 'RecordingPolicyTest': ['restaurantOrHistoryFeeAndKmAreNotAnOffer','acceptedStatusAndSettingsToggleCannotActAsAcceptButton','windowReorderCountdownAndEtaDoNotProduceExtraScreens','emptySnapshotDoesNotEraseTheLastKnownScreen'],
 'ScreenReadingE7Test': None,  # 4 (biri uyarlanır)
 'WoltDeliverySliderTest': ['sliderLabelIsPendingDeliveryAndNotAnotherOffer','waitingContextDoesNotOverrideAnActiveDeliveryOrProveDispatchAvailability','bothKnownWoltPackagesUseTheSameWaitingAndSliderRules'],
 'WoltOfferFeeK37Test': ['theSyntheticFramesAreOfferCardsWithoutAFee','ocrFallsBackToTheDisplayAfterAnInvalidWindow'],
 'WoltSliderSpinnerK33Test': ['onlyTheProgressBarClassIsASpinner','aRoundIndicatorOnTheSliderRowCountsALinearBarDoesNot'],
 'WoltStatsF81Test': None,
 'IcuRegexTest': None,
 'CourierFlowTest': ['pricedBoltButtonIsAccept','woltOfferHasDistinctStopAndOrderCounts','plusCodeIsRetainedAsRestaurantAddress','customerAddressInOfferIsCaptured','readyTimeIsSeparateFromDeliveryEstimate','negativeClockIsNotTravelEta','woltCollectTranslationIsNotDelivery','customerDetailsCaptureSparseFieldsOnly','notesContainingPhoneStayOriginal','longDirectionsStayComplete','checkIdRequirementCaptured','sliderIsContextualArrivalNotCollection','futureBonusIsRecordedWithStartTimeNotAnOffer','woltBanIsOfflineNotAnOrder','multiplierIsNotMoney'],
 'DeliveryRecognitionTest': ['woltCustomerCardIsDropoffWithDeliverySlider','supportStatsAndSettingsPagesAreNotOrderScreens','woltWaitingPanelWithQuickLinksIsStillWaiting','bothOfflineScreensAreOffline','emptyFieldsAreFilledFromTheCustomerNote','platformFieldWinsOverTheNote','azerbaijaniNoteWordsAreRecognised','phoneNumbersInNotesAreFound','ordinaryNumbersAreNotPhones','truncatedNoteDoesNotReplaceTheFullOne','routeDistanceComesOnlyFromATripSummary'],
 'SahaTekrarTest': ['karelerYuklenirVeKisiselVeriYok','d22HazirEtiketleri','d19BeklemeKarelerininEnAz95iTaninir','d5BolunmusEkranKarelerininHepsiBekleme','d1KartKazanciOkunurParaUstuAsla','d21ExpectedEarningsTekBasinaTeklifIsareti'],
 'WoltFlowK1K5Test': ['demandMapAloneIsNoAvailabilityEvidence','ocrSliderVariantsAreRead','waitingSheetWithToolbarIconIsNotTheSideMenu','woltBuildingNameAndColumnOrderedRow','ocrRowOrderEntranceFloorApartment'],
}
# orders'a gitmeyenler (core'a ya da çevrilmez)
NOT_ORDERS = {
 'RecordingPolicyTest': ['movingBlocksAllContentUntilSeveralStillFixes','staleOrPoorGpsNeverResumesScreenCapture','slowMovementAndGpsReplayDoNotPretendStopped','badNumericFixesAndShiftResetPauseCapture','partialOfferEnrichesRatherThanBecomingAnotherOffer','differentPriceRouteOrPlatformIsANewOffer','restoreOnlyAcceptsKnownLogsInsideAnActualDay','reimportKeepsNewRowsAndDoesNotDoubleOldOrders'],
 'CourierFlowTest': ['automaticAndStickyStartsCannotCreateShift','captureNeedsManualSessionOnly'],
}
ORDERS_FILES = ['CourierFlowTest','DeliveryRecognitionTest','WoltFlowK1K5Test','SahaTekrarTest','WoltDeliverySliderTest','WoltSliderSpinnerK33Test','WoltOfferFeeK37Test','OcrPaceD6Test','BoltBubbleR2Test','ParserEconomicsTest','CashScreenM16Test']
