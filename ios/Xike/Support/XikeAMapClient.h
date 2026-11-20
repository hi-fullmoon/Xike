#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>

NS_ASSUME_NONNULL_BEGIN

@interface XikeAMapClient : NSObject
@property (class, nonatomic, readonly) BOOL available;
- (BOOL)requestWithKey:(NSString *)key
              english:(BOOL)english
           completion:(void (^)(CLLocation * _Nullable location,
                                NSDictionary<NSString *, NSString *> *places,
                                BOOL precise))completion NS_SWIFT_NAME(request(key:english:completion:));
- (void)stop;
@end

NS_ASSUME_NONNULL_END
