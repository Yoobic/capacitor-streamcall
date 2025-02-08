# stream-call

Uses the https://getstream.io/ SDK to implement calling in Capacitor

## Install

```bash
npm install stream-call
npx cap sync
```

## API

<docgen-index>

* [`echo(...)`](#echo)
* [`initialize()`](#initialize)
* [`login(...)`](#login)
* [`logout()`](#logout)
* [`call(...)`](#call)
* [`endCall()`](#endcall)
* [`setMicrophoneEnabled(...)`](#setmicrophoneenabled)
* [`setCameraEnabled(...)`](#setcameraenabled)
* [`addListener('callStarted', ...)`](#addlistenercallstarted-)
* [`addListener('callEnded', ...)`](#addlistenercallended-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### echo(...)

```typescript
echo(options: { value: string; }) => Promise<{ value: string; }>
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ value: string; }</code> |

**Returns:** <code>Promise&lt;{ value: string; }&gt;</code>

--------------------


### initialize()

```typescript
initialize() => Promise<void>
```

--------------------


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
addListener(eventName: 'callEnded', listenerFunc: (event: {}) => void) => Promise<{ remove: () => Promise<void>; }>
```

| Param              | Type                                |
| ------------------ | ----------------------------------- |
| **`eventName`**    | <code>'callEnded'</code>            |
| **`listenerFunc`** | <code>(event: {}) =&gt; void</code> |

**Returns:** <code>Promise&lt;{ remove: () =&gt; Promise&lt;void&gt;; }&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------


### Interfaces


#### SuccessResponse

| Prop          | Type                 |
| ------------- | -------------------- |
| **`success`** | <code>boolean</code> |


#### LoginOptions

| Prop           | Type                |
| -------------- | ------------------- |
| **`token`**    | <code>string</code> |
| **`userId`**   | <code>string</code> |
| **`name`**     | <code>string</code> |
| **`imageURL`** | <code>string</code> |


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

</docgen-api>
