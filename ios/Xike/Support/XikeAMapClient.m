#import "XikeAMapClient.h"

#if __has_include(<AMapLocationKit/AMapLocationKit.h>) && __has_include(<AMapFoundationKit/AMapFoundationKit.h>)
#import <AMapFoundationKit/AMapFoundationKit.h>
#import <AMapLocationKit/AMapLocationKit.h>
#define XIKE_HAS_AMAP 1
#endif

#if XIKE_HAS_AMAP
@interface XikeAMapClient () <AMapLocationManagerDelegate>
@property (nonatomic, strong) AMapLocationManager *manager;
@end
#endif

@implementation XikeAMapClient

+ (BOOL)available {
#if XIKE_HAS_AMAP
    return YES;
#else
    return NO;
#endif
}

- (BOOL)requestWithKey:(NSString *)key
              english:(BOOL)english
           completion:(void (^)(CLLocation *, NSDictionary<NSString *, NSString *> *, BOOL))completion {
#if XIKE_HAS_AMAP
    [AMapLocationManager updatePrivacyShow:AMapPrivacyShowStatusDidShow privacyInfo:AMapPrivacyInfoStatusDidContain];
    [AMapLocationManager updatePrivacyAgree:AMapPrivacyAgreeStatusDidAgree];
    AMapServices *services = [AMapServices sharedServices];
    services.apiKey = key;
    services.enableHTTPS = YES;
    services.regionLanguageType = english ? AMapRegionLanguageTypeEn : AMapRegionLanguageTypeZhHans;
    AMapLocationManager *manager = [[AMapLocationManager alloc] init];
    self.manager = manager;
    manager.delegate = self;
    manager.allowsBackgroundLocationUpdates = NO;
    manager.desiredAccuracy = kCLLocationAccuracyHundredMeters;
    manager.locationTimeout = 10;
    manager.reGeocodeTimeout = 8;
    manager.locationAccuracyMode = AMapLocationReduceAccuracy;
    __weak XikeAMapClient *weakSelf = self;
    return [manager requestLocationWithReGeocode:YES completionBlock:^(CLLocation *location, AMapLocationReGeocode *place, NSError *error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            AMapLocationManager *active = weakSelf.manager;
            if (!active) return;
            BOOL precise = location && active.currentAuthorization == CLAccuracyAuthorizationFullAccuracy &&
                location.horizontalAccuracy >= 0 && location.horizontalAccuracy <= 100;
            NSDictionary *parts = @{@"country": place.country ?: @"", @"province": place.province ?: @"",
                @"city": place.city ?: @"", @"district": place.district ?: @"", @"street": place.street ?: @"",
                @"poi": place.POIName ?: @"", @"aoi": place.AOIName ?: @"", @"address": place.formattedAddress ?: @""};
            [weakSelf stop];
            completion(location, parts, precise);
        });
    }];
#else
    return NO;
#endif
}

- (void)stop {
#if XIKE_HAS_AMAP
    [self.manager stopUpdatingLocation];
    self.manager.delegate = nil;
    self.manager = nil;
#endif
}

#if XIKE_HAS_AMAP
- (void)amapLocationManager:(AMapLocationManager *)manager doRequireLocationAuth:(CLLocationManager *)locationManager {
    [locationManager requestWhenInUseAuthorization];
}
#endif

@end
