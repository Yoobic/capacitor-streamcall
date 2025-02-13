# stream-call

WIP: We are actively working on this plugin. not yet ready for production. not Released on npm yet.
Uses the https://getstream.io/ SDK to implement calling in Capacitor

## Install

```bash
npm install stream-call
npx cap sync
```

## API

<docgen-index>

* [`login(...)`](#login)
* [`logout()`](#logout)
* [`call(...)`](#call)
* [`endCall()`](#endcall)
* [`setMicrophoneEnabled(...)`](#setmicrophoneenabled)
* [`setCameraEnabled(...)`](#setcameraenabled)
* [`addListener('callStarted', ...)`](#addlistenercallstarted-)
* [`addListener('callEnded', ...)`](#addlistenercallended-)
* [`addListener('callRinging', ...)`](#addlistenercallringing-)
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


### addListener('callStarted', ...)

```typescript
addListener(eventName: 'callStarted', listenerFunc: (event: CallStartedEvent) => void) => Promise<{ remove: () => Promise<void>; }>
```

| Param              | Type                                                                              |
| ------------------ | --------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'callStarted'</code>                                                        |
| **`listenerFunc`** | <code>(event: <a href="#callstartedevent">CallStartedEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; Promise&lt;void&gt;; }&gt;</code>

--------------------


### addListener('callEnded', ...)

```typescript
addListener(eventName: 'callEnded', listenerFunc: (event: CallEndedEvent) => void) => Promise<{ remove: () => Promise<void>; }>
```

| Param              | Type                                                                          |
| ------------------ | ----------------------------------------------------------------------------- |
| **`eventName`**    | <code>'callEnded'</code>                                                      |
| **`listenerFunc`** | <code>(event: <a href="#callendedevent">CallEndedEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; Promise&lt;void&gt;; }&gt;</code>

--------------------


### addListener('callRinging', ...)

```typescript
addListener(eventName: 'callRinging', listenerFunc: (event: CallRingingEvent) => void) => Promise<{ remove: () => Promise<void>; }>
```

| Param              | Type                                                                              |
| ------------------ | --------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'callRinging'</code>                                                        |
| **`listenerFunc`** | <code>(event: <a href="#callringingevent">CallRingingEvent</a>) =&gt; void</code> |

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
| **`refreshToken`** | <code>{ url: string; headers?: <a href="#record">Record</a>&lt;string, string&gt;; }</code> |


#### CallOptions

| Prop         | Type                 |
| ------------ | -------------------- |
| **`userId`** | <code>string</code>  |
| **`type`**   | <code>string</code>  |
| **`ring`**   | <code>boolean</code> |


#### CallStartedEvent

| Prop         | Type                |
| ------------ | ------------------- |
| **`callId`** | <code>string</code> |


#### CallEndedEvent

| Prop         | Type                |
| ------------ | ------------------- |
| **`callId`** | <code>string</code> |


#### CallRingingEvent

| Prop         | Type                |
| ------------ | ------------------- |
| **`callId`** | <code>string</code> |


### Type Aliases


#### Record

Construct a type with a set of properties K of type T

<code>{
 [P in K]: T;
 }</code>

</docgen-api>
