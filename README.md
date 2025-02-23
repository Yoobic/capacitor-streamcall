# stream-call

WIP: We are actively working on this plugin. not yet ready for production. not Released on npm yet.
Uses the https://getstream.io/ SDK to implement calling in Capacitor

## Install

```bash
npm install stream-call
npx cap sync
```

## Setting up Android StreamVideo apikey
1. Add your apikey to the Android project:
```
your_app/android/app/src/main/res/values/strings.xml
```

2. Add your apikey to the Android project:
```xml
<string name="CAPACITOR_STREAM_VIDEO_APIKEY">your_api_key</string>
```

## Setting up iOS StreamVideo apikey
1. Add your apikey to the iOS project:
```
your_app/ios/App/App/Info.plist
```

Add the following to the Info.plist file:
```xml
<dict>
  <key>CAPACITOR_STREAM_VIDEO_APIKEY</key>
  <string>n8wv8vjmucdw</string>
  <!-- other keys -->
</dict>
```

## Native Localization

### iOS

1. Add `Localizable.strings` and `Localizable.stringsdict` files to your Xcode project if you don't have them:
```
/App/App/en.lproj/Localizable.strings
/App/App/en.lproj/Localizable.stringsdict
```

2. Add new languages to your project in Xcode:
   - Open project settings
   - Select your project
   - Click "Info" tab
   - Under "Localizations" click "+"
   - Select the languages you want to add

3. Add the translations in your `Localizable.strings`:
```
// en.lproj/Localizable.strings
"stream.video.call.incoming" = "Incoming call from %@";
"stream.video.call.accept" = "Accept";
"stream.video.call.reject" = "Reject";
"stream.video.call.hangup" = "Hang up";
"stream.video.call.joining" = "Joining...";
"stream.video.call.reconnecting" = "Reconnecting...";
```

4. Configure the localization provider in your `AppDelegate.swift`:
```swift
import StreamVideo

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Set localization provider to use your app's bundle
        Appearance.localizationProvider = { key, table in
            Bundle.main.localizedString(forKey: key, value: nil, table: table)
        }
        return true
    }
}
```

You can find all available localization keys in the [StreamVideo SDK repository](https://github.com/GetStream/stream-video-swift/blob/main/Sources/StreamVideoSwiftUI/Resources/en.lproj/Localizable.strings).

### Android
1. Create string resources in `/app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="stream_video_call_incoming">Incoming call from %1$s</string>
    <string name="stream_video_call_accept">Accept</string>
    <string name="stream_video_call_reject">Reject</string>
    <string name="stream_video_call_hangup">Hang up</string>
    <string name="stream_video_call_joining">Joining...</string>
    <string name="stream_video_call_reconnecting">Reconnecting...</string>
</resources>
```

2. Add translations for other languages in their respective folders (e.g., `/app/src/main/res/values-fr/strings.xml`):
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="stream_video_call_incoming">Appel entrant de %1$s</string>
    <string name="stream_video_call_accept">Accepter</string>
    <string name="stream_video_call_reject">Refuser</string>
    <string name="stream_video_call_hangup">Raccrocher</string>
    <string name="stream_video_call_joining">Connexion...</string>
    <string name="stream_video_call_reconnecting">Reconnexion...</string>
</resources>
```

The SDK will automatically use the system language and these translations.

## API

<docgen-index>

* [`login(...)`](#login)
* [`logout()`](#logout)
* [`call(...)`](#call)
* [`endCall()`](#endcall)
* [`setMicrophoneEnabled(...)`](#setmicrophoneenabled)
* [`setCameraEnabled(...)`](#setcameraenabled)
* [`addListener('callEvent', ...)`](#addlistenercallevent-)
* [`removeAllListeners()`](#removealllisteners)
* [`acceptCall()`](#acceptcall)
* [`rejectCall()`](#rejectcall)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### login(...)

```typescript
login(options: LoginOptions) => Promise<SuccessResponse>
```

| Param         | Type                                                  |
| ------------- | ----------------------------------------------------- |
| **`options`** | <code><a href="#loginoptions">LoginOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#successresponse">SuccessResponse</a>&gt;</code>

--------------------


### logout()

```typescript
logout() => Promise<SuccessResponse>
```

**Returns:** <code>Promise&lt;<a href="#successresponse">SuccessResponse</a>&gt;</code>

--------------------


### call(...)

```typescript
call(options: CallOptions) => Promise<SuccessResponse>
```

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#calloptions">CallOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#successresponse">SuccessResponse</a>&gt;</code>

--------------------


### endCall()

```typescript
endCall() => Promise<SuccessResponse>
```

**Returns:** <code>Promise&lt;<a href="#successresponse">SuccessResponse</a>&gt;</code>

--------------------


### setMicrophoneEnabled(...)

```typescript
setMicrophoneEnabled(options: { enabled: boolean; }) => Promise<SuccessResponse>
```

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ enabled: boolean; }</code> |

**Returns:** <code>Promise&lt;<a href="#successresponse">SuccessResponse</a>&gt;</code>

--------------------


### setCameraEnabled(...)

```typescript
setCameraEnabled(options: { enabled: boolean; }) => Promise<SuccessResponse>
```

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ enabled: boolean; }</code> |

**Returns:** <code>Promise&lt;<a href="#successresponse">SuccessResponse</a>&gt;</code>

--------------------


### addListener('callEvent', ...)

```typescript
addListener(eventName: 'callEvent', listenerFunc: (event: CallEvent) => void) => Promise<{ remove: () => Promise<void>; }>
```

| Param              | Type                                                                |
| ------------------ | ------------------------------------------------------------------- |
| **`eventName`**    | <code>'callEvent'</code>                                            |
| **`listenerFunc`** | <code>(event: <a href="#callevent">CallEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; Promise&lt;void&gt;; }&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------


### acceptCall()

```typescript
acceptCall() => Promise<SuccessResponse>
```

**Returns:** <code>Promise&lt;<a href="#successresponse">SuccessResponse</a>&gt;</code>

--------------------


### rejectCall()

```typescript
rejectCall() => Promise<SuccessResponse>
```

**Returns:** <code>Promise&lt;<a href="#successresponse">SuccessResponse</a>&gt;</code>

--------------------


### Interfaces


#### SuccessResponse

| Prop          | Type                 |
| ------------- | -------------------- |
| **`success`** | <code>boolean</code> |


#### LoginOptions

| Prop               | Type                                                                                        |
| ------------------ | ------------------------------------------------------------------------------------------- |
| **`token`**        | <code>string</code>                                                                         |
| **`userId`**       | <code>string</code>                                                                         |
| **`name`**         | <code>string</code>                                                                         |
| **`imageURL`**     | <code>string</code>                                                                         |
| **`apiKey`**       | <code>string</code>                                                                         |
| **`magicDivId`**   | <code>string</code>                                                                         |
| **`refreshToken`** | <code>{ url: string; headers?: <a href="#record">Record</a>&lt;string, string&gt;; }</code> |


#### CallOptions

| Prop         | Type                 |
| ------------ | -------------------- |
| **`userId`** | <code>string</code>  |
| **`type`**   | <code>string</code>  |
| **`ring`**   | <code>boolean</code> |


#### CallEvent

| Prop         | Type                |
| ------------ | ------------------- |
| **`callId`** | <code>string</code> |
| **`state`**  | <code>string</code> |


### Type Aliases


#### Record

Construct a type with a set of properties K of type T

<code>{ [P in K]: T; }</code>

</docgen-api>
